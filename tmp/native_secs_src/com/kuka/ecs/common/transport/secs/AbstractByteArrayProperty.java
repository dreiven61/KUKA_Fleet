/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractProperty;
import com.kuka.ecs.common.transport.secs.ByteArrayProperty;
import java.util.Objects;

public abstract class AbstractByteArrayProperty
extends AbstractProperty<byte[]>
implements ByteArrayProperty {
    private static final long serialVersionUID = -3387589927771782849L;

    public AbstractByteArrayProperty(byte[] initial) {
        super(Objects.requireNonNull(initial));
    }

    @Override
    public void set(byte[] v) {
        super.set(Objects.requireNonNull(v));
    }
}

