package com.github.pfmiles.createmvnkotlinjar.impl.async.futuremultiplex;

import com.github.pfmiles.createmvnkotlinjar.impl.Runner;
import com.google.common.base.Preconditions;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author pf-miles
 * <p>
 * 2022-11-15 19:16
 */
public class FutureItem<T> extends BlockingItem<T> {
    private final Future<T> future;

    public FutureItem(Future<T> future, Date expire, Consumer<T> whenDone,
                      Consumer<Throwable> whenError, Runnable whenTimeout) {
        super(expire, whenDone, whenError, whenTimeout);
        Preconditions.checkArgument(future != null);
        this.future = future;
    }

    @Override
    public boolean isDone() {
        return this.future.isDone();
    }

    @Override
    public T getResult() throws ExecutionException, InterruptedException {
        return this.future.get();
    }

    @Override
    public void cancel(boolean mayInterruptIfRunning) {
        this.future.cancel(mayInterruptIfRunning);
    }

    @Override
    public void dispose() {
        Runner.tryExec(() -> future.cancel(true));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FutureItem<?> that = (FutureItem<?>) o;
        return future.equals(that.future);
    }

    @Override
    public int hashCode() {
        return Objects.hash(future);
    }
}
