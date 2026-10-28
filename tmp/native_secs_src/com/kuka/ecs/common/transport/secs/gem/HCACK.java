/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;

public enum HCACK {
    UNDEFINED(-1),
    OK(0),
    InvalidCommand(1),
    CannotDoNow(2),
    ParameterError(3),
    InitiatedForAsynchronousCompletion(4),
    RejectedAlreadyInDesiredCondition(5),
    InvalidObject(6);

    private final byte code;
    private final Secs2 ss;

    private HCACK(byte b) {
        this.code = b;
        this.ss = Secs2.binary(b);
    }

    public byte code() {
        return this.code;
    }

    public Secs2 secs2() {
        return this.ss;
    }

    public static HCACK get(byte b) {
        for (HCACK v : HCACK.values()) {
            if (v == UNDEFINED || v.code != b) continue;
            return v;
        }
        return UNDEFINED;
    }

    public static HCACK get(Secs2 value) throws Secs2Exception {
        byte b = value.getByte(0);
        return HCACK.get(b);
    }
}

