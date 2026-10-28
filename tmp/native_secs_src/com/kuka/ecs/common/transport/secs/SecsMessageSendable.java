/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.sml.SmlMessage;
import java.util.Optional;

public interface SecsMessageSendable {
    public Optional<SecsMessage> send(int var1, int var2, boolean var3) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> send(int var1, int var2, boolean var3, Secs2 var4) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> send(SecsMessage var1, int var2, int var3, boolean var4) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> send(SecsMessage var1, int var2, int var3, boolean var4, Secs2 var5) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> send(SmlMessage var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> send(SecsMessage var1, SmlMessage var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;
}

