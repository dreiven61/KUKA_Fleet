/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.InterruptableRunnable;
import com.kuka.ecs.common.transport.secs.OpenAndCloseable;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractBaseCommunicator
implements OpenAndCloseable {
    private final ExecutorService execServ = Executors.newCachedThreadPool(r -> {
        Thread th = new Thread(r);
        th.setDaemon(true);
        return th;
    });
    private final Object syncOpenClose = new Object();
    private boolean opened = false;
    private boolean closed = false;

    public ExecutorService executorService() {
        return this.execServ;
    }

    public static Runnable createLoopTask(final InterruptableRunnable task) {
        return new Runnable(){

            @Override
            public void run() {
                try {
                    while (true) {
                        task.run();
                    }
                }
                catch (InterruptedException interruptedException) {
                    return;
                }
            }
        };
    }

    public void executeLoopTask(InterruptableRunnable r) {
        this.execServ.execute(AbstractBaseCommunicator.createLoopTask(r));
    }

    public <T> T executeInvokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return this.execServ.invokeAny(tasks);
    }

    public <T> T executeInvokeAny(Collection<? extends Callable<T>> tasks, ReadOnlyTimeProperty timeout) throws InterruptedException, ExecutionException, TimeoutException {
        return this.execServ.invokeAny(tasks, timeout.getMilliSeconds(), TimeUnit.MILLISECONDS);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void open() throws IOException {
        Object object = this.syncOpenClose;
        synchronized (object) {
            if (this.closed) {
                throw new AlreadyClosedException();
            }
            if (this.opened) {
                throw new AlreadyOpenedException();
            }
            this.opened = true;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void close() throws IOException {
        Object object = this.syncOpenClose;
        synchronized (object) {
            if (this.closed) {
                return;
            }
            this.closed = true;
        }
        try {
            this.execServ.shutdown();
            if (!this.execServ.awaitTermination(1L, TimeUnit.MILLISECONDS)) {
                this.execServ.shutdownNow();
                if (!this.execServ.awaitTermination(10L, TimeUnit.SECONDS)) {
                    throw new ExecutorServiceShutdownFailedException();
                }
            }
        }
        catch (InterruptedException interruptedException) {
            // empty catch block
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean isOpen() {
        Object object = this.syncOpenClose;
        synchronized (object) {
            return this.opened && !this.closed;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean isClosed() {
        Object object = this.syncOpenClose;
        synchronized (object) {
            return this.closed;
        }
    }

    private class ExecutorServiceShutdownFailedException
    extends IOException {
        private static final long serialVersionUID = -4286787943602603541L;
    }

    private class AlreadyOpenedException
    extends IOException {
        private static final long serialVersionUID = 2168211058459635677L;
    }

    private class AlreadyClosedException
    extends IOException {
        private static final long serialVersionUID = 7336806506693638863L;
    }
}

