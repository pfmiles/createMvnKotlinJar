package com.github.pfmiles.createmvnkotlinjar.impl.async;

import com.github.pfmiles.createmvnkotlinjar.impl.Runner;
import com.github.pfmiles.createmvnkotlinjar.impl.async.futuremultiplex.BlockingItem;
import com.github.pfmiles.createmvnkotlinjar.impl.async.futuremultiplex.FutureItem;
import com.github.pfmiles.createmvnkotlinjar.impl.async.futuremultiplex.ProcessItem;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * java.util.concurrent.Future的多路复用器，采用单线程轮询多个future，实现non-blocking的集体等待，能支持大量的future异步处理而节省线程资源
 * 该工具采用内部单线程event loop轮询的方式对所有被管理的futures进行轮询，监控其完成状态，从而取代"一个线程wait一个future"的blocking模式，节省线程资源
 * 内部event loop的间隔时间可根据业务需要进行调整, 对于实时性要求高的业务(即future的执行时间普遍较短，如几十毫秒级)，eventLoopInterval可设置较小的值(最小到1);
 * 对于实时性要求不高的业务(如future的执行时间普遍较长: 秒级甚至更多)，则可设置稍大的eventLoopInterval值以减少空轮询频率, 不过建议eventLoopInterval最大不要超过1000
 *
 * @author pf-miles
 * <p>
 * 2022-09-27 14:17
 */
public class FuturesMultiplexer {
    private static final Logger logger = LoggerFactory
            .getLogger(FuturesMultiplexer.class);

    private final long eventLoopInterval;

    // 当前正在运行的future, Pair<taskFuture, expireDate>
    private final Set<BlockingItem<?>> futures = Collections
            .newSetFromMap(new ConcurrentHashMap<>());

    private final ScheduledExecutorService pollingThread = Executors
            .newSingleThreadScheduledExecutor(r -> new Thread(r, "FuturesMultiplexer-polling-thread"));

    private static final AtomicLong seq = new AtomicLong();
    private final ExecutorService callbackExePool = Executors.newCachedThreadPool(
            r -> new Thread(r, "FuturesMultiplexer-callback-exe-thread-" + seq.getAndIncrement()));

    /**
     * 创建一个futures multiplexer，并指定其轮询时间间隔
     *
     * @param eventLoopInterval 内部event loop的轮询时间间隔ms, 根据业务需要进行设置; 对于实时性要求高的业务(即future的执行时间普遍较短，如几十毫秒级)，eventLoopInterval可设置较小的值(最小到1);
     *                          对于实时性要求不高的业务(如future的执行时间普遍较长: 秒级甚至更多)，则可设置稍大的eventLoopInterval值以减少空轮询频率, 不过建议eventLoopInterval最大不要超过1000
     */
    public FuturesMultiplexer(long eventLoopInterval) {
        Preconditions.checkArgument(eventLoopInterval > 0);
        this.eventLoopInterval = eventLoopInterval;
        pollingLoop();
    }

