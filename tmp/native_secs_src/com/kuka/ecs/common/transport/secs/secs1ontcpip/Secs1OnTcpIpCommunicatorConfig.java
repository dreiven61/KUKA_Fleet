/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1ontcpip;

import com.kuka.ecs.common.transport.secs.ReadOnlySocketAddressProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.SocketAddressProperty;
import com.kuka.ecs.common.transport.secs.TimeProperty;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1CommunicatorConfig;
import java.net.SocketAddress;
import java.util.Objects;

public class Secs1OnTcpIpCommunicatorConfig
extends AbstractSecs1CommunicatorConfig {
    private static final long serialVersionUID = -7468433384957790240L;
    private SocketAddressProperty socketAddr = SocketAddressProperty.newInstance(null);
    private TimeProperty reconnectSeconds = TimeProperty.newInstance(5.0f);

    public void socketAddress(SocketAddress socketAddress) {
        this.socketAddr.set(Objects.requireNonNull(socketAddress));
    }

    public ReadOnlySocketAddressProperty socketAddress() {
        return this.socketAddr;
    }

    public void reconnectSeconds(float seconds) {
        this.reconnectSeconds.set(seconds);
    }

    public ReadOnlyTimeProperty reconnectSeconds() {
        return this.reconnectSeconds;
    }
}

