/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1CircuitControlLog;
import java.time.LocalDateTime;
import java.util.Optional;

public final class Secs1RetryCircuitControlLog
extends AbstractSecs1CircuitControlLog {
    private static final long serialVersionUID = 7325289626109481607L;
    private final int retryCount;
    private static final String commonSubjectHeader = "SECS1-Circuit-Control RETRY count=";

    private Secs1RetryCircuitControlLog(CharSequence subject, LocalDateTime timestamp, int retryCount) {
        super(subject, timestamp, retryCount);
        this.retryCount = retryCount;
    }

    private Secs1RetryCircuitControlLog(CharSequence subject, int retryCount) {
        super(subject, retryCount);
        this.retryCount = retryCount;
    }

    @Override
    public Optional<String> optionalValueString() {
        return Optional.empty();
    }

    public int retryCount() {
        return this.retryCount;
    }

    private static String createSubject(int retryCount) {
        return commonSubjectHeader + retryCount;
    }

    public static Secs1RetryCircuitControlLog newInstance(int retryCount) {
        return new Secs1RetryCircuitControlLog((CharSequence)Secs1RetryCircuitControlLog.createSubject(retryCount), retryCount);
    }

    public static Secs1RetryCircuitControlLog newInstance(int retryCount, LocalDateTime timestamp) {
        return new Secs1RetryCircuitControlLog((CharSequence)Secs1RetryCircuitControlLog.createSubject(retryCount), timestamp, retryCount);
    }
}

