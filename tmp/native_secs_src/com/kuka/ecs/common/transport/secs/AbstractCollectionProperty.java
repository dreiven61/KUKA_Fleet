/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.CollectionProperty;
import com.kuka.ecs.common.transport.secs.InterruptableRunnable;
import com.kuka.ecs.common.transport.secs.PropertyChangeListener;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractCollectionProperty<T>
implements CollectionProperty<T>,
Serializable {
    private static final long serialVersionUID = -4935312347859335201L;
    private final Object sync = new Object();
    private Collection<T> vv;
    private final Collection<PropertyChangeListener<? super Collection<T>>> listeners = new CopyOnWriteArrayList<PropertyChangeListener<? super Collection<T>>>();

    public AbstractCollectionProperty(Collection<T> initial) {
        this.vv = Objects.requireNonNull(initial);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Collection<T> get() {
        Object object = this.sync;
        synchronized (object) {
            return this.vv.stream().map(v -> v).collect(Collectors.toList());
        }
    }

    @Override
    public void set(Collection<? extends T> v) {
        throw new UnsupportedOperationException("Unsupport AbstractCollectionProperty#set");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean addChangeListener(PropertyChangeListener<? super Collection<T>> listener) {
        Object object = this.sync;
        synchronized (object) {
            boolean f = this.listeners.add(listener);
            if (f) {
                listener.changed((Collection<Object>)this.get());
            }
            return f;
        }
    }

    @Override
    public boolean removeChangeListener(PropertyChangeListener<? super Collection<T>> listener) {
        return this.listeners.remove(listener);
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

    private void notifyChanged(boolean f) {
        if (f) {
            this.notifyChanged();
        }
    }

    @Override
    public void waitUntil(Collection<T> v) throws InterruptedException {
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
    public void waitUntil(Collection<T> v, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        this.waitWithTimeout(() -> this.waitUntil(v), timeout, unit);
    }

    @Override
    public void waitUntil(Collection<T> v, ReadOnlyTimeProperty tp) throws InterruptedException, TimeoutException {
        this.waitUntil(v, tp.getMilliSeconds(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void waitUntilNot(Collection<T> v) throws InterruptedException {
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
    public void waitUntilNot(Collection<T> v, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        this.waitWithTimeout(() -> this.waitUntilNot(v), timeout, unit);
    }

    @Override
    public void waitUntilNot(Collection<T> v, ReadOnlyTimeProperty tp) throws InterruptedException, TimeoutException {
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

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public int size() {
        Object object = this.sync;
        synchronized (object) {
            return this.get().size();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean isEmpty() {
        Object object = this.sync;
        synchronized (object) {
            return this.get().isEmpty();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean contains(Object o) {
        Object object = this.sync;
        synchronized (object) {
            return this.get().contains(o);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        Object object = this.sync;
        synchronized (object) {
            return this.get().containsAll(c);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Iterator<T> iterator() {
        Object object = this.sync;
        synchronized (object) {
            return this.get().stream().map(x -> x).iterator();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Object[] toArray() {
        Object object = this.sync;
        synchronized (object) {
            return this.get().toArray();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public <U> U[] toArray(U[] a) {
        Object object = this.sync;
        synchronized (object) {
            return this.get().toArray(a);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean add(T v) {
        Object object = this.sync;
        synchronized (object) {
            boolean f = this.vv.add(v);
            this.notifyChanged(f);
            return f;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean remove(Object o) {
        Object object = this.sync;
        synchronized (object) {
            boolean f = this.vv.remove(o);
            this.notifyChanged(f);
            return f;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean addAll(Collection<? extends T> c) {
        Object object = this.sync;
        synchronized (object) {
            boolean f = this.vv.addAll(c);
            this.notifyChanged(f);
            return f;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        Object object = this.sync;
        synchronized (object) {
            boolean f = this.vv.removeAll(c);
            this.notifyChanged(f);
            return f;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        Object object = this.sync;
        synchronized (object) {
            boolean f = this.vv.retainAll(c);
            this.notifyChanged(f);
            return f;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void clear() {
        Object object = this.sync;
        synchronized (object) {
            if (!this.isEmpty()) {
                this.vv.clear();
                this.notifyChanged();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        Object object = this.sync;
        synchronized (object) {
            boolean f = this.vv.removeIf(filter);
            this.notifyChanged(f);
            return f;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Spliterator<T> spliterator() {
        Object object = this.sync;
        synchronized (object) {
            return this.get().stream().map(x -> x).spliterator();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Stream<T> stream() {
        Object object = this.sync;
        synchronized (object) {
            return this.get().stream().map(x -> x);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Stream<T> parallelStream() {
        Object object = this.sync;
        synchronized (object) {
            return this.get().parallelStream().map(x -> x);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void forEach(Consumer<? super T> action) {
        Object object = this.sync;
        synchronized (object) {
            this.get().forEach(action);
        }
    }
}

