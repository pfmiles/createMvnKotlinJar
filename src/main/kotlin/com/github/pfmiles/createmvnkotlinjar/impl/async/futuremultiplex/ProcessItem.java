package com.github.pfmiles.createmvnkotlinjar.impl.async.futuremultiplex;

import com.google.common.base.Preconditions;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * @author pf-miles
 * <p>
 * 2022-11-15 20:34
 */
public class ProcessItem extends BlockingItem<Integer> {
    private final Process process;

    public ProcessItem(Process process, Date expire, Consumer<Integer> whenDone,
                       Consumer<Throwable> whenError, Runnable whenTimeout) {
        super(expire, whenDone, whenError, whenTimeout);
        Preconditions.checkArgument(process != null);
        this.process = process;
    }

    @Override
    public boolean isDone() {
        return !process.isAlive();
    }

    @Override
    public Integer getResult() throws ExecutionException, InterruptedException {
        return process.waitFor();
    }

    @Override
    public void cancel(boolean mayInterrupt) {
        if (mayInterrupt) {
            process.destroyForcibly();
        } else {
            process.destroy();
        }
    }

    @Override
    public void dispose() {
        // 清理process
        try {
            process.getErrorStream().close();
            process.getOutputStream().close();
            process.getInputStream().close();
            process.destroyForcibly();
        } catch (Exception e) {
            // ignored...
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ProcessItem that = (ProcessItem) o;
        return process.equals(that.process);
    }

    @Override
    public int hashCode() {
        return Objects.hash(process);
    }
}
