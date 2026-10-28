/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.gem.AbstractClock;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import java.time.LocalDateTime;

public interface Clock {
    public static Clock from(LocalDateTime ldt) {
        return AbstractClock.from(ldt);
    }

    public static Clock now() {
        return AbstractClock.now();
    }

    public static Clock from(Secs2 secs2) throws Secs2Exception {
        return AbstractClock.from(secs2);
    }

    public LocalDateTime toLocalDateTime();

    public Secs2 toAscii12();

    public Secs2 toAscii16();
}

