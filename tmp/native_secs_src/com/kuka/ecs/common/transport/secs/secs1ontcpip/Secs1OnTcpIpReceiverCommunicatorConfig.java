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

public class Secs1OnTcpIpReceiverCommunicatorConfig
extends AbstractSecs1CommunicatorConfig {
    private static final long serialVersionUID = 6842392464950831424L;
    private SocketAddressProperty socketAddr = SocketAddressProperty.newInstance(null);
    private TimeProperty rebindSeconds = TimeProperty.newInstance(5.0f);

    public void socketAddress(SocketAddress socketAddress) {
        this.socketAddr.set(Objects.requireNonNull(socketAddress));
    }

    public ReadOnlySocketAddressProperty socketAddress() {
        return this.socketAddr;
    }

    public void rebindSeconds(float seconds) {
        this.rebindSeconds.set(seconds);
    }

    public ReadOnlyTimeProperty rebindSeconds() {
        return this.rebindSeconds;
    }
}

