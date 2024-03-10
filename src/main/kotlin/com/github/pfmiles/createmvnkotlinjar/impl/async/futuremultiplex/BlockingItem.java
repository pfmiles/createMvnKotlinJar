package com.github.pfmiles.createmvnkotlinjar.impl.async.futuremultiplex;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * 代表一个阻塞的任务，比如java.concurrent.Future或子进程Process等
 * 
 * @author pf-miles
 * <p>
 * 2022-09-27 16:01
 */
public abstract class BlockingItem<T> {
    private Date                expire;
    private Consumer<T>         whenDone;
    private Consumer<Throwable> whenError;
    private Runnable            whenTimeout;

    public BlockingItem(Date expire, Consumer<T> whenDone, Consumer<Throwable> whenError,
                        Runnable whenTimeout) {
        this.expire = expire;
        this.whenDone = whenDone;
        this.whenError = whenError;
        this.whenTimeout = whenTimeout;
    }

    public Date getExpire() {
        return expire;
    }

    public void setExpire(Date expire) {
        this.expire = expire;
    }

    public Consumer<T> getWhenDone() {
        return whenDone;
    }

    public void setWhenDone(Consumer<T> whenDone) {
        this.whenDone = whenDone;
    }

    public Consumer<Throwable> getWhenError() {
        return whenError;
    }

    public void setWhenError(Consumer<Throwable> whenError) {
        this.whenError = whenError;
    }

    public Runnable getWhenTimeout() {
        return whenTimeout;
    }

    public void setWhenTimeout(Runnable whenTimeout) {
        this.whenTimeout = whenTimeout;
    }

    public abstract boolean isDone();

    public abstract T getResult() throws ExecutionException, InterruptedException;

    public abstract void cancel(boolean mayInterrupt);

    public abstract void dispose();

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);
}
