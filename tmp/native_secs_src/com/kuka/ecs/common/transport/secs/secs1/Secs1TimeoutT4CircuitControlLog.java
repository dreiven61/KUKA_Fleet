/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1CircuitControlLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBlock;
import java.time.LocalDateTime;

public final class Secs1TimeoutT4CircuitControlLog
extends AbstractSecs1CircuitControlLog {
    private static final long serialVersionUID = 1624501190541595887L;
    private final Secs1MessageBlock prevBlock;
    private static final String commonSubject = "SECS1-Circuit-Control T4-Timeout";

    private Secs1TimeoutT4CircuitControlLog(CharSequence subject, LocalDateTime timestamp, Secs1MessageBlock block) {
        super(subject, timestamp, block);
        this.prevBlock = block;
    }

    private Secs1TimeoutT4CircuitControlLog(CharSequence subject, Secs1MessageBlock block) {
        super(subject, block);
        this.prevBlock = block;
    }

    public Secs1MessageBlock previousMessageBlock() {
        return this.prevBlock;
    }

    public static Secs1TimeoutT4CircuitControlLog newInstance(Secs1MessageBlock block) {
        return new Secs1TimeoutT4CircuitControlLog((CharSequence)commonSubject, block);
    }

    public static Secs1TimeoutT4CircuitControlLog newInstance(Secs1MessageBlock block, LocalDateTime timestamp) {
        return new Secs1TimeoutT4CircuitControlLog((CharSequence)commonSubject, timestamp, block);
    }
}

