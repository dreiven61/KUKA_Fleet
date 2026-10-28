/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBlock;
import java.io.Serializable;
import java.util.Arrays;

public abstract class AbstractSecs1MessageBlock
implements Secs1MessageBlock,
Serializable {
    private static final long serialVersionUID = 1841349953959422187L;
    public static final int ZERO = 0;
    public static final int ONE = 1;
    private final Object sync = new Object();
    private final int length;
    private final byte[] bs;
    private String cacheToString;
    private Integer cacheSystemBytesKey;

    public AbstractSecs1MessageBlock(byte[] bs) {
        this.length = bs[0] & 0xFF;
        this.bs = Arrays.copyOf(bs, this.length + 3);
        this.cacheToString = null;
        this.cacheSystemBytesKey = null;
    }

    @Override
    public int deviceId() {
        return this.bs[1] << 8 & 0x7F00 | this.bs[2] & 0xFF;
    }

    @Override
    public boolean ebit() {
        return (this.bs[5] & 0x80) == 128;
    }

    @Override
    public int blockNumber() {
        return this.bs[5] << 8 & 0x7F00 | this.bs[6] & 0xFF;
    }

    @Override
    public boolean isFirstBlock() {
        int n = this.blockNumber();
        return n == 0 || n == 1;
    }

    @Override
    public int length() {
        return this.length;
    }

    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(this.bs, this.bs.length);
    }

    @Override
    public boolean checkSum() {
        int i;
        int v = this.bs[i + 1] << 8 & 0xFF00;
        v |= this.bs[i + 2] & 0xFF;
        for (i = this.length(); i > 0; --i) {
            v -= this.bs[i] & 0xFF;
        }
        return v == 0;
    }

    @Override
    public boolean equalsSystemBytes(Secs1MessageBlock otherBlock) {
        byte[] o = otherBlock.getBytes();
        return o[7] == this.bs[7] && o[8] == this.bs[8] && o[9] == this.bs[9] && o[10] == this.bs[10];
    }

    @Override
    public boolean isNextBlock(Secs1MessageBlock nextBlock) {
        int n = this.blockNumber();
        if (n == 0) {
            return false;
        }
        return nextBlock.blockNumber() == n + 1;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public String toString() {
        AbstractSecs1MessageBlock abstractSecs1MessageBlock = this;
        synchronized (abstractSecs1MessageBlock) {
            if (this.cacheToString == null) {
                try {
                    this.cacheToString = "[" + String.format("%02X", this.bs[1]) + " " + String.format("%02X", this.bs[2]) + "|" + String.format("%02X", this.bs[3]) + " " + String.format("%02X", this.bs[4]) + "|" + String.format("%02X", this.bs[5]) + " " + String.format("%02X", this.bs[6]) + "|" + String.format("%02X", this.bs[7]) + " " + String.format("%02X", this.bs[8]) + " " + String.format("%02X", this.bs[9]) + " " + String.format("%02X", this.bs[10]) + "] length: " + this.length();
                }
                catch (IndexOutOfBoundsException e) {
                    this.cacheToString = "#toString failed";
                }
            }
            return this.cacheToString;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Integer systemBytesKey() {
        Object object = this.sync;
        synchronized (object) {
            if (this.cacheSystemBytesKey == null) {
                int n = this.bs[7] << 24 & 0xFF000000 | this.bs[8] << 16 & 0xFF0000 | this.bs[9] << 8 & 0xFF00 | this.bs[10] & 0xFF;
                this.cacheSystemBytesKey = n;
            }
            return this.cacheSystemBytesKey;
        }
    }
}

