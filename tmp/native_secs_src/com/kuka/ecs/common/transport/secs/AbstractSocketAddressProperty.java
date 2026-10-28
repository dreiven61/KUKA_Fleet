/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractProperty;
import com.kuka.ecs.common.transport.secs.SocketAddressProperty;
import java.net.SocketAddress;
import java.util.Objects;

public abstract class AbstractSocketAddressProperty
extends AbstractProperty<SocketAddress>
implements SocketAddressProperty {
    private static final long serialVersionUID = -7185437946139606493L;

    public AbstractSocketAddressProperty(SocketAddress initial) {
        super(initial);
    }

    @Override
    public void set(SocketAddress v) {
        super.set(Objects.requireNonNull(v));
    }

    @Override
    public SocketAddress getSocketAddress() {
        SocketAddress a = (SocketAddress)this.get();
        if (a == null) {
            throw new IllegalStateException("SocketAddress not setted");
        }
        return a;
    }
}

