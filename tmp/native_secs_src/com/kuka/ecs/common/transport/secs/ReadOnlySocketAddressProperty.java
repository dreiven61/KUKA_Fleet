/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.ReadOnlyProperty;
import java.net.SocketAddress;

public interface ReadOnlySocketAddressProperty
extends ReadOnlyProperty<SocketAddress> {
    public SocketAddress getSocketAddress();
}

