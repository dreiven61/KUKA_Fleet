/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;

public enum ACKC10 {
    UNDEFINED(-1),
    AcceptedForDisplay(0),
    MessageWillNotBeDisplayed(1),
    TerminalNotAvailable(2);

    private final byte code;
    private final Secs2 ss;

    private ACKC10(byte b) {
        this.code = b;
        this.ss = Secs2.binary(b);
    }

    public byte code() {
        return this.code;
    }

    public Secs2 secs2() {
        return this.ss;
    }

    public static ACKC10 get(byte b) {
        for (ACKC10 v : ACKC10.values()) {
            if (v == UNDEFINED || v.code != b) continue;
            return v;
        }
        return UNDEFINED;
    }

    public static ACKC10 get(Secs2 value) throws Secs2Exception {
        byte b = value.getByte(0);
        return ACKC10.get(b);
    }
}

