/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractBooleanProperty;
import com.kuka.ecs.common.transport.secs.Property;
import com.kuka.ecs.common.transport.secs.ReadOnlyBooleanProperty;
import com.kuka.ecs.common.transport.secs.WritableBooleanValue;

public interface BooleanProperty
extends Property<Boolean>,
ReadOnlyBooleanProperty,
WritableBooleanValue {
    public static BooleanProperty newInstance(boolean initial) {
        return new AbstractBooleanProperty(initial){
            private static final long serialVersionUID = 5083511805576401840L;
        };
    }
}

