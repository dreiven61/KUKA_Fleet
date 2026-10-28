/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.AbstractSecsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.util.Arrays;

public abstract class AbstractHsmsMessage
extends AbstractSecsMessage
implements HsmsMessage {
    private static final long serialVersionUID = 7234808180120409439L;
    private static final int HEADER_SIZE = 10;
    private final byte[] header;
    private final Secs2 body;
    private static final String BR = System.lineSeparator();

    public AbstractHsmsMessage(byte[] header, Secs2 body) {
        this.header = Arrays.copyOf(header, 10);
        this.body = body;
    }

    public AbstractHsmsMessage(byte[] header) {
        this.header = Arrays.copyOf(header, 10);
        this.body = Secs2.empty();
    }

    @Override
    public Secs2 secs2() {
        return this.body;
    }

    @Override
    public byte[] header10Bytes() {
        return Arrays.copyOf(this.header, 10);
    }

    @Override
    public HsmsMessageType messageType() {
        return HsmsMessageType.get(this);
    }

    @Override
    public byte pType() {
        return this.messageType().pType();
    }

    @Override
    public byte sType() {
        return this.messageType().sType();
    }

    protected int getSessionIdFromHeader() {
        return this.header[0] << 8 & 0xFF00 | this.header[1] & 0xFF;
    }

    protected String toDataMessageJsonProxy() {
        return "{\"messageType\":\"" + this.messageType().toString() + "\",\"strm\":" + this.getStream() + ",\"func\":" + this.getFunction() + ",\"wbit\":" + (this.wbit() ? "true" : "false") + ",\"sessionId\":" + this.sessionId() + ",\"systemBytes\":" + this.systemBytesKey().toString() + ",\"secs2\":" + this.secs2().toJson() + "}";
    }

    protected String toControllMessageJsonProxy() {
        return "{\"messageType\":\"" + this.messageType().toString() + "\",\"p\":" + this.pType() + ",\"s\":" + this.sType() + ",\"sessionId\":" + this.sessionId() + ",\"systemBytes\":" + this.systemBytesKey().toString() + "}";
    }

    protected String toDataMessageStringProxy() {
        String body;
        StringBuilder sb = new StringBuilder(this.toHeaderBytesString());
        sb.append(BR).append("S").append(this.getStream()).append("F").append(this.getFunction());
        if (this.wbit()) {
            sb.append(" W");
        }
        if (!(body = this.secs2().toString()).isEmpty()) {
            sb.append(BR).append(body);
        }
        sb.append(".");
        return sb.toString();
    }

    protected String toControllMessageStringProxy() {
        return this.toHeaderBytesString();
    }
}

