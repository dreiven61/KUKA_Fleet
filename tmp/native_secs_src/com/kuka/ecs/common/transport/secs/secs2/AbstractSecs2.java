/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BuildException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesPackBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import com.kuka.ecs.common.transport.secs.secs2.Secs2IllegalDataFormatException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2LengthByteOutOfRangeException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class AbstractSecs2
implements Secs2,
Serializable {
    private static final long serialVersionUID = 7168919889159900080L;

    protected abstract void putBytesPack(Secs2BytesPackBuilder var1) throws Secs2BuildException;

    protected void putHeadAndBodyBytesToBytesPack(Secs2BytesPackBuilder builder, byte[] body) throws Secs2BuildException {
        this.putHeaderBytesToBytesPack(builder, body.length);
        builder.put(body);
    }

    protected void putHeaderBytesToBytesPack(Secs2BytesPackBuilder builder, int length) throws Secs2BuildException {
        if (length > 0xFFFFFF || length < 0) {
            throw new Secs2LengthByteOutOfRangeException("length: " + length);
        }
        byte b = this.secs2Item().code();
        if (length > 65535) {
            builder.put(new byte[]{(byte)(b | 3), (byte)(length >> 16), (byte)(length >> 8), (byte)length});
        } else if (length > 255) {
            builder.put(new byte[]{(byte)(b | 2), (byte)(length >> 8), (byte)length});
        } else {
            builder.put(new byte[]{(byte)(b | 1), (byte)length});
        }
    }

    private static LinkedList<Integer> createLinkedList(int ... indices) {
        LinkedList<Integer> ll = new LinkedList<Integer>();
        IntStream.of(indices).boxed().forEach(ll::add);
        return ll;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Stream<Secs2> stream() {
        return Stream.empty();
    }

    @Override
    public Iterator<Secs2> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public Secs2 get() {
        return this;
    }

    @Override
    public final Secs2 get(int ... indices) throws Secs2Exception {
        return this.get(AbstractSecs2.createLinkedList(indices));
    }

    protected AbstractSecs2 get(LinkedList<Integer> list) throws Secs2Exception {
        if (list.isEmpty()) {
            return this;
        }
        throw new Secs2IllegalDataFormatException("Not Secs2List");
    }

    @Override
    public final String getAscii(int ... indices) throws Secs2Exception {
        return this.get(indices).getAscii();
    }

    @Override
    public String getAscii() throws Secs2Exception {
        throw new Secs2IllegalDataFormatException("Not Secs2Ascii");
    }

    @Override
    public final byte getByte(int ... indices) throws Secs2Exception {
        LinkedList<Integer> list = AbstractSecs2.createLinkedList(indices);
        int lastIndex = list.removeLast();
        return this.get(list).getByte(lastIndex);
    }

    protected byte getByte(int index) throws Secs2Exception {
        throw new Secs2IllegalDataFormatException("Not Secs2Byte");
    }

    @Override
    public final boolean getBoolean(int ... indices) throws Secs2Exception {
        LinkedList<Integer> list = AbstractSecs2.createLinkedList(indices);
        int lastIndex = list.removeLast();
        return this.get(list).getBoolean(lastIndex);
    }

    protected boolean getBoolean(int index) throws Secs2Exception {
        throw new Secs2IllegalDataFormatException("Not Secs2Boolean");
    }

    @Override
    public final int getInt(int ... indices) throws Secs2Exception {
        LinkedList<Integer> list = AbstractSecs2.createLinkedList(indices);
        int lastIndex = list.removeLast();
        return this.get(list).getInt(lastIndex);
    }

    protected int getInt(int index) throws Secs2Exception {
        throw new Secs2IllegalDataFormatException("Not Secs2Number");
    }

    @Override
    public final long getLong(int ... indices) throws Secs2Exception {
        LinkedList<Integer> list = AbstractSecs2.createLinkedList(indices);
        int lastIndex = list.removeLast();
        return this.get(list).getLong(lastIndex);
    }

    protected long getLong(int index) throws Secs2Exception {
        throw new Secs2IllegalDataFormatException("Not Secs2Number");
    }

    @Override
    public final BigInteger getBigInteger(int ... indices) throws Secs2Exception {
        LinkedList<Integer> list = AbstractSecs2.createLinkedList(indices);
        int lastIndex = list.removeLast();
        return this.get(list).getBigInteger(lastIndex);
    }

    protected BigInteger getBigInteger(int index) throws Secs2Exception {
        throw new Secs2IllegalDataFormatException("Not Secs2Number");
    }

    @Override
    public final float getFloat(int ... indices) throws Secs2Exception {
        LinkedList<Integer> list = AbstractSecs2.createLinkedList(indices);
        int lastIndex = list.removeLast();
        return this.get(list).getFloat(lastIndex);
    }

    protected float getFloat(int index) throws Secs2Exception {
        throw new Secs2IllegalDataFormatException("Not Secs2Float");
    }

    @Override
    public final double getDouble(int ... indices) throws Secs2Exception {
        LinkedList<Integer> list = AbstractSecs2.createLinkedList(indices);
        int lastIndex = list.removeLast();
        return this.get(list).getDouble(lastIndex);
    }

    protected double getDouble(int index) throws Secs2Exception {
        throw new Secs2IllegalDataFormatException("Not Secs2Double");
    }

    @Override
    public Number getNumber(int ... indices) throws Secs2Exception {
        LinkedList<Integer> list = AbstractSecs2.createLinkedList(indices);
        int lastIndex = list.removeLast();
        return this.get(list).getNumber(lastIndex);
    }

    protected Number getNumber(int index) throws Secs2Exception {
        throw new Secs2IllegalDataFormatException("Not Secs2Number");
    }

    public String toString() {
        return "<" + this.secs2Item().symbol() + " [" + this.toStringSize() + "] " + this.toStringValue() + ">";
    }

    protected int toStringSize() {
        return this.size();
    }

    protected abstract String toStringValue();

    @Override
    public String toJson() {
        return "{\"f\":\"" + this.secs2Item().symbol() + "\",\"v\":" + this.toJsonValue() + "}";
    }

    protected abstract String toJsonValue();

    public int hashCode() {
        return this.toJson().hashCode();
    }

    public boolean equals(Object o) {
        if (o != null && o instanceof AbstractSecs2) {
            return ((AbstractSecs2)o).toJson().equals(this.toJson());
        }
        return false;
    }
}

