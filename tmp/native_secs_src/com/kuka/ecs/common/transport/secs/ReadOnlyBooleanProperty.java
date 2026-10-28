/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.ReadOnlyProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface ReadOnlyBooleanProperty
extends ReadOnlyProperty<Boolean> {
    public boolean booleanValue();

    public void waitUntilTrue() throws InterruptedException;

    public void waitUntilTrue(long var1, TimeUnit var3) throws InterruptedException, TimeoutException;

    public void waitUntilTrue(ReadOnlyTimeProperty var1) throws InterruptedException, TimeoutException;

    public void waitUntilFalse() throws InterruptedException;

    public void waitUntilFalse(long var1, TimeUnit var3) throws InterruptedException, TimeoutException;

    public void waitUntilFalse(ReadOnlyTimeProperty var1) throws InterruptedException, TimeoutException;
}

