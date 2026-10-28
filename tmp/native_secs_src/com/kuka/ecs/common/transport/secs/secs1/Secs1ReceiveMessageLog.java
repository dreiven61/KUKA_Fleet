/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.AbstractSecsReceiveMessageLog;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Message;
import java.time.LocalDateTime;

public class Secs1ReceiveMessageLog
extends AbstractSecsReceiveMessageLog {
    private static final long serialVersionUID = 4047242282742361051L;
    private static final String commonSubject = "Receive SECS1-Message";

    public Secs1ReceiveMessageLog(AbstractSecs1Message message, LocalDateTime timestamp) {
        super(message, timestamp);
    }

    public Secs1ReceiveMessageLog(AbstractSecs1Message message) {
        super(message);
    }

    @Override
    public String subject() {
        return commonSubject;
    }
}

