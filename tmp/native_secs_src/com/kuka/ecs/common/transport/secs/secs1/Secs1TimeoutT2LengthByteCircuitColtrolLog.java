/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1CircuitControlLog;
import java.time.LocalDateTime;

public final class Secs1TimeoutT2LengthByteCircuitColtrolLog
extends AbstractSecs1CircuitControlLog {
    private static final long serialVersionUID = 8042282283823555669L;
    private static final String commonSubject = "SECS1-Circuit-Control T2-Timeout Length-Byte";

    private Secs1TimeoutT2LengthByteCircuitColtrolLog(CharSequence subject, LocalDateTime timestamp) {
        super(subject, timestamp);
    }

    private Secs1TimeoutT2LengthByteCircuitColtrolLog(CharSequence subject) {
        super(subject);
    }

    public static Secs1TimeoutT2LengthByteCircuitColtrolLog newInstance() {
        return new Secs1TimeoutT2LengthByteCircuitColtrolLog(commonSubject);
    }

    public static Secs1TimeoutT2LengthByteCircuitColtrolLog newInstance(LocalDateTime timestamp) {
        return new Secs1TimeoutT2LengthByteCircuitColtrolLog((CharSequence)commonSubject, timestamp);
    }
}

