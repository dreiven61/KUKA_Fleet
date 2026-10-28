/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.AbstractBaseCommunicator;
import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import com.kuka.ecs.common.transport.secs.AbstractSecsMessage;
import com.kuka.ecs.common.transport.secs.AbstractSecsThrowableLog;
import com.kuka.ecs.common.transport.secs.SecsCommunicatableStateChangeBiListener;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsLog;
import com.kuka.ecs.common.transport.secs.SecsLogListener;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsMessageReceiveBiListener;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageBuilder;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageDeselectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessagePassThroughListener;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageRejectReason;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageSelectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsMessageBuilder;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsSession;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsUnknownSessionIdException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.sml.SmlMessage;
import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class AbstractHsmsGsCommunicator
extends AbstractBaseCommunicator
implements HsmsGsCommunicator {
    private final HsmsGsCommunicatorConfig config;
    private final Set<AbstractHsmsSession> sessions;
    private final HsmsMessageBuilder msgBuilder = new AbstractHsmsGsMessageBuilder(){};
    private final Object syncClosed = new Object();
    private final Collection<SecsLogListener> logListeners = new CopyOnWriteArrayList<SecsLogListener>();
    private final BlockingQueue<AbstractSecsLog> logQueue = new LinkedBlockingQueue<AbstractSecsLog>();
    private final Collection<HsmsMessagePassThroughListener> trySendHsmsMsgPassThroughLstnrs = new CopyOnWriteArrayList<HsmsMessagePassThroughListener>();
    private final BlockingQueue<HsmsMessage> trySendHsmsMsgPassThroughQueue = new LinkedBlockingQueue<HsmsMessage>();
    private final Collection<HsmsMessagePassThroughListener> sendedHsmsMsgPassThroughLstnrs = new CopyOnWriteArrayList<HsmsMessagePassThroughListener>();
    private final BlockingQueue<HsmsMessage> sendedHsmsMsgPassThroughQueue = new LinkedBlockingQueue<HsmsMessage>();
    private final Collection<HsmsMessagePassThroughListener> recvHsmsMsgPassThroughLstnrs = new CopyOnWriteArrayList<HsmsMessagePassThroughListener>();
    private final BlockingQueue<HsmsMessage> recvHsmsMsgPassThroughQueue = new LinkedBlockingQueue<HsmsMessage>();

    public AbstractHsmsGsCommunicator(HsmsGsCommunicatorConfig config) {
        this.config = config;
        this.sessions = config.sessionIds().stream().map(i -> new AbstractHsmsGsSession(config, (int)i){}).collect(Collectors.toSet());
    }

    public HsmsGsCommunicatorConfig config() {
        return this.config;
    }

    public HsmsMessageBuilder msgBuilder() {
        return this.msgBuilder;
    }

    @Override
    public void open() throws IOException {
        super.open();
        this.executeLogQueueTask();
        this.executeTrySendHsmsMsgPassThroughQueueTask();
        this.executeSendedHsmsMsgPassThroughQueueTask();
        this.executeRecvHsmsMsgPassThroughQueueTask();
        for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
            s.open();
        }
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
                for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
                    if (s.deselect()) continue;
                    s.separate();
                }
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
            for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
                try {
                    s.close();
                }
                catch (IOException e) {
                    ioExcept = e;
                }
            }
            if (ioExcept != null) {
                throw ioExcept;
            }
        }
    }

    public Set<AbstractHsmsSession> getAbstractHsmsSessions() {
        return this.sessions;
    }

    @Override
    public Set<HsmsSession> getSessions() {
        return Collections.unmodifiableSet(this.getAbstractHsmsSessions());
    }

    @Override
    public HsmsSession getSession(int sessionId) throws HsmsGsUnknownSessionIdException {
        for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
            if (s.sessionId() != sessionId) continue;
            return s;
        }
        throw new HsmsGsUnknownSessionIdException(sessionId);
    }

    @Override
    public boolean existSession(int sessionId) {
        for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
            if (s.sessionId() != sessionId) continue;
            return true;
        }
        return false;
    }

    protected void completionAction(AsynchronousSocketChannel channel) throws InterruptedException {
        AbstractHsmsAsyncSocketChannel asyncChannel = this.buildAsyncSocketChannel(channel);
        LinkedBlockingQueue queue = new LinkedBlockingQueue();
        asyncChannel.addHsmsMessageReceiveListener(msg -> {
            try {
                queue.put(msg);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
        });
        ArrayList<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
        tasks.add(() -> {
            try {
                asyncChannel.reading();
            }
            catch (HsmsException e) {
                this.notifyLog(e);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            return null;
        });
        tasks.add(() -> {
            try {
                asyncChannel.linktesting();
            }
            catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException e) {
                this.notifyLog(e);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            return null;
        });
        tasks.add(() -> {
            try {
                this.recvMsgTask(asyncChannel, queue);
            }
            catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException e) {
                this.notifyLog(e);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            return null;
        });
        this.getAbstractHsmsSessions().forEach(session -> tasks.add(() -> {
            try {
                this.trySelectRequesting(asyncChannel, (AbstractHsmsSession)session);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            return null;
        }));
        try {
            this.executeInvokeAny(tasks);
        }
        catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
            this.notifyLog(t);
        }
    }

    private void trySelectRequesting(AbstractHsmsAsyncSocketChannel asyncChannel, AbstractHsmsSession session) throws InterruptedException {
        while (true) {
            this.config.isTrySelectRequest().waitUntilTrue();
            session.waitUntilUnsetAsyncSocketChannel();
            try {
                HsmsMessageSelectStatus status = asyncChannel.sendSelectRequest(session).map(HsmsMessageSelectStatus::get).orElse(HsmsMessageSelectStatus.UNKNOWN);
                switch (status) {
                    case SUCCESS: 
                    case ENTITY_ACTIVED: {
                        session.setAsyncSocketChannel(asyncChannel);
                        break;
                    }
                }
            }
            catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException e) {
                this.notifyLog(e);
            }
            this.config.retrySelectRequestTimeout().sleep();
        }
    }

    private void recvMsgTask(AbstractHsmsAsyncSocketChannel asyncChannel, BlockingQueue<AbstractHsmsMessage> queue) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        block8: while (true) {
            AbstractHsmsMessage msg = queue.take();
            switch (msg.messageType()) {
                case DATA: {
                    int id = msg.sessionId();
                    boolean recved = false;
                    for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
                        if (s.sessionId() != id) continue;
                        if (!s.notifyReceiveHsmsMessage(msg)) break;
                        recved = true;
                        break;
                    }
                    if (recved) continue block8;
                    asyncChannel.sendRejectRequest(msg, HsmsMessageRejectReason.NOT_SELECTED);
                    break;
                }
                case SELECT_REQ: {
                    int id = msg.sessionId();
                    boolean responsed = false;
                    for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
                        if (s.sessionId() != id) continue;
                        if (s.setAsyncSocketChannel(asyncChannel)) {
                            asyncChannel.sendSelectResponse(msg, HsmsMessageSelectStatus.SUCCESS);
                        } else if (s.equalsAsyncSocketChannel(asyncChannel)) {
                            asyncChannel.sendSelectResponse(msg, HsmsMessageSelectStatus.ENTITY_ACTIVED);
                        } else {
                            asyncChannel.sendSelectResponse(msg, HsmsMessageSelectStatus.ENTITY_ALREADY_USED);
                        }
                        responsed = true;
                        break;
                    }
                    if (responsed) continue block8;
                    asyncChannel.sendSelectResponse(msg, HsmsMessageSelectStatus.ENTITY_UNKNOWN);
                    break;
                }
                case DESELECT_REQ: {
                    int id = msg.sessionId();
                    boolean responsed = false;
                    for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
                        if (s.sessionId() != id) continue;
                        boolean f = s.unsetAsyncSocketChannel();
                        HsmsMessageDeselectStatus status = f ? HsmsMessageDeselectStatus.SUCCESS : HsmsMessageDeselectStatus.NO_SELECTED;
                        asyncChannel.sendDeselectResponse(msg, status);
                        responsed = true;
                        break;
                    }
                    if (responsed) continue block8;
                    asyncChannel.sendDeselectResponse(msg, HsmsMessageDeselectStatus.FAILED);
                    break;
                }
                case LINKTEST_REQ: {
                    asyncChannel.sendLinktestResponse(msg);
                    break;
                }
                case SEPARATE_REQ: {
                    AbstractHsmsSession s;
                    int id = msg.sessionId();
                    Iterator<AbstractHsmsSession> iterator = this.getAbstractHsmsSessions().iterator();
                    do {
                        if (!iterator.hasNext()) continue block8;
                    } while ((s = iterator.next()).sessionId() != id);
                    s.unsetAsyncSocketChannel();
                    break;
                }
                case SELECT_RSP: 
                case DESELECT_RSP: 
                case LINKTEST_RSP: 
                case REJECT_REQ: {
                    asyncChannel.sendRejectRequest(msg, HsmsMessageRejectReason.TRANSACTION_NOT_OPEN);
                    break;
                }
                default: {
                    if (HsmsMessageType.supportSType(msg)) {
                        if (HsmsMessageType.supportPType(msg)) continue block8;
                        asyncChannel.sendRejectRequest(msg, HsmsMessageRejectReason.NOT_SUPPORT_TYPE_P);
                        break;
                    }
                    asyncChannel.sendRejectRequest(msg, HsmsMessageRejectReason.NOT_SUPPORT_TYPE_S);
                }
            }
        }
    }

    @Override
    public Optional<SecsMessage> send(int sessionId, int strm, int func, boolean wbit) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.getSession(sessionId).send(strm, func, wbit);
    }

    @Override
    public Optional<SecsMessage> send(int sessionId, int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.getSession(sessionId).send(strm, func, wbit, secs2);
    }

    @Override
    public Optional<SecsMessage> send(int sessionId, SecsMessage primaryMsg, int strm, int func, boolean wbit) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.getSession(sessionId).send(primaryMsg, strm, func, wbit);
    }

    @Override
    public Optional<SecsMessage> send(int sessionId, SecsMessage primaryMsg, int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.getSession(sessionId).send(primaryMsg, strm, func, wbit, secs2);
    }

    @Override
    public Optional<SecsMessage> send(int sessionId, SmlMessage sml) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.getSession(sessionId).send(sml);
    }

    public Optional<SecsMessage> send(int sessionId, SecsMessage primaryMsg, SmlMessage sml) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.getSession(sessionId).send(primaryMsg, sml);
    }

    @Override
    public boolean addSecsMessageReceiveListener(SecsMessageReceiveBiListener lstnr) {
        boolean r = true;
        for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
            if (s.addSecsMessageReceiveListener(lstnr)) continue;
            r = false;
        }
        return r;
    }

    @Override
    public boolean removeSecsMessageReceiveListener(SecsMessageReceiveBiListener lstnr) {
        boolean r = true;
        for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
            if (s.removeSecsMessageReceiveListener(lstnr)) continue;
            r = false;
        }
        return r;
    }

    @Override
    public boolean addSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeBiListener lstnr) {
        boolean r = true;
        for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
            if (s.addSecsCommunicatableStateChangeListener(lstnr)) continue;
            r = false;
        }
        return r;
    }

    @Override
    public boolean removeSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeBiListener lstnr) {
        boolean r = true;
        for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
            if (s.removeSecsCommunicatableStateChangeListener(lstnr)) continue;
            r = false;
        }
        return r;
    }

    protected AbstractHsmsAsyncSocketChannel buildAsyncSocketChannel(AsynchronousSocketChannel channel) {
        return new AbstractHsmsGsAsyncSocketChannel(channel, this){};
    }

    @Override
    public boolean addSecsLogListener(SecsLogListener lstnr) {
        return this.logListeners.add(lstnr);
    }

    @Override
    public boolean removeSecsLogListener(SecsLogListener lstnr) {
        return this.logListeners.remove(lstnr);
    }

    private void executeLogQueueTask() {
        this.executorService().execute(() -> {
            try {
                while (true) {
                    SecsLog log = this.logQueue.take();
                    this.logListeners.forEach(l -> l.received(log));
                }
            }
            catch (InterruptedException log) {
                try {
                    while ((log = (SecsLog)this.logQueue.poll(100L, TimeUnit.MILLISECONDS)) != null) {
                        this.logListeners.forEach(l -> l.received(log));
                    }
                }
                catch (InterruptedException interruptedException) {
                    // empty catch block
                }
                return;
            }
        });
    }

    public void notifyLog(AbstractSecsLog log) throws InterruptedException {
        log.subjectHeader(this.config.logSubjectHeader().get());
        this.logQueue.put(log);
        AbstractSecsMessage msg = log.optionalAbstractSecsMessage().orElse(null);
        if (msg != null) {
            for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
                if (s.sessionId() != msg.sessionId()) continue;
                s.notifyLog(log);
            }
        }
    }

    public void notifyLog(Throwable t) throws InterruptedException {
        this.notifyLog(new AbstractSecsThrowableLog(t){
            private static final long serialVersionUID = -6535535244608063298L;
        });
    }

    @Override
    public boolean addTrySendHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.trySendHsmsMsgPassThroughLstnrs.add(lstnr);
    }

    @Override
    public boolean removeTrySendHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.trySendHsmsMsgPassThroughLstnrs.remove(lstnr);
    }

    private void executeTrySendHsmsMsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            HsmsMessage msg = this.trySendHsmsMsgPassThroughQueue.take();
            this.trySendHsmsMsgPassThroughLstnrs.forEach(l -> l.passThrough(msg));
        });
    }

    public void notifyTrySendHsmsMessagePassThrough(HsmsMessage msg) throws InterruptedException {
        this.trySendHsmsMsgPassThroughQueue.put(msg);
        for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
            if (msg.sessionId() != s.sessionId()) continue;
            s.notifyTrySendHsmsMessagePassThrough(msg);
            s.notifyTrySendMessagePassThrough(msg);
        }
    }

    @Override
    public boolean addSendedHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.sendedHsmsMsgPassThroughLstnrs.add(lstnr);
    }

    @Override
    public boolean removeSendedHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.sendedHsmsMsgPassThroughLstnrs.remove(lstnr);
    }

    private void executeSendedHsmsMsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            HsmsMessage msg = this.sendedHsmsMsgPassThroughQueue.take();
            this.sendedHsmsMsgPassThroughLstnrs.forEach(l -> l.passThrough(msg));
        });
    }

    public void notifySendedHsmsMessagePassThrough(HsmsMessage msg) throws InterruptedException {
        this.sendedHsmsMsgPassThroughQueue.put(msg);
        for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
            if (msg.sessionId() != s.sessionId()) continue;
            s.notifySendedHsmsMessagePassThrough(msg);
            s.notifySendedMessagePassThrough(msg);
        }
    }

    @Override
    public boolean addReceiveHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.recvHsmsMsgPassThroughLstnrs.add(lstnr);
    }

    @Override
    public boolean removeReceiveHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.recvHsmsMsgPassThroughLstnrs.remove(lstnr);
    }

    private void executeRecvHsmsMsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            HsmsMessage msg = this.recvHsmsMsgPassThroughQueue.take();
            this.recvHsmsMsgPassThroughLstnrs.forEach(l -> l.passThrough(msg));
        });
    }

    public void notifyReceiveHsmsMessagePassThrough(HsmsMessage msg) throws InterruptedException {
        this.recvHsmsMsgPassThroughQueue.put(msg);
        for (AbstractHsmsSession s : this.getAbstractHsmsSessions()) {
            if (msg.sessionId() != s.sessionId()) continue;
            s.notifyReceiveHsmsMessagePassThrough(msg);
            s.notifyReceiveMessagePassThrough(msg);
        }
    }
}

