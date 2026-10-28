/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsLinktest;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsCommunicator;
import java.util.Iterator;
import java.util.Optional;

public abstract class AbstractHsmsGsLinktest
extends AbstractHsmsLinktest {
    private final AbstractHsmsAsyncSocketChannel asyncChannel;
    private final AbstractHsmsGsCommunicator comm;

    public AbstractHsmsGsLinktest(AbstractHsmsAsyncSocketChannel asyncChannel, AbstractHsmsGsCommunicator communicator) {
        this.asyncChannel = asyncChannel;
        this.comm = communicator;
    }

    @Override
    protected ReadOnlyTimeProperty timer() {
        return this.comm.config().linktest();
    }

    @Override
    protected Optional<HsmsMessage> send() throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        Iterator<AbstractHsmsSession> iterator = this.comm.getAbstractHsmsSessions().iterator();
        if (iterator.hasNext()) {
            AbstractHsmsSession s = iterator.next();
            return this.asyncChannel.sendLinktestRequest(s);
        }
        return Optional.empty();
    }
}

