/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractSocketAddressProperty;
import com.kuka.ecs.common.transport.secs.Property;
import com.kuka.ecs.common.transport.secs.ReadOnlySocketAddressProperty;
import java.net.SocketAddress;

public interface SocketAddressProperty
extends Property<SocketAddress>,
ReadOnlySocketAddressProperty {
    public static SocketAddressProperty newInstance(SocketAddress initial) {
        return new AbstractSocketAddressProperty(initial){
            private static final long serialVersionUID = -460713208491670175L;
        };
    }
}

