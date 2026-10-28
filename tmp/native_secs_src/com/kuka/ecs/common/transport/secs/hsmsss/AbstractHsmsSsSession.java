/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsSession;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicatorConfig;

public abstract class AbstractHsmsSsSession
extends AbstractHsmsSession {
    private final int sessionId;

    public AbstractHsmsSsSession(HsmsSsCommunicatorConfig config, int sessionId) {
        super(config);
        this.sessionId = sessionId;
    }

    @Override
    public int deviceId() {
        return this.sessionId;
    }

    @Override
    public int sessionId() {
        return this.sessionId;
    }
}

