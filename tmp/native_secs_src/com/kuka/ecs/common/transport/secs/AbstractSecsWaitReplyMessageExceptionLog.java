/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractSecsThrowableLog;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageExceptionLog;
import java.time.LocalDateTime;
import java.util.Optional;

public abstract class AbstractSecsWaitReplyMessageExceptionLog
extends AbstractSecsThrowableLog
implements SecsWaitReplyMessageExceptionLog {
    private static final long serialVersionUID = -4007928836031665906L;
    private final SecsWaitReplyMessageException cause;

    public AbstractSecsWaitReplyMessageExceptionLog(SecsWaitReplyMessageException cause) {
        super(cause);
        this.cause = cause;
    }

    public AbstractSecsWaitReplyMessageExceptionLog(SecsWaitReplyMessageException cause, LocalDateTime timestamp) {
        super(cause, timestamp);
        this.cause = cause;
    }

    @Override
    public Optional<SecsMessage> referenceSecsMessage() {
        return this.cause.secsMessage();
    }

    @Override
    public Optional<String> optionalValueString() {
        return this.referenceSecsMessage().map(msg -> AbstractSecsWaitReplyMessageExceptionLog.createHeader10BytesString(msg));
    }

    private static String createHeader10BytesString(SecsMessage msg) {
        byte[] bs = msg.header10Bytes();
        return "[" + AbstractSecsWaitReplyMessageExceptionLog.s02X(bs[0]) + " " + AbstractSecsWaitReplyMessageExceptionLog.s02X(bs[1]) + "|" + AbstractSecsWaitReplyMessageExceptionLog.s02X(bs[2]) + " " + AbstractSecsWaitReplyMessageExceptionLog.s02X(bs[3]) + "|" + AbstractSecsWaitReplyMessageExceptionLog.s02X(bs[4]) + " " + AbstractSecsWaitReplyMessageExceptionLog.s02X(bs[5]) + "|" + AbstractSecsWaitReplyMessageExceptionLog.s02X(bs[6]) + " " + AbstractSecsWaitReplyMessageExceptionLog.s02X(bs[7]) + " " + AbstractSecsWaitReplyMessageExceptionLog.s02X(bs[8]) + " " + AbstractSecsWaitReplyMessageExceptionLog.s02X(bs[9]) + "]";
    }

    private static String s02X(byte b) {
        return String.format("%02X", b);
    }
}

