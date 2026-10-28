/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsCommunicator;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageBuilder;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsmsss.AbstractHsmsSsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsmsss.AbstractHsmsSsMessageBuilder;
import com.kuka.ecs.common.transport.secs.hsmsss.AbstractHsmsSsSession;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Optional;

public abstract class AbstractHsmsSsCommunicator
extends AbstractHsmsCommunicator
implements HsmsSsCommunicator {
    private final HsmsSsCommunicatorConfig config;
    private final AbstractHsmsSsSession session;
    private final HsmsMessageBuilder msgBuilder = new AbstractHsmsSsMessageBuilder(){};
    private final Object syncClosed = new Object();

    public AbstractHsmsSsCommunicator(HsmsSsCommunicatorConfig config) {
        super(config);
        this.config = config;
        this.session = new AbstractHsmsSsSession(config, config.sessionId().intValue()){};
    }

    @Override
    public int deviceId() {
        return this.getSession().deviceId();
    }

    @Override
    public int sessionId() {
        return this.getSession().sessionId();
    }

    @Override
    public void open() throws IOException {
        super.open();
        this.getSession().open();
    }

    public HsmsSsCommunicatorConfig config() {
        return this.config;
    }

    public HsmsMessageBuilder msgBuilder() {
        return this.msgBuilder;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void close() throws IOException {
        Object object = this.syncClosed;
        synchronized (object) {
            if (this.isClosed()) {
                return;
            }
            try {
                this.getSession().separate();
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            IOException ioExcept = null;
            try {
                super.close();
            }
            catch (IOException e) {
                ioExcept = e;
            }
            try {
                this.getSession().close();
            }
            catch (IOException e) {
                ioExcept = e;
            }
            if (ioExcept != null) {
                throw ioExcept;
            }
        }
    }

    @Override
    public boolean linktest() throws InterruptedException {
        return this.getSession().linktest();
    }

    @Override
    public Optional<SecsMessage> templateSend(int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.getSession().templateSend(strm, func, wbit, secs2);
    }

    @Override
    public Optional<SecsMessage> templateSend(SecsMessage primaryMsg, int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.getSession().templateSend(primaryMsg, strm, func, wbit, secs2);
    }

    @Override
    public Optional<HsmsMessage> send(HsmsMessage msg) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.getSession().send(msg);
    }

    protected AbstractHsmsSession getSession() {
        return this.session;
    }

    protected AbstractHsmsAsyncSocketChannel buildAsyncSocketChannel(AsynchronousSocketChannel channel) {
        return new AbstractHsmsSsAsyncSocketChannel(channel, this){};
    }
}

