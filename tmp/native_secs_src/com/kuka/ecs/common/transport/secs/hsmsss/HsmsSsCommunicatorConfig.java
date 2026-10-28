/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.NumberProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyNumberProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlySocketAddressProperty;
import com.kuka.ecs.common.transport.secs.SocketAddressProperty;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsProtocol;
import java.net.SocketAddress;
import java.util.Objects;

public class HsmsSsCommunicatorConfig
extends AbstractHsmsCommunicatorConfig {
    private static final long serialVersionUID = 71663164318605890L;
    private final NumberProperty sessionId = NumberProperty.newInstance(10);
    private final SocketAddressProperty socketAddr = SocketAddressProperty.newInstance(null);

    @Deprecated
    public void protocol(HsmsSsProtocol protocol) {
        switch (protocol) {
            case PASSIVE: {
                this.connectionMode(HsmsConnectionMode.PASSIVE);
                break;
            }
            case ACTIVE: {
                this.connectionMode(HsmsConnectionMode.ACTIVE);
            }
        }
    }

    @Deprecated
    public ReadOnlyProperty<HsmsConnectionMode> protocol() {
        return this.connectionMode();
    }

    public void socketAddress(SocketAddress socketAddress) {
        this.socketAddr.set(Objects.requireNonNull(socketAddress));
    }

    public ReadOnlySocketAddressProperty socketAddress() {
        return this.socketAddr;
    }

    public void sessionId(int id) {
        if (id < 0 || id > Short.MAX_VALUE) {
            throw new SessionIdIllegalArgumentException(id);
        }
        this.sessionId.set(id);
    }

    public ReadOnlyNumberProperty sessionId() {
        return this.sessionId;
    }

    private static class SessionIdIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = -5233865584009807866L;

        public SessionIdIllegalArgumentException(int id) {
            super("SESSION-ID is in 0 - 32767, id=" + id);
        }
    }
}

