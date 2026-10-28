/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.AbstractSecsSendedMessageLog;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import java.time.LocalDateTime;

public class HsmsSendedMessageLog
extends AbstractSecsSendedMessageLog {
    private static final long serialVersionUID = 7358456946949913863L;
    private static final String commonSubject = "Sended HSMS-Message";

    public HsmsSendedMessageLog(AbstractHsmsMessage message, LocalDateTime timestamp) {
        super(message, timestamp);
    }

    public HsmsSendedMessageLog(AbstractHsmsMessage message) {
        super(message);
    }

    @Override
    public String subject() {
        return commonSubject;
    }
}

