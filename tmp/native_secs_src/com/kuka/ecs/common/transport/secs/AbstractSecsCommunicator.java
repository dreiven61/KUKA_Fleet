/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractBaseCommunicator;
import com.kuka.ecs.common.transport.secs.AbstractSecsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import com.kuka.ecs.common.transport.secs.AbstractSecsMessage;
import com.kuka.ecs.common.transport.secs.AbstractSecsThrowableLog;
import com.kuka.ecs.common.transport.secs.BooleanProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.SecsCommunicatableStateChangeBiListener;
import com.kuka.ecs.common.transport.secs.SecsCommunicatableStateChangeListener;
import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsLogListener;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsMessagePassThroughListener;
import com.kuka.ecs.common.transport.secs.SecsMessageReceiveBiListener;
import com.kuka.ecs.common.transport.secs.SecsMessageReceiveListener;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.gem.Gem;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.sml.SmlMessage;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractSecsCommunicator
extends AbstractBaseCommunicator
implements SecsCommunicator {
    private final BooleanProperty communicatable = BooleanProperty.newInstance(false);
    private final AbstractSecsCommunicatorConfig config;
    private final Gem gem;
    private final Object syncOpen = new Object();
    private final Collection<SecsMessageReceiveListener> msgRecvListeners = new CopyOnWriteArrayList<SecsMessageReceiveListener>();
    private final Collection<SecsMessageReceiveBiListener> msgRecvBiListeners = new CopyOnWriteArrayList<SecsMessageReceiveBiListener>();
    private final BlockingQueue<SecsMessage> msgRecvQueue = new LinkedBlockingQueue<SecsMessage>();
    private final Collection<SecsLogListener> logListeners = new CopyOnWriteArrayList<SecsLogListener>();
    private final BlockingQueue<AbstractSecsLog> logQueue = new LinkedBlockingQueue<AbstractSecsLog>();
    private final Map<SecsCommunicatableStateChangeBiListener, SecsCommunicatableStateChangeListener> biCommStateMap = new HashMap<SecsCommunicatableStateChangeBiListener, SecsCommunicatableStateChangeListener>();
    private final Collection<SecsMessagePassThroughListener> trySendMsgPassThroughListeners = new CopyOnWriteArrayList<SecsMessagePassThroughListener>();
    private BlockingQueue<SecsMessage> trySendMsgPassThroughQueue = new LinkedBlockingQueue<SecsMessage>();
    private final Collection<SecsMessagePassThroughListener> sendedMsgPassThroughListeners = new CopyOnWriteArrayList<SecsMessagePassThroughListener>();
    private final BlockingQueue<SecsMessage> sendedMsgPassThroughQueue = new LinkedBlockingQueue<SecsMessage>();
    private final Collection<SecsMessagePassThroughListener> recvMsgPassThroughListeners = new CopyOnWriteArrayList<SecsMessagePassThroughListener>();
    private final BlockingQueue<SecsMessage> recvMsgPassThroughQueue = new LinkedBlockingQueue<SecsMessage>();

    public AbstractSecsCommunicator(AbstractSecsCommunicatorConfig config) {
        this.config = config;
        this.gem = Gem.newInstance(this, config.gem());
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void open() throws IOException {
        Object object = this.syncOpen;
        synchronized (object) {
            super.open();
        }
        this.executeLogQueueTask();
        this.executeMsgRecvQueueTask();
        this.executeTrySendMsgPassThroughQueueTask();
        this.executeSendedMsgPassThroughQueueTask();
        this.executeRecvMsgPassThroughQueueTask();
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    @Override
    public Gem gem() {
        return this.gem;
    }

    @Override
    public boolean isEquip() {
        return this.config.isEquip().booleanValue();
    }

    @Override
    public boolean isCommunicatable() {
        return this.communicatable.booleanValue();
    }

    @Override
    public void waitUntilCommunicatable() throws InterruptedException {
        this.communicatable.waitUntilTrue();
    }

    @Override
    public void waitUntilCommunicatable(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        this.communicatable.waitUntilTrue(timeout, unit);
    }

    @Override
    public void waitUntilCommunicatable(ReadOnlyTimeProperty tp) throws InterruptedException, TimeoutException {
        this.communicatable.waitUntilTrue(tp);
    }

    @Override
    public void waitUntilNotCommunicatable() throws InterruptedException {
        this.communicatable.waitUntilFalse();
    }

    @Override
    public void waitUntilNotCommunicatable(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        this.communicatable.waitUntilFalse(timeout, unit);
    }

    @Override
    public void waitUntilNotCommunicatable(ReadOnlyTimeProperty tp) throws InterruptedException, TimeoutException {
        this.communicatable.waitUntilFalse(tp);
    }

    @Override
    public void openAndWaitUntilCommunicatable() throws IOException, InterruptedException {
        this.openIfNotOpen();
        this.waitUntilCommunicatable();
    }

    @Override
    public void openAndWaitUntilCommunicatable(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
        this.openIfNotOpen();
        this.waitUntilCommunicatable(timeout, unit);
    }

    @Override
    public void openAndWaitUntilCommunicatable(ReadOnlyTimeProperty tp) throws IOException, InterruptedException, TimeoutException {
        this.openIfNotOpen();
        this.waitUntilCommunicatable(tp);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void openIfNotOpen() throws IOException {
        Object object = this.syncOpen;
        synchronized (object) {
            if (!this.isOpen()) {
                this.open();
            }
        }
    }

    public abstract Optional<SecsMessage> templateSend(int var1, int var2, boolean var3, Secs2 var4) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public abstract Optional<SecsMessage> templateSend(SecsMessage var1, int var2, int var3, boolean var4, Secs2 var5) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    @Override
    public Optional<SecsMessage> send(int strm, int func, boolean wbit) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.templateSend(strm, func, wbit, Secs2.empty());
    }

    @Override
    public Optional<SecsMessage> send(int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.templateSend(strm, func, wbit, secs2);
    }

    @Override
    public Optional<SecsMessage> send(SecsMessage primaryMsg, int strm, int func, boolean wbit) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.templateSend(primaryMsg, strm, func, wbit, Secs2.empty());
    }

    @Override
    public Optional<SecsMessage> send(SecsMessage primaryMsg, int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.templateSend(primaryMsg, strm, func, wbit, secs2);
    }

    @Override
    public Optional<SecsMessage> send(SmlMessage sml) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.templateSend(sml.getStream(), sml.getFunction(), sml.wbit(), sml.secs2());
    }

    @Override
    public Optional<SecsMessage> send(SecsMessage primaryMsg, SmlMessage sml) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.templateSend(primaryMsg, sml.getStream(), sml.getFunction(), sml.wbit(), sml.secs2());
    }

    @Override
    public boolean addSecsMessageReceiveListener(SecsMessageReceiveListener l) {
        return this.msgRecvListeners.add(Objects.requireNonNull(l));
    }

    @Override
    public boolean removeSecsMessageReceiveListener(SecsMessageReceiveListener l) {
        return this.msgRecvListeners.remove(Objects.requireNonNull(l));
    }

    @Override
    public boolean addSecsMessageReceiveListener(SecsMessageReceiveBiListener l) {
        return this.msgRecvBiListeners.add(Objects.requireNonNull(l));
    }

    @Override
    public boolean removeSecsMessageReceiveListener(SecsMessageReceiveBiListener l) {
        return this.msgRecvBiListeners.remove(Objects.requireNonNull(l));
    }

    private void executeMsgRecvQueueTask() {
        this.executeLoopTask(() -> {
            SecsMessage msg = this.msgRecvQueue.take();
            this.msgRecvListeners.forEach(l -> l.received(msg));
            this.msgRecvBiListeners.forEach(l -> l.received(msg, this));
        });
    }

    public void notifyReceiveMessage(AbstractSecsMessage msg) throws InterruptedException {
        this.msgRecvQueue.put(msg);
    }

    @Override
    public boolean addSecsLogListener(SecsLogListener l) {
        return this.logListeners.add(Objects.requireNonNull(l));
    }

    @Override
    public boolean removeSecsLogListener(SecsLogListener l) {
        return this.logListeners.remove(Objects.requireNonNull(l));
    }

    private void executeLogQueueTask() {
        this.executorService().execute(() -> {
            try {
                while (true) {
                    AbstractSecsLog log = this.logQueue.take();
                    this.logListeners.forEach(l -> l.received(log));
                }
            }
            catch (InterruptedException log) {
                try {
                    while ((log = this.logQueue.poll(100L, TimeUnit.MILLISECONDS)) != null) {
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
    }

    public void notifyLog(Throwable t) throws InterruptedException {
        this.notifyLog(new AbstractSecsThrowableLog(t){
            private static final long serialVersionUID = -1271705310309086030L;
        });
    }

    @Override
    public boolean addSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeListener l) {
        return this.communicatable.addChangeListener(l::changed);
    }

    @Override
    public boolean removeSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeListener l) {
        return this.communicatable.removeChangeListener(l::changed);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean addSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeBiListener l) {
        SecsCommunicatableStateChangeListener x = f -> l.changed(f, this);
        Map<SecsCommunicatableStateChangeBiListener, SecsCommunicatableStateChangeListener> map = this.biCommStateMap;
        synchronized (map) {
            this.biCommStateMap.put(l, x);
            return this.communicatable.addChangeListener(x::changed);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public boolean removeSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeBiListener l) {
        Map<SecsCommunicatableStateChangeBiListener, SecsCommunicatableStateChangeListener> map = this.biCommStateMap;
        synchronized (map) {
            SecsCommunicatableStateChangeListener x = this.biCommStateMap.remove(l);
            if (x != null) {
                return this.communicatable.removeChangeListener(x::changed);
            }
            return false;
        }
    }

    public void notifyCommunicatableStateChange(boolean f) {
        this.communicatable.set(f);
    }

    @Override
    public boolean addTrySendMessagePassThroughListener(SecsMessagePassThroughListener l) {
        return this.trySendMsgPassThroughListeners.add(Objects.requireNonNull(l));
    }

    @Override
    public boolean removeTrySendMessagePassThroughListener(SecsMessagePassThroughListener l) {
        return this.trySendMsgPassThroughListeners.remove(Objects.requireNonNull(l));
    }

    private void executeTrySendMsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            SecsMessage msg = this.trySendMsgPassThroughQueue.take();
            this.trySendMsgPassThroughListeners.forEach(l -> l.passThrough(msg));
        });
    }

    public void notifyTrySendMessagePassThrough(SecsMessage msg) throws InterruptedException {
        this.trySendMsgPassThroughQueue.put(msg);
    }

    @Override
    public boolean addSendedMessagePassThroughListener(SecsMessagePassThroughListener l) {
        return this.sendedMsgPassThroughListeners.add(Objects.requireNonNull(l));
    }

    @Override
    public boolean removeSendedMessagePassThroughListener(SecsMessagePassThroughListener l) {
        return this.sendedMsgPassThroughListeners.remove(Objects.requireNonNull(l));
    }

    private void executeSendedMsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            SecsMessage msg = this.sendedMsgPassThroughQueue.take();
            this.sendedMsgPassThroughListeners.forEach(l -> l.passThrough(msg));
        });
    }

    public void notifySendedMessagePassThrough(SecsMessage msg) throws InterruptedException {
        this.sendedMsgPassThroughQueue.put(msg);
    }

    @Override
    public boolean addReceiveMessagePassThroughListener(SecsMessagePassThroughListener l) {
        return this.recvMsgPassThroughListeners.add(Objects.requireNonNull(l));
    }

    @Override
    public boolean removeReceiveMessagePassThroughListener(SecsMessagePassThroughListener l) {
        return this.recvMsgPassThroughListeners.remove(Objects.requireNonNull(l));
    }

    private void executeRecvMsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            SecsMessage msg = this.recvMsgPassThroughQueue.take();
            this.recvMsgPassThroughListeners.forEach(l -> l.passThrough(msg));
        });
    }

    public void notifyReceiveMessagePassThrough(SecsMessage msg) throws InterruptedException {
        this.recvMsgPassThroughQueue.put(msg);
    }
}

