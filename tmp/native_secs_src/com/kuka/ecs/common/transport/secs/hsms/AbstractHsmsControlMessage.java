/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;

public abstract class AbstractHsmsControlMessage
extends AbstractHsmsMessage {
    private static final long serialVersionUID = 2973976500766619674L;

    public AbstractHsmsControlMessage(byte[] header) {
        super(header);
    }

    public AbstractHsmsControlMessage(byte[] header, Secs2 body) {
        super(header, body);
    }

    @Override
    public boolean isDataMessage() {
        return false;
    }

    @Override
    public int getStream() {
        return -1;
    }

    @Override
    public int getFunction() {
        return -1;
    }

    @Override
    public boolean wbit() {
        return false;
    }

    @Override
    public int deviceId() {
        return -1;
    }

    @Override
    protected String toJsonProxy() {
        return this.toControllMessageJsonProxy();
    }

    @Override
    protected String toStringProxy() {
        return this.toControllMessageStringProxy();
    }
}

