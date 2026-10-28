/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyProperty;
import com.kuka.ecs.common.transport.secs.WritableValue;

public interface Property<T>
extends ReadOnlyProperty<T>,
WritableValue<T> {
    public static <T> Property<T> newInstance(T v) {
        return new AbstractProperty<T>((Object)v){
            private static final long serialVersionUID = 8231860884227790551L;
        };
    }
}

