/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractNumberProperty;
import com.kuka.ecs.common.transport.secs.Property;
import com.kuka.ecs.common.transport.secs.ReadOnlyNumberProperty;
import com.kuka.ecs.common.transport.secs.WritableNumberValue;

public interface NumberProperty
extends Property<Number>,
ReadOnlyNumberProperty,
WritableNumberValue {
    public static NumberProperty newInstance(Number initial) {
        return new AbstractNumberProperty(initial){
            private static final long serialVersionUID = 8783334196281463984L;
        };
    }

    public static NumberProperty newInstance(int initial) {
        return NumberProperty.newInstance((Number)initial);
    }

    public static NumberProperty newInstance(long initial) {
        return NumberProperty.newInstance((Number)initial);
    }

    public static NumberProperty newInstance(float initial) {
        return NumberProperty.newInstance(Float.valueOf(initial));
    }

    public static NumberProperty newInstance(double initial) {
        return NumberProperty.newInstance((Number)initial);
    }
}

