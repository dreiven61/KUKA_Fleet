/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

public interface Secs1MessageBlock {
    public int deviceId();

    public boolean ebit();

    public int blockNumber();

    public boolean isFirstBlock();

    public int length();

    public byte[] getBytes();

    public boolean checkSum();

    public boolean equalsSystemBytes(Secs1MessageBlock var1);

    public boolean isNextBlock(Secs1MessageBlock var1);
}

