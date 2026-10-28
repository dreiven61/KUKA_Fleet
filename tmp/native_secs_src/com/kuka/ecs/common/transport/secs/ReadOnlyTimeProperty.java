/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.ReadOnlyNumberProperty;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

public interface ReadOnlyTimeProperty
extends ReadOnlyNumberProperty {
    public float getSeconds();

    public long getMilliSeconds();

    public boolean gtZero();

    public boolean geZero();

    public void sleep() throws InterruptedException;

    public void wait(Object var1) throws InterruptedException;

    public <T> T future(Future<T> var1) throws InterruptedException, TimeoutException, ExecutionException;

    public <T> T poll(BlockingQueue<T> var1) throws InterruptedException;
}

