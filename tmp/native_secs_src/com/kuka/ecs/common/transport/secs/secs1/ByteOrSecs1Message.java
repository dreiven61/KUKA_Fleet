/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBlockPack;

public final class ByteOrSecs1Message {
    private final Byte b;
    private final Secs1MessageBlockPack msg;
    private static final byte ENQ = 5;

    public ByteOrSecs1Message(Byte b, Secs1MessageBlockPack msg) {
        this.b = b;
        this.msg = msg;
    }

    public Secs1MessageBlockPack message() {
        return this.msg;
    }

    public Byte getByte() {
        return this.b;
    }

    public boolean isENQ() {
        return this.b != null && this.b == 5;
    }
}

