/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import com.kuka.ecs.common.transport.secs.AbstractSecsMessage;
import com.kuka.ecs.common.transport.secs.SecsSendedMessageLog;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public abstract class AbstractSecsSendedMessageLog
extends AbstractSecsLog
implements SecsSendedMessageLog {
    private static final long serialVersionUID = 7389391644974019197L;
    private static final String commonSubject = "Sended SECS-Message";
    private final AbstractSecsMessage msg;

    public AbstractSecsSendedMessageLog(AbstractSecsMessage message, LocalDateTime timestamp) {
        super(commonSubject, timestamp, Objects.requireNonNull(message));
        this.msg = message;
    }

    public AbstractSecsSendedMessageLog(AbstractSecsMessage message) {
        super((CharSequence)commonSubject, Objects.requireNonNull(message));
        this.msg = message;
    }

    @Override
    public String subject() {
        return commonSubject;
    }

    @Override
    public Optional<AbstractSecsMessage> optionalAbstractSecsMessage() {
        return Optional.of(this.msg);
    }
}

