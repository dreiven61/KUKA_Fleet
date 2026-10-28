/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractProperty;
import com.kuka.ecs.common.transport.secs.BooleanProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractBooleanProperty
extends AbstractProperty<Boolean>
implements BooleanProperty {
    private static final long serialVersionUID = -1707441122073604603L;

    public AbstractBooleanProperty(boolean initial) {
        super(initial);
    }

    @Override
    public void set(Boolean v) {
        super.set(Objects.requireNonNull(v));
    }

    @Override
    public void set(boolean v) {
        super.set(v);
    }

    @Override
    public boolean booleanValue() {
        return (Boolean)this.get();
    }

    @Override
    public void waitUntilTrue() throws InterruptedException {
        this.waitUntil(Boolean.TRUE);
    }

    @Override
    public void waitUntilTrue(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        this.waitUntil(Boolean.TRUE, timeout, unit);
    }

    @Override
    public void waitUntilTrue(ReadOnlyTimeProperty tp) throws InterruptedException, TimeoutException {
        this.waitUntil(Boolean.TRUE, tp);
    }

    @Override
    public void waitUntilFalse() throws InterruptedException {
        this.waitUntil(Boolean.FALSE);
    }

    @Override
    public void waitUntilFalse(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        this.waitUntil(Boolean.FALSE, timeout, unit);
    }

    @Override
    public void waitUntilFalse(ReadOnlyTimeProperty tp) throws InterruptedException, TimeoutException {
        this.waitUntil(Boolean.FALSE, tp);
    }
}

