/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;

public enum LRACK {
    UNDEFINED(-1),
    OK(0),
    OutOfSpace(1),
    InvalidFormat(2),
    OneOrMoreCeidLinksAlreadyDefined(3),
    OneOrMoreCeidInvalid(4),
    OneOrMoreRptidInvalid(5);

    private final byte code;
    private final Secs2 ss;

    private LRACK(byte b) {
        this.code = b;
        this.ss = Secs2.binary(b);
    }

    public byte code() {
        return this.code;
    }

    public Secs2 secs2() {
        return this.ss;
    }

    public static LRACK get(byte b) {
        for (LRACK v : LRACK.values()) {
            if (v == UNDEFINED || v.code != b) continue;
            return v;
        }
        return UNDEFINED;
    }

    public static LRACK get(Secs2 value) throws Secs2Exception {
        byte b = value.getByte(0);
        return LRACK.get(b);
    }
}

