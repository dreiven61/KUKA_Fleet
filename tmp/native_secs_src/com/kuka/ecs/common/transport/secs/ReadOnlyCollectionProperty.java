/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.ReadOnlyProperty;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface ReadOnlyCollectionProperty<T>
extends ReadOnlyProperty<Collection<T>> {
    public int size();

    public boolean isEmpty();

    public boolean contains(Object var1);

    public boolean containsAll(Collection<?> var1);

    public Iterator<T> iterator();

    public Object[] toArray();

    public <U> U[] toArray(U[] var1);

    public Spliterator<T> spliterator();

    public Stream<T> stream();

    public Stream<T> parallelStream();

    public void forEach(Consumer<? super T> var1);
}

