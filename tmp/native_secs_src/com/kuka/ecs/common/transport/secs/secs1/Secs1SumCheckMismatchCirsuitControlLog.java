/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1CircuitControlLog;
import java.time.LocalDateTime;

public final class Secs1SumCheckMismatchCirsuitControlLog
extends AbstractSecs1CircuitControlLog {
    private static final long serialVersionUID = 7501853603924670922L;
    private static final String commonSubject = "SECS1-Circuit-Control Sum-Check mismatch";

    private Secs1SumCheckMismatchCirsuitControlLog(CharSequence subject, LocalDateTime timestamp) {
        super(subject, timestamp);
    }

    private Secs1SumCheckMismatchCirsuitControlLog(CharSequence subject) {
        super(subject);
    }

    public static Secs1SumCheckMismatchCirsuitControlLog newInstance() {
        return new Secs1SumCheckMismatchCirsuitControlLog(commonSubject);
    }

    public static Secs1SumCheckMismatchCirsuitControlLog newInstance(LocalDateTime timestamp) {
        return new Secs1SumCheckMismatchCirsuitControlLog((CharSequence)commonSubject, timestamp);
    }
}

