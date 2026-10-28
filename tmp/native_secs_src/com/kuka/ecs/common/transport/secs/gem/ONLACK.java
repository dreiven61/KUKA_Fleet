/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;

public enum ONLACK {
    UNDEFINED(-1),
    OK(0),
    Refused(1),
    AlreadyOnline(2);

    private final byte code;
    private final Secs2 ss;

    private ONLACK(byte b) {
        this.code = b;
        this.ss = Secs2.binary(b);
    }

    public byte code() {
        return this.code;
    }

    public Secs2 secs2() {
        return this.ss;
    }

    public static ONLACK get(byte b) {
        for (ONLACK v : ONLACK.values()) {
            if (v == UNDEFINED || v.code != b) continue;
            return v;
        }
        return UNDEFINED;
    }

    public static ONLACK get(Secs2 value) throws Secs2Exception {
        byte b = value.getByte(0);
        return ONLACK.get(b);
    }
}