    public void pollingLoop() {
        try {
            if (!futures.isEmpty()) {
                Iterator<BlockingItem<?>> iter = futures.iterator();
                // 遍历处理：done、error和timeout状态的item分别处置并从futures中删除
                while (iter.hasNext()) {
                    BlockingItem item = iter.next();
                    if (item.isDone()) {
                        try {
                            // 完成: 包含抛错或正常完成两种情况
                            Object rst;
                            try {
                                rst = item.getResult();
                            } catch (CancellationException | ExecutionException ee) {
                                // 业务上的取消或抛错
                                if (item.getWhenError() != null) {
                                    callbackExePool.submit(() -> {
                                        item.getWhenError().accept(ee);
                                    });
                                }
                                continue;
                            }
                            // 正常返回了值
                            if (item.getWhenDone() != null) {
                                callbackExePool.submit(() -> {
                                    item.getWhenDone().accept(rst);
                                });
                            }
                        } finally {
                            iter.remove();
                            Runner.tryExec(item::dispose);
                        }
                    } else if (System.currentTimeMillis() > item.getExpire().getTime()) {
                        // 超时
                        item.cancel(true);
                        if (item.getWhenTimeout() != null) {
                            callbackExePool.submit(() -> {
                                item.getWhenTimeout().run();
                            });
                        }
                        iter.remove();
                        Runner.tryExec(item::dispose);
                    }
                }
            }
        } catch (Throwable e) {
            if (e instanceof InterruptedException) {
                logger.info("FuturesMultiplexer-polling-thread interrupted, stopped polling.", e);
                return; // exit polling when interrupted
            }
            // polling loop never die except interrupt
            logger.error(
                    "FuturesMultiplexer polling thread throws exception, ignored and continue polling.",
                    e);
        }
        pollingThread.schedule(this::pollingLoop, this.eventLoopInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * 将future注册进multiplexer, 并在其完成、抛错或超时时异步地执行相应后续逻辑；
     * 注意: 最好不要在后续逻辑中使用ThreadLocal变量，因为到时候执行后续逻辑的线程并非这时的提交线程，除非你能自己处理好这些ThreadLocal值的传递
     * 不建议在后续callback中做太重的工作
     *
     * @param <T>         future的返回值类型
     * @param future      future
     * @param expire      future超时时间，超过此时间后future将被视为超时，将被取消
     * @param whenDone    当future正常执行结束后的处理逻辑，能拿到future正常返回的返回值
     * @param whenError   当future正常执行结束但抛错后的处理逻辑，能拿到所抛出的错误
     * @param whenTimeout 当future执行超时后的处理逻辑
     */
    public <T> void submitFuture(Future<T> future, Date expire, Consumer<T> whenDone,
                                 Consumer<Throwable> whenError, Runnable whenTimeout) {
        Preconditions.checkArgument(future != null);
        Preconditions.checkArgument(expire != null && expire.after(new Date()),
                "Illegal expire date, must be a time after now.");

        this.submit(new FutureItem<>(future, expire, whenDone, whenError, whenTimeout));
    }

    /**
     * 将future注册进multiplexer, 将其转换为completableFuture后返回，以便在后续代码中实现CPS风格的异步处理
     *
     * @param future 原始future
     * @param expire future超时时间，超过此时间后future将被视为超时，将被取消
     * @param <T>    future的返回值类型
     * @return 原始future转换而成的completableFuture
     */
    public <T> CompletableFuture<T> submitFuture(Future<T> future, Date expire) {
        CompletableFuture<T> ret = new CompletableFuture<>();
        this.submitFuture(future, expire, ret::complete, ret::completeExceptionally,
                () -> ret.completeExceptionally(new TimeoutException(
                        String.format("Future execution exceeds the expiration moment: %s.", expire))));
        return ret;
    }

    /**
     * 往multiplexer中注册进一个blocking item
     *
     * @param item blocking item
     * @param <T>  blocking item所代表的的任务完成后的返回值类型
     */
    public <T> void submit(BlockingItem<T> item) {
        Preconditions.checkArgument(item != null, "Submitted blocking item cannot be null.");
        futures.add(item);
    }

    /**
     * 将Process注册进multiplexer, 并在其完成、抛错或超时时异步地执行相应后续逻辑；
     * 注意: 最好不要在后续逻辑中使用ThreadLocal变量，因为到时候执行后续逻辑的线程并非这时的提交线程，除非你能自己处理好这些ThreadLocal值的传递
     * 不建议在后续callback中做太重的工作
     *
     * @param process     process
     * @param expire      process超时时间，超过此时间后process将被视为超时，将被取消
     * @param whenDone    当process正常执行结束后的处理逻辑，能拿到process的exit code
     * @param whenError   当process正常执行结束但抛错后的处理逻辑，能拿到所抛出的错误
     * @param whenTimeout 当process执行超时后的处理逻辑
     */
    public void submitProcess(Process process, Date expire, Consumer<Integer> whenDone,
                              Consumer<Throwable> whenError, Runnable whenTimeout) {
        Preconditions.checkArgument(process != null);
        Preconditions.checkArgument(expire != null && expire.after(new Date()),
                "Illegal expire date, must be a time after now.");

        this.submit(new ProcessItem(process, expire, whenDone, whenError, whenTimeout));
    }

    /**
     * 将process注册进multiplexer, 将其转换为completableFuture后返回，以便在后续代码中实现CPS风格的异步处理
     *
     * @param process 原始process
     * @param expire  process超时时间，超过此时间后process将被视为超时，将被取消
     * @return 原始process转换而成的completableFuture
     */
    public CompletableFuture<Integer> submitProcess(Process process, Date expire) {
        CompletableFuture<Integer> ret = new CompletableFuture<>();
        this.submitProcess(process, expire, ret::complete, ret::completeExceptionally,
                () -> ret.completeExceptionally(new TimeoutException(
                        String.format("Process execution exceeds the expiration moment: %s.", expire))));
        return ret;
    }

    public void destroy() {
        Runner.shutdownThreadPool(this.pollingThread, 1);
        Runner.shutdownThreadPool(this.callbackExePool, 3);
        this.futures.forEach(fi -> fi.cancel(true));
        this.futures.forEach(BlockingItem::dispose);
        this.futures.clear();
    }
}
