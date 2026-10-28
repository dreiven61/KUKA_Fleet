/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsLinktest;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsmsss.AbstractHsmsSsCommunicator;
import java.util.Optional;

public abstract class AbstractHsmsSsLinktest
extends AbstractHsmsLinktest {
    private final AbstractHsmsAsyncSocketChannel asyncChannel;
    private final AbstractHsmsSsCommunicator comm;

    public AbstractHsmsSsLinktest(AbstractHsmsAsyncSocketChannel asyncChannel, AbstractHsmsSsCommunicator communicator) {
        this.asyncChannel = asyncChannel;
        this.comm = communicator;
    }

    @Override
    protected ReadOnlyTimeProperty timer() {
        return this.comm.config().linktest();
    }

    @Override
    protected Optional<HsmsMessage> send() throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.asyncChannel.sendLinktestRequest(this.comm.getSession());
    }
}

