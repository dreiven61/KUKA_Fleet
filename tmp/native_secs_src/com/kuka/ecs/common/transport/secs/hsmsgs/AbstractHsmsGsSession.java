/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicateState;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsCommunicatorConfig;

public abstract class AbstractHsmsGsSession
extends AbstractHsmsSession {
    private final int sessionId;
    private final Object syncAsyncChannel = new Object();

    public AbstractHsmsGsSession(HsmsGsCommunicatorConfig config, int sessionId) {
        super(config);
        this.sessionId = sessionId;
    }

    @Override
    public int deviceId() {
        return -1;
    }

    @Override
    public int sessionId() {
        return this.sessionId;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean setAsyncSocketChannel(AbstractHsmsAsyncSocketChannel asyncChannel) {
        Object object = this.syncAsyncChannel;
        synchronized (object) {
            boolean f = super.setAsyncSocketChannel(asyncChannel);
            if (f) {
                this.notifyHsmsCommunicateState(HsmsCommunicateState.SELECTED);
            }
            return f;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean unsetAsyncSocketChannel() {
        Object object = this.syncAsyncChannel;
        synchronized (object) {
            boolean f = super.unsetAsyncSocketChannel();
            this.notifyHsmsCommunicateState(HsmsCommunicateState.NOT_SELECTED);
            return f;
        }
    }
}

