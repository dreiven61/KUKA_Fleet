/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.AbstractSecs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BuildException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesPackBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import com.kuka.ecs.common.transport.secs.secs2.Secs2IndexOutOfBoundsException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Secs2Boolean
extends AbstractSecs2 {
    private static final long serialVersionUID = -7190678080526078554L;
    private static Secs2Item secs2Item = Secs2Item.BOOLEAN;
    private static final byte BYTE_TRUE = -1;
    private static final byte BYTE_FALSE = 0;
    private static final String STRING_TRUE = "TRUE";
    private static final String STRING_FALSE = "FALSE";
    private List<Boolean> bools;
    private byte[] bytes;

    public Secs2Boolean() {
        this(new boolean[0]);
    }

    public Secs2Boolean(boolean ... bools) {
        Objects.requireNonNull(bools);
        this.bools = new ArrayList<Boolean>();
        for (boolean b : bools) {
            this.bools.add(b);
        }
        this.bytes = null;
    }

    public Secs2Boolean(List<Boolean> bools) {
        Objects.requireNonNull(bools);
        this.bools = new ArrayList<Boolean>(bools);
        this.bytes = null;
    }

    public Secs2Boolean(byte[] bs) {
        Objects.requireNonNull(bs);
        this.bools = null;
        this.bytes = Arrays.copyOf(bs, bs.length);
    }

    @Override
    public int size() {
        return this.bools().size();
    }

    @Override
    protected void putBytesPack(Secs2BytesPackBuilder builder) throws Secs2BuildException {
        this.putHeadAndBodyBytesToBytesPack(builder, this.bytes());
    }

    private synchronized List<Boolean> bools() {
        if (this.bools == null) {
            this.bools = new ArrayList<Boolean>();
            for (byte b : this.bytes()) {
                this.bools.add(b != 0);
            }
        }
        return this.bools;
    }

    private synchronized byte[] bytes() {
        if (this.bytes == null) {
            List<Boolean> bs = this.bools();
            this.bytes = new byte[bs.size()];
            int i = 0;
            for (Boolean b : bs) {
                this.bytes[i] = b != false ? -1 : 0;
                ++i;
            }
        }
        return this.bytes;
    }

    @Override
    protected boolean getBoolean(int index) throws Secs2Exception {
        try {
            return this.bools().get(index);
        }
        catch (IndexOutOfBoundsException e) {
            throw new Secs2IndexOutOfBoundsException(e);
        }
    }

    @Override
    public Secs2Item secs2Item() {
        return secs2Item;
    }

    @Override
    protected String toJsonValue() {
        return this.bools().stream().map(f -> f != false ? "true" : "false").collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    protected String toStringValue() {
        return this.bools().stream().map(b -> b != false ? STRING_TRUE : STRING_FALSE).collect(Collectors.joining(" "));
    }
}

