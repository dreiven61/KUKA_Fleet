/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageDeselectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageReceiveListener;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageRejectReason;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageSelectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.util.Optional;

public interface HsmsAsyncSocketChannel {
    public void reading() throws HsmsException, InterruptedException;

    public void linktesting() throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public void shutdown();

    public void waitingUntilShutdown() throws InterruptedException;

    public boolean addHsmsMessageReceiveListener(HsmsMessageReceiveListener var1);

    public boolean removeHsmsMessageReceiveListener(HsmsMessageReceiveListener var1);

    public Optional<HsmsMessage> sendSelectRequest(AbstractHsmsSession var1) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public Optional<HsmsMessage> sendSelectResponse(HsmsMessage var1, HsmsMessageSelectStatus var2) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public Optional<HsmsMessage> sendDeselectRequest(AbstractHsmsSession var1) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public Optional<HsmsMessage> sendDeselectResponse(HsmsMessage var1, HsmsMessageDeselectStatus var2) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public Optional<HsmsMessage> sendLinktestRequest(AbstractHsmsSession var1) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public Optional<HsmsMessage> sendLinktestResponse(HsmsMessage var1) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public Optional<HsmsMessage> sendRejectRequest(HsmsMessage var1, HsmsMessageRejectReason var2) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public Optional<HsmsMessage> sendSeparateRequest(AbstractHsmsSession var1) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public Optional<HsmsMessage> send(AbstractHsmsSession var1, int var2, int var3, boolean var4, Secs2 var5) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public Optional<HsmsMessage> send(SecsMessage var1, int var2, int var3, boolean var4, Secs2 var5) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public Optional<HsmsMessage> sendHsmsMessage(HsmsMessage var1) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;
}

