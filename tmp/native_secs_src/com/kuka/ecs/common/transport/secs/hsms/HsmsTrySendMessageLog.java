/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.AbstractSecsTrySendMessageLog;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import java.time.LocalDateTime;

public class HsmsTrySendMessageLog
extends AbstractSecsTrySendMessageLog {
    private static final long serialVersionUID = 5307779082574384425L;
    private static final String commonSubject = "Try-Send HSMS-Message";

    public HsmsTrySendMessageLog(AbstractHsmsMessage message, LocalDateTime timestamp) {
        super(message, timestamp);
    }

    public HsmsTrySendMessageLog(AbstractHsmsMessage message) {
        super(message);
    }

    @Override
    public String subject() {
        return commonSubject;
    }
}

