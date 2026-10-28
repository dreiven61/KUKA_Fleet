/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.AbstractSecs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BuildException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesPack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Secs2BytesPackBuilder {
    private final List<byte[]> bss = new ArrayList<byte[]>();
    private final int bytesSize;
    private byte[] lastbs;
    private int pos;

    private Secs2BytesPackBuilder(int bytesSize) {
        this.bytesSize = bytesSize;
        this.lastbs = new byte[bytesSize];
        this.pos = 0;
    }

    protected void put(byte b) {
        this.put(new byte[]{b});
    }

    protected void put(byte[] bs) {
        int i = 0;
        int m = bs.length;
        while (i < m) {
            if (m - i > this.bytesSize - this.pos) {
                while (this.pos < this.bytesSize) {
                    this.lastbs[this.pos] = bs[i];
                    ++this.pos;
                    ++i;
                }
                this.bss.add(Arrays.copyOf(this.lastbs, this.bytesSize));
                this.pos = 0;
                continue;
            }
            while (i < m) {
                this.lastbs[this.pos] = bs[i];
                ++i;
                ++this.pos;
            }
        }
    }

    private List<byte[]> getBytes() {
        ArrayList<byte[]> aa = new ArrayList<byte[]>();
        aa.addAll(this.bss);
        aa.add(Arrays.copyOf(this.lastbs, this.pos));
        return aa;
    }

    public static Secs2BytesPack build(int bytesSize, Secs2 secs2) throws Secs2BuildException {
        Secs2BytesPackBuilder bb = new Secs2BytesPackBuilder(bytesSize);
        if (secs2 instanceof AbstractSecs2) {
            ((AbstractSecs2)secs2).putBytesPack(bb);
            return new Secs2BytesPack(bb.getBytes());
        }
        throw new Secs2BuildException("cast failed");
    }
}

