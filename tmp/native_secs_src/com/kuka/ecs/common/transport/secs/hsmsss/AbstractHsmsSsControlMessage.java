/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsControlMessage;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;

public abstract class AbstractHsmsSsControlMessage
extends AbstractHsmsControlMessage {
    private static final long serialVersionUID = -2422124720891020047L;

    public AbstractHsmsSsControlMessage(byte[] header) {
        super(header);
    }

    public AbstractHsmsSsControlMessage(byte[] header, Secs2 body) {
        super(header, body);
    }

    @Override
    public int sessionId() {
        return -1;
    }
}

