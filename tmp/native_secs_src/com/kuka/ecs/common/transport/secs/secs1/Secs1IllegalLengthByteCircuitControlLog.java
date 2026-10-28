/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1CircuitControlLog;
import java.time.LocalDateTime;
import java.util.Optional;

public final class Secs1IllegalLengthByteCircuitControlLog
extends AbstractSecs1CircuitControlLog {
    private static final long serialVersionUID = 5403876623697778247L;
    private final int length;
    private static final String commonSubjectHeader = "SECS1-Circuit-Control Illegal-Length-Byte length=";

    private Secs1IllegalLengthByteCircuitControlLog(CharSequence subject, LocalDateTime timestamp, int length) {
        super(subject, timestamp, length);
        this.length = length;
    }

    private Secs1IllegalLengthByteCircuitControlLog(CharSequence subject, int length) {
        super(subject, length);
        this.length = length;
    }

    public int length() {
        return this.length;
    }

    @Override
    public Optional<String> optionalValueString() {
        return Optional.empty();
    }

    private static String createSubject(int length) {
        return commonSubjectHeader + length;
    }

    public static Secs1IllegalLengthByteCircuitControlLog newInstance(int length) {
        return new Secs1IllegalLengthByteCircuitControlLog((CharSequence)Secs1IllegalLengthByteCircuitControlLog.createSubject(length), length);
    }

    public static Secs1IllegalLengthByteCircuitControlLog newInstance(int length, LocalDateTime timestamp) {
        return new Secs1IllegalLengthByteCircuitControlLog((CharSequence)Secs1IllegalLengthByteCircuitControlLog.createSubject(length), timestamp, length);
    }
}

