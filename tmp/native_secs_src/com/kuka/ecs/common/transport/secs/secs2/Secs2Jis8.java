/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.AbstractSecs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BuildException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesPackBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import java.util.Arrays;
import java.util.Objects;

public class Secs2Jis8
extends AbstractSecs2 {
    private static final long serialVersionUID = 4496230020131931885L;
    private static final Secs2Item secs2Item = Secs2Item.JIS8;
    private byte[] bytes;

    public Secs2Jis8(byte[] bs) {
        Objects.requireNonNull(bs);
        this.bytes = Arrays.copyOf(bs, bs.length);
    }

    private synchronized byte[] bytes() {
        if (this.bytes == null) {
            this.bytes = new byte[0];
        }
        return this.bytes;
    }

    @Override
    public int size() {
        return this.bytes().length;
    }

    @Override
    protected void putBytesPack(Secs2BytesPackBuilder builder) throws Secs2BuildException {
        this.putHeadAndBodyBytesToBytesPack(builder, this.bytes());
    }

    @Override
    public Secs2Item secs2Item() {
        return secs2Item;
    }

    @Override
    protected String toJsonValue() {
        return "false";
    }

    @Override
    protected String toStringValue() {
        return "NOT_SUPPORT";
    }
}

