/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.AbstractSecsTrySendMessageLog;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Message;
import java.time.LocalDateTime;

public class Secs1TrySendMessageLog
extends AbstractSecsTrySendMessageLog {
    private static final long serialVersionUID = -5052290289868782404L;
    private static final String commonSubject = "Try-Send SECS1-Message";

    public Secs1TrySendMessageLog(AbstractSecs1Message message, LocalDateTime timestamp) {
        super(message, timestamp);
    }

    public Secs1TrySendMessageLog(AbstractSecs1Message message) {
        super(message);
    }

    @Override
    public String subject() {
        return commonSubject;
    }
}

