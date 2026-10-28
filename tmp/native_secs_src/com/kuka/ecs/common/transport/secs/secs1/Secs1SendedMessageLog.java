/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.AbstractSecsSendedMessageLog;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Message;
import java.time.LocalDateTime;

public class Secs1SendedMessageLog
extends AbstractSecsSendedMessageLog {
    private static final long serialVersionUID = 5464441728143108575L;
    private static final String commonSubject = "Sended SECS1-Message";

    public Secs1SendedMessageLog(AbstractSecs1Message message, LocalDateTime timestamp) {
        super(message, timestamp);
    }

    public Secs1SendedMessageLog(AbstractSecs1Message message) {
        super(message);
    }

    @Override
    public String subject() {
        return commonSubject;
    }
}

