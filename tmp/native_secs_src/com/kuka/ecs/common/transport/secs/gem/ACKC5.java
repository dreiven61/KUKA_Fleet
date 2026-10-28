/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;

public enum ACKC5 {
    UNDEFINED(-1),
    OK(0);

    private final byte code;
    private final Secs2 ss;

    private ACKC5(byte b) {
        this.code = b;
        this.ss = Secs2.binary(b);
    }

    public byte code() {
        return this.code;
    }

    public Secs2 secs2() {
        return this.ss;
    }

    public static ACKC5 get(byte b) {
        for (ACKC5 v : ACKC5.values()) {
            if (v == UNDEFINED || v.code != b) continue;
            return v;
        }
        return UNDEFINED;
    }

    public static ACKC5 get(Secs2 value) throws Secs2Exception {
        byte b = value.getByte(0);
        return ACKC5.get(b);
    }
}

