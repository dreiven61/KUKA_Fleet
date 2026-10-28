/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.AbstractSecsMessage;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1MessageBlock;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBlock;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractSecs1Message
extends AbstractSecsMessage
implements Secs1Message {
    private static final long serialVersionUID = -7944936333752743698L;
    private final byte[] header;
    private final Secs2 body;
    private static final String BR = System.lineSeparator();

    public AbstractSecs1Message(byte[] header, Secs2 body) {
        this.header = Arrays.copyOf(header, header.length);
        this.body = body;
    }

    public AbstractSecs1Message(byte[] header) {
        this.header = Arrays.copyOf(header, header.length);
        this.body = Secs2.empty();
    }

    @Override
    public int getStream() {
        return this.header[2] & 0x7F;
    }

    @Override
    public int getFunction() {
        return this.header[3] & 0xFF;
    }

    @Override
    public boolean wbit() {
        return (this.header[2] & 0x80) == 128;
    }

    @Override
    public Secs2 secs2() {
        return this.body;
    }

    @Override
    public int deviceId() {
        return this.header[0] << 8 & 0x7F00 | this.header[1] & 0xFF;
    }

    @Override
    public int sessionId() {
        return this.deviceId();
    }

    @Override
    public byte[] header10Bytes() {
        return Arrays.copyOf(this.header, this.header.length);
    }

    @Override
    public boolean rbit() {
        return (this.header[0] & 0x80) == 128;
    }

    @Override
    protected String toJsonProxy() {
        return "{\"strm\":" + this.getStream() + ",\"func\":" + this.getFunction() + ",\"wbit\":" + (this.wbit() ? "true" : "false") + ",\"rbit\":" + (this.rbit() ? "true" : "false") + ",\"deviceId\":" + this.deviceId() + ",\"systemBytes\":" + this.systemBytesKey().toString() + ",\"body\":" + this.secs2().toJson() + "}";
    }

    @Override
    protected String toStringProxy() {
        String ss;
        StringBuilder sb = new StringBuilder(this.toHeaderBytesString()).append(BR).append("S").append(this.getStream()).append("F").append(this.getFunction());
        if (this.wbit()) {
            sb.append(" W");
        }
        if ((ss = this.secs2().toString()).length() > 0) {
            sb.append(BR).append(ss);
        }
        return sb.append(".").toString();
    }

    @Override
    public List<Secs1MessageBlock> toBlocks() {
        return this.toAbstractBlocks().stream().map(b -> b).collect(Collectors.toList());
    }

    public abstract List<AbstractSecs1MessageBlock> toAbstractBlocks();
}

