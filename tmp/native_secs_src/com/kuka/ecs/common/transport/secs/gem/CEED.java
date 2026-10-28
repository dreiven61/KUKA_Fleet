/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import com.kuka.ecs.common.transport.secs.secs2.Secs2IllegalDataFormatException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import java.util.Objects;

public enum CEED {
    ENABLE(Secs2.bool(true)),
    DISABLE(Secs2.bool(false));

    private final Secs2 v;

    private CEED(Secs2 v) {
        this.v = v;
    }

    public Secs2 secs2() {
        return this.v;
    }

    public static CEED get(Secs2 value) throws Secs2Exception {
        if (value.secs2Item() == Secs2Item.BOOLEAN) {
            if (Objects.equals(value, DISABLE.secs2())) {
                return DISABLE;
            }
            return ENABLE;
        }
        throw new Secs2IllegalDataFormatException("CEED require BOOLEAN");
    }
}

