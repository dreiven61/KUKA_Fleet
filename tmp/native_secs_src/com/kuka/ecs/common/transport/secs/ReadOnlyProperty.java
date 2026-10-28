/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.PropertyChangeListener;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface ReadOnlyProperty<T> {
    public T get();

    public boolean addChangeListener(PropertyChangeListener<? super T> var1);

    public boolean removeChangeListener(PropertyChangeListener<? super T> var1);

    public void waitUntil(T var1) throws InterruptedException;

    public void waitUntil(T var1, long var2, TimeUnit var4) throws InterruptedException, TimeoutException;

    public void waitUntil(T var1, ReadOnlyTimeProperty var2) throws InterruptedException, TimeoutException;

    public void waitUntilNot(T var1) throws InterruptedException;

    public void waitUntilNot(T var1, long var2, TimeUnit var4) throws InterruptedException, TimeoutException;

    public void waitUntilNot(T var1, ReadOnlyTimeProperty var2) throws InterruptedException, TimeoutException;
}

