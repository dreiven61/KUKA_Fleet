/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;

public enum ACKC7 {
    UNDEFINED(-1),
    Accepted(0),
    PermissionNotGranted(1),
    LengthError(2),
    MatrixOverflow(3),
    PpidNotFound(4),
    UnsupportedMode(5),
    OtherError(6);

    private final byte code;
    private final Secs2 ss;

    private ACKC7(byte b) {
        this.code = b;
        this.ss = Secs2.binary(b);
    }

    public byte code() {
        return this.code;
    }

    public Secs2 secs2() {
        return this.ss;
    }

    public static ACKC7 get(byte b) {
        for (ACKC7 v : ACKC7.values()) {
            if (v == UNDEFINED || v.code != b) continue;
            return v;
        }
        return UNDEFINED;
    }

    public static ACKC7 get(Secs2 value) throws Secs2Exception {
        byte b = value.getByte(0);
        return ACKC7.get(b);
    }
}

