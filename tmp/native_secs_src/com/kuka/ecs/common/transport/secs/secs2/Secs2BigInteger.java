/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import com.kuka.ecs.common.transport.secs.secs2.Secs2IndexOutOfBoundsException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Number;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public abstract class Secs2BigInteger
extends Secs2Number<BigInteger> {
    private static final long serialVersionUID = 6007516711524334157L;

    public Secs2BigInteger(int ... values) {
        Objects.requireNonNull(values);
        this.values = IntStream.of(values).mapToObj(BigInteger::valueOf).collect(Collectors.toList());
    }

    public Secs2BigInteger(long ... values) {
        Objects.requireNonNull(values);
        this.values = LongStream.of(values).mapToObj(BigInteger::valueOf).collect(Collectors.toList());
    }

    public Secs2BigInteger(BigInteger ... values) {
        Objects.requireNonNull(values);
        this.values = Stream.of(values).collect(Collectors.toList());
    }

    public Secs2BigInteger(List<? extends Number> values) {
        Objects.requireNonNull(values);
        this.values = values.stream().map(v -> {
            if (v instanceof BigInteger) {
                return (BigInteger)v;
            }
            return BigInteger.valueOf(v.longValue());
        }).collect(Collectors.toList());
    }

    public Secs2BigInteger(byte[] bs) {
        Objects.requireNonNull(bs);
        this.bytes = Arrays.copyOf(bs, bs.length);
    }

    @Override
    protected BigInteger getBigInteger(int index) throws Secs2Exception {
        try {
            return (BigInteger)this.values().get(index);
        }
        catch (IndexOutOfBoundsException e) {
            throw new Secs2IndexOutOfBoundsException(e);
        }
    }

    protected BigInteger getBigInteger(ByteBuffer bf, boolean unsigned) {
        byte[] val = new byte[this.secs2Item().size()];
        bf.get(val);
        return unsigned ? new BigInteger(1, val) : new BigInteger(val);
    }
}

