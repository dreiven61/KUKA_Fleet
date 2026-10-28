/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractStringProperty;
import com.kuka.ecs.common.transport.secs.Property;
import com.kuka.ecs.common.transport.secs.WritableStringValue;

public interface StringProperty
extends Property<String>,
WritableStringValue {
    public static StringProperty newInstance(CharSequence initial) {
        return new AbstractStringProperty(initial){
            private static final long serialVersionUID = -2385258740337484595L;
        };
    }
}

