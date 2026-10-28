/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.util;

import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsMessageSendable;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.util.AbstractEntityMessageSender;
import java.util.Optional;

public interface EntityMessageSender
extends SecsMessageSendable {
    public void setSendS9F9(boolean var1);

    public Optional<SecsMessage> sendS9F9(SecsWaitReplyMessageException var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public static EntityMessageSender newInstance(SecsCommunicator communicator) {
        return new AbstractEntityMessageSender(communicator){};
    }
}

