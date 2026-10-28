/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractTimeProperty;
import com.kuka.ecs.common.transport.secs.NumberProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;

public interface TimeProperty
extends NumberProperty,
ReadOnlyTimeProperty {
    public static TimeProperty newInstance(Number initial) {
        return new AbstractTimeProperty(initial){
            private static final long serialVersionUID = -3059262099117306526L;
        };
    }

    public static TimeProperty newInstance(int initial) {
        return TimeProperty.newInstance((Number)initial);
    }

    public static TimeProperty newInstance(long initial) {
        return TimeProperty.newInstance((Number)initial);
    }

    public static TimeProperty newInstance(float initial) {
        return TimeProperty.newInstance(Float.valueOf(initial));
    }

    public static TimeProperty newInstance(double initial) {
        return TimeProperty.newInstance((Number)initial);
    }
}

