/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.InterruptableRunnable;
import com.kuka.ecs.common.transport.secs.Property;
import com.kuka.ecs.common.transport.secs.PropertyChangeListener;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractProperty<T>
implements Property<T>,
Serializable {
    private static final long serialVersionUID = -7897962203686565003L;
    private final Object sync = new Object();
    private T present;
    private final Collection<PropertyChangeListener<? super T>> listeners = new CopyOnWriteArrayList<PropertyChangeListener<? super T>>();

    public AbstractProperty(T initial) {
        this.present = initial;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public T get() {
        Object object = this.sync;
        synchronized (object) {
            return this.present;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void set(T v) {
        Object object = this.sync;
        synchronized (object) {
            if (!Objects.equals(v, this.present)) {
                this.present = v;
                this.notifyChanged();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean addChangeListener(PropertyChangeListener<? super T> l) {
        Object object = this.sync;
        synchronized (object) {
            boolean f = this.listeners.add(l);
            if (f) {
                l.changed(this.get());
            }
            return f;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean removeChangeListener(PropertyChangeListener<? super T> l) {
        Object object = this.sync;
        synchronized (object) {
            return this.listeners.remove(l);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected void notifyChanged() {
        Object object = this.sync;
        synchronized (object) {
            this.listeners.forEach(l -> l.changed(this.get()));
            this.sync.notifyAll();
        }
    }

    @Override
    public void waitUntil(T v) throws InterruptedException {
        Object object = this.sync;
        synchronized (object) {
            while (true) {
                if (Objects.equals(this.get(), v)) {
                    return;
                }
                this.sync.wait();
            }
        }
    }

    @Override
    public void waitUntil(T v, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        this.waitWithTimeout(() -> this.waitUntil(v), timeout, unit);
    }

    @Override
    public void waitUntil(T v, ReadOnlyTimeProperty tp) throws InterruptedException, TimeoutException {
        this.waitUntil(v, tp.getMilliSeconds(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void waitUntilNot(T v) throws InterruptedException {
        Object object = this.sync;
        synchronized (object) {
            while (true) {
                if (!Objects.equals(this.get(), v)) {
                    return;
                }
                this.sync.wait();
            }
        }
    }

    @Override
    public void waitUntilNot(T v, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        this.waitWithTimeout(() -> this.waitUntilNot(v), timeout, unit);
    }

    @Override
    public void waitUntilNot(T v, ReadOnlyTimeProperty tp) throws InterruptedException, TimeoutException {
        this.waitUntilNot(v, tp.getMilliSeconds(), TimeUnit.MILLISECONDS);
    }

    private void waitWithTimeout(InterruptableRunnable r, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        FutureTask<Object> f = new FutureTask<Object>(() -> {
            try {
                r.run();
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
        }, null);
        try {
            f.get(timeout, unit);
        }
        catch (InterruptedException e) {
            f.cancel(true);
            throw e;
        }
        catch (TimeoutException e) {
            f.cancel(true);
            throw e;
        }
        catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
            throw new RuntimeException(t);
        }
    }

    public String toString() {
        return Objects.toString(this.get());
    }
}

