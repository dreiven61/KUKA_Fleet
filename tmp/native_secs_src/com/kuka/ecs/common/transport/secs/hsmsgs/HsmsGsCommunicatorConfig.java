/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.BooleanProperty;
import com.kuka.ecs.common.transport.secs.CollectionProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyBooleanProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyCollectionProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlySocketAddressProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.SocketAddressProperty;
import com.kuka.ecs.common.transport.secs.TimeProperty;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsCommunicatorConfig;
import java.net.SocketAddress;
import java.util.Objects;

public class HsmsGsCommunicatorConfig
extends AbstractHsmsCommunicatorConfig {
    private static final long serialVersionUID = -5254158215625876513L;
    private final CollectionProperty<Integer> sessionIds = CollectionProperty.newSet();
    private final SocketAddressProperty socketAddr = SocketAddressProperty.newInstance(null);
    private final BooleanProperty isTrySelectRequest = BooleanProperty.newInstance(false);
    private final TimeProperty retrySelectRequestTimeout = TimeProperty.newInstance(10.0f);

    public boolean addSessionId(int id) {
        if (id < 0 || id > 65535) {
            throw new AddSessionIdIllegalArgumentException(id);
        }
        return this.sessionIds.add(id);
    }

    public boolean removeSessionId(int id) {
        return this.sessionIds.remove(id);
    }

    public ReadOnlyCollectionProperty<Integer> sessionIds() {
        return this.sessionIds;
    }

    public void socketAddress(SocketAddress socketAddress) {
        this.socketAddr.set(Objects.requireNonNull(socketAddress));
    }

    public ReadOnlySocketAddressProperty socketAddress() {
        return this.socketAddr;
    }

    public void isTrySelectRequest(boolean f) {
        this.isTrySelectRequest.set(f);
    }

    public ReadOnlyBooleanProperty isTrySelectRequest() {
        return this.isTrySelectRequest;
    }

    public void retrySelectRequestTimeout(float v) {
        if (v <= 0.0f) {
            throw new RetrySelectRequestTimeoutIllegalArgumentException(v);
        }
        this.retrySelectRequestTimeout.set(v);
    }

    public ReadOnlyTimeProperty retrySelectRequestTimeout() {
        return this.retrySelectRequestTimeout;
    }

    private static class RetrySelectRequestTimeoutIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = -2444363506021169418L;

        public RetrySelectRequestTimeoutIllegalArgumentException(float value) {
            super("retrySelectRequestTimeout value requires 0.0F, value=" + value);
        }
    }

    private static class AddSessionIdIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = -9211783480973747830L;

        public AddSessionIdIllegalArgumentException(int id) {
            super("Session-ID is in 0 - 65535, id=" + id);
        }
    }
}

