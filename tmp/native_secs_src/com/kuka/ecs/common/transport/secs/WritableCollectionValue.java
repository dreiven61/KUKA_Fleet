/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.WritableValue;
import java.util.Collection;
import java.util.function.Predicate;

public interface WritableCollectionValue<T>
extends WritableValue<Collection<? extends T>> {
    public boolean add(T var1);

    public boolean remove(Object var1);

    public boolean addAll(Collection<? extends T> var1);

    public boolean removeAll(Collection<?> var1);

    public boolean retainAll(Collection<?> var1);

    public void clear();

    public boolean removeIf(Predicate<? super T> var1);
}

