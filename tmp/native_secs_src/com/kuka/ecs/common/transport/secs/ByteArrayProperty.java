/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractByteArrayProperty;
import com.kuka.ecs.common.transport.secs.Property;

public interface ByteArrayProperty
extends Property<byte[]> {
    public static ByteArrayProperty newInstance(byte[] initial) {
        return new AbstractByteArrayProperty(initial){
            private static final long serialVersionUID = -1499639983991564408L;
        };
    }
}

