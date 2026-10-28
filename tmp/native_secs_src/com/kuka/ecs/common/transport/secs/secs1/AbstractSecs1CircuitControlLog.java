/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1CircuitControlLog;
import java.time.LocalDateTime;

public abstract class AbstractSecs1CircuitControlLog
extends AbstractSecsLog
implements Secs1CircuitControlLog {
    private static final long serialVersionUID = -8516994973196921648L;

    public AbstractSecs1CircuitControlLog(CharSequence subject, LocalDateTime timestamp, Object value) {
        super(subject, timestamp, value);
    }

    public AbstractSecs1CircuitControlLog(CharSequence subject, Object value) {
        super(subject, value);
    }

    public AbstractSecs1CircuitControlLog(CharSequence subject, LocalDateTime timestamp) {
        super(subject, timestamp);
    }

    public AbstractSecs1CircuitControlLog(CharSequence subject) {
        super(subject);
    }
}

