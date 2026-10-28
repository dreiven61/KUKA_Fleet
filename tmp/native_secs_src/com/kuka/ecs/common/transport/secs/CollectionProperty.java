/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractCollectionProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyCollectionProperty;
import com.kuka.ecs.common.transport.secs.WritableCollectionValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public interface CollectionProperty<T>
extends ReadOnlyCollectionProperty<T>,
WritableCollectionValue<T> {
    public static <T> CollectionProperty<T> newList() {
        return new AbstractCollectionProperty<T>((Collection)new ArrayList()){
            private static final long serialVersionUID = -2632352478183516719L;
        };
    }

    public static <T> CollectionProperty<T> newSet() {
        return new AbstractCollectionProperty<T>((Collection)new HashSet()){
            private static final long serialVersionUID = -2680409238179687587L;
        };
    }
}

