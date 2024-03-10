package com.github.pfmiles.createmvnkotlinjar.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class Runner {
    /**
     * The constant logger.
     */
    private static final Logger tryLogger = LoggerFactory.getLogger("try-run");

    private static final AtomicLong interSeq = new AtomicLong();
    private static final ExecutorService interrupters = Executors.newCachedThreadPool(
            r -> new Thread(r, "timed-waiting-interrupter-" + interSeq.getAndIncrement()));

    /**
     * Try execute.
     *
     * @param runnable the runnable
     * @return the boolean
     */
    public static boolean tryExec(Runnable runnable) {
        try {
            runnable.run();
            return true;
        } catch (Throwable t) {
            tryLogger.warn("failed to execute runnable.", t);
        }
        return false;
    }

    /**
     * 用于包装某耗时逻辑，并以指定的时间blocking等待其执行完毕，若未执行完毕则抛出TimeoutException; 若在指定的时间内执行完毕则正常返回
     *
     * @param logic  将要执行的耗时逻辑, 注意：该逻辑中的耗时操作必须正确响应interrupt操作
     * @param toWait 期望等待的时间，以ms计
     * @throws TimeoutException 超时抛出
     */
    public static <R> R timedWaiting(Callable<R> logic, final long toWait) throws TimeoutException {
        // 创建一个interrupter,在规定的时间后中断本线程；本线程若先于interrupter执行结束则先中断interrupter线程
        Thread main = Thread.currentThread();
        String mainThreadName = main.getName();
        if (tryLogger.isDebugEnabled()) {
            tryLogger.debug("timeWaiting submit, time out in {}", toWait);
        }
        Future<?> interFuture = interrupters.submit(() -> {
            try {
                Thread.sleep(toWait);
                tryLogger.error("timeout {}! interrupt main thread {}", toWait, mainThreadName);
                main.interrupt();
            } catch (InterruptedException e) {
                // interrupter interrupted, normal end, do nothing
            }
        });
        try {
            R ret = logic.call();
            interFuture.cancel(true);
            return ret;
        } catch (Exception e) {
            interFuture.cancel(true);
            // may timeout
            if (ExceptionUtils.isCausedByInterrupt(e)) {
                throw new TimeoutException(
                        String.format("Interrupted, execution may timed out: %sms.", toWait));
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 同步等待条件满足(condition返回true)
     * condition一直不返回或返回false或返回null都将继续等待直到超时
     *
     * @param initDelay     初始等待，ms
     * @param condition     条件逻辑
     * @param waitMillis    最长等待时间，ms
     * @param checkInterval 等待期间检查condition返回值的时间间隔, ms
     */
    public static void untilCondMet(Supplier<Boolean> condition, long initDelay, long waitMillis,
                                    long checkInterval) throws TimeoutException, ExecutionException,
            InterruptedException {
        if (initDelay > 0)
            Thread.sleep(initDelay);
        Callable<Void> waitLogic = () -> {
            int i = 0;// limit the max round, make the possibly ill-interrupted logics behave ok
            long maxRound = (waitMillis / checkInterval) + 1;
            for (Boolean ok = condition.get(); (ok == null || !ok)
                    && i < maxRound; ok = condition.get(), i++)
                Thread.sleep(checkInterval);
            return null;
        };
        timedWaiting(waitLogic, waitMillis);
    }

    public static void untilCondMet(Supplier<Boolean> condition, long waitMillis,
                                    long checkInterval) throws TimeoutException, ExecutionException,
            InterruptedException {
        untilCondMet(condition, 0, waitMillis, checkInterval);
    }

    /**
     * 关闭线程池
     *
     * @param pool                  要关闭的线程池
     * @param secsToWaitTermination 最多等待正在执行的任务多少秒
     */
    public static void shutdownThreadPool(ExecutorService pool, int secsToWaitTermination) {
        if (pool == null || pool.isShutdown())
            return;
        tryExec(pool::shutdown);
        tryExec(() -> {
            try {
                pool.awaitTermination(secsToWaitTermination, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        tryExec(() -> pool.shutdownNow());
    }

}
