/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.ReadOnlyProperty;

public interface ReadOnlyNumberProperty
extends ReadOnlyProperty<Number> {
    public int intValue();

    public long longValue();

    public float floatValue();

    public double doubleValue();
}

