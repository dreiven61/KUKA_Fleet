/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.AbstractSecs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BuildException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesPackBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import java.util.Arrays;

public class Secs2RawBytes
extends AbstractSecs2 {
    private static final long serialVersionUID = 696538383179762711L;
    private final byte[] bs;

    public Secs2RawBytes() {
        this.bs = new byte[0];
    }

    public Secs2RawBytes(byte[] bs) {
        this.bs = Arrays.copyOf(bs, bs.length);
    }

    @Override
    public boolean isEmpty() {
        return this.bs.length == 0;
    }

    @Override
    public int size() {
        return -1;
    }

    @Override
    public Secs2Item secs2Item() {
        return Secs2Item.UNDEFINED;
    }

    @Override
    protected void putBytesPack(Secs2BytesPackBuilder builder) throws Secs2BuildException {
        builder.put(this.bs);
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    protected String toStringValue() {
        return "";
    }

    @Override
    public String toJson() {
        return "{}";
    }

    @Override
    protected String toJsonValue() {
        return "";
    }
}

