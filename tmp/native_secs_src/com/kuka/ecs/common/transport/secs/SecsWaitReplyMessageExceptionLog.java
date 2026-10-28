/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsThrowableLog;
import java.util.Optional;

public interface SecsWaitReplyMessageExceptionLog
extends SecsThrowableLog {
    public Optional<SecsMessage> referenceSecsMessage();
}

