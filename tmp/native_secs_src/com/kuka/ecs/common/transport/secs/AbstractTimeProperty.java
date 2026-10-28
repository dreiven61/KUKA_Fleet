/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractNumberProperty;
import com.kuka.ecs.common.transport.secs.TimeProperty;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractTimeProperty
extends AbstractNumberProperty
implements TimeProperty {
    private static final long serialVersionUID = 4647118881525451997L;
    private final Object sync = new Object();
    private long milliSec;

    public AbstractTimeProperty(Number initial) {
        super(initial);
        this.setMilliSeconds(initial);
    }

    public AbstractTimeProperty(int initial) {
        this((Number)initial);
    }

    public AbstractTimeProperty(long initial) {
        this((Number)initial);
    }

    public AbstractTimeProperty(float initial) {
        this(Float.valueOf(initial));
    }

    public AbstractTimeProperty(double initial) {
        this((Number)initial);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void set(Number v) {
        Object object = this.sync;
        synchronized (object) {
            this.setMilliSeconds(v);
            super.set(v);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void setMilliSeconds(Number v) {
        Object object = this.sync;
        synchronized (object) {
            this.milliSec = (long)(Objects.requireNonNull(v).floatValue() * 1000.0f);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public float getSeconds() {
        Object object = this.sync;
        synchronized (object) {
            return this.floatValue();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public long getMilliSeconds() {
        Object object = this.sync;
        synchronized (object) {
            return this.milliSec;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean gtZero() {
        Object object = this.sync;
        synchronized (object) {
            return this.milliSec > 0L;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean geZero() {
        Object object = this.sync;
        synchronized (object) {
            return this.milliSec >= 0L;
        }
    }

    @Override
    public void sleep() throws InterruptedException {
        long t = this.getMilliSeconds();
        if (t > 0L) {
            TimeUnit.MILLISECONDS.sleep(t);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void wait(Object syncObj) throws InterruptedException {
        Object object = syncObj;
        synchronized (object) {
            long t = this.getMilliSeconds();
            if (t >= 0L) {
                syncObj.wait(t);
            } else {
                syncObj.wait();
            }
        }
    }

    @Override
    public <T> T future(Future<T> f) throws InterruptedException, TimeoutException, ExecutionException {
        return f.get(this.getMilliSeconds(), TimeUnit.MILLISECONDS);
    }

    @Override
    public <T> T poll(BlockingQueue<T> queue) throws InterruptedException {
        return queue.poll(this.getMilliSeconds(), TimeUnit.MILLISECONDS);
    }
}

