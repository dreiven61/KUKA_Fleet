/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.AbstractSecsReceiveMessageLog;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import java.time.LocalDateTime;

public class HsmsReceiveMessageLog
extends AbstractSecsReceiveMessageLog {
    private static final long serialVersionUID = 2805162103637679173L;
    private static final String commonSubject = "Receive HSMS-Message";

    public HsmsReceiveMessageLog(AbstractHsmsMessage message, LocalDateTime timestamp) {
        super(message, timestamp);
    }

    public HsmsReceiveMessageLog(AbstractHsmsMessage message) {
        super(message);
    }

    @Override
    public String subject() {
        return commonSubject;
    }
}

