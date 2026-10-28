/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsLinktest;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageBuilder;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsTransactionManager;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsLinktest;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsMessageBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;

public abstract class AbstractHsmsGsAsyncSocketChannel
extends AbstractHsmsAsyncSocketChannel {
    private final AbstractHsmsGsCommunicator comm;
    private final AbstractHsmsLinktest linktest;
    private final HsmsTransactionManager<AbstractHsmsMessage> transMgr = new HsmsTransactionManager();

    public AbstractHsmsGsAsyncSocketChannel(AsynchronousSocketChannel channel, AbstractHsmsGsCommunicator communicator) {
        super(channel);
        this.comm = communicator;
        this.linktest = new AbstractHsmsGsLinktest(this, communicator){};
    }

    @Override
    public void linktesting() throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        this.linktest.testing();
    }

    @Override
    protected HsmsMessageBuilder messageBuilder() {
        return this.comm.msgBuilder();
    }

    @Override
    protected AbstractHsmsMessage buildMessageFromBytes(byte[] header, List<byte[]> bodies) throws Secs2BytesParseException {
        return HsmsGsMessageBuilder.fromBytes(header, bodies);
    }

    @Override
    protected AbstractHsmsMessage buildMessageFromMessage(HsmsMessage message) {
        return HsmsGsMessageBuilder.fromMessage(message);
    }

    @Override
    protected HsmsTransactionManager<AbstractHsmsMessage> transactionManager() {
        return this.transMgr;
    }

    @Override
    protected void notifyLog(AbstractSecsLog log) throws InterruptedException {
        this.comm.notifyLog(log);
    }

    @Override
    protected void notifyTrySendHsmsMsgPassThrough(HsmsMessage msg) throws InterruptedException {
        this.comm.notifyTrySendHsmsMessagePassThrough(msg);
    }

    @Override
    protected void notifySendedHsmsMsgPassThrough(HsmsMessage msg) throws InterruptedException {
        this.comm.notifySendedHsmsMessagePassThrough(msg);
    }

    @Override
    protected void notifyReceiveHsmsMessagePassThrough(HsmsMessage msg) throws InterruptedException {
        this.comm.notifyReceiveHsmsMessagePassThrough(msg);
    }

    @Override
    protected ReadOnlyTimeProperty timeoutT3() {
        return this.comm.config().timeout().t3();
    }

    @Override
    protected ReadOnlyTimeProperty timeoutT6() {
        return this.comm.config().timeout().t6();
    }

    @Override
    protected ReadOnlyTimeProperty timeoutT8() {
        return this.comm.config().timeout().t8();
    }

    @Override
    protected void resetLinktestTimer() {
        this.linktest.resetTimer();
    }
}

