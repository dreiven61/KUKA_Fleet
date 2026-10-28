/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.AbstractSecsCommunicator;
import com.kuka.ecs.common.transport.secs.AbstractSecsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.Property;
import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicateState;
import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicateStateChangeListener;
import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicator;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessagePassThroughListener;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class AbstractHsmsCommunicator
extends AbstractSecsCommunicator
implements HsmsCommunicator {
    private final Property<HsmsCommunicateState> hsmsCommState = Property.newInstance(HsmsCommunicateState.NOT_CONNECTED);
    private final Collection<HsmsMessagePassThroughListener> trySendHsmsMsgPassThroughLstnrs = new CopyOnWriteArrayList<HsmsMessagePassThroughListener>();
    private final BlockingQueue<HsmsMessage> trySendHsmsMsgQueue = new LinkedBlockingQueue<HsmsMessage>();
    private final Collection<HsmsMessagePassThroughListener> sendedHsmsMsgPassThroughLstnrs = new CopyOnWriteArrayList<HsmsMessagePassThroughListener>();
    private final BlockingQueue<HsmsMessage> sendedHsmsMsgQueue = new LinkedBlockingQueue<HsmsMessage>();
    private final Collection<HsmsMessagePassThroughListener> recvHsmsMsgPassThroughLstnrs = new CopyOnWriteArrayList<HsmsMessagePassThroughListener>();
    private final BlockingQueue<HsmsMessage> recvHsmsMsgQueue = new LinkedBlockingQueue<HsmsMessage>();

    public AbstractHsmsCommunicator(AbstractSecsCommunicatorConfig config) {
        super(config);
        this.hsmsCommState.addChangeListener(state -> this.notifyCommunicatableStateChange(state == HsmsCommunicateState.SELECTED));
    }

    @Override
    public void open() throws IOException {
        super.open();
        this.executeTrySendHsmsMsgPassThroughQueueTask();
        this.executeSendedHsmsMsgPassThroughQueueTask();
        this.executeRecvHsmsMsgPassThroughQueueTask();
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    @Override
    public boolean addHsmsCommunicateStateChangeListener(HsmsCommunicateStateChangeListener listener) {
        return this.hsmsCommState.addChangeListener(listener::changed);
    }

    @Override
    public boolean removeHsmsCommunicateStateChangeListener(HsmsCommunicateStateChangeListener listener) {
        return this.hsmsCommState.removeChangeListener(listener::changed);
    }

    public void notifyHsmsCommunicateState(HsmsCommunicateState state) {
        this.hsmsCommState.set(state);
    }

    @Override
    public boolean addTrySendHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.trySendHsmsMsgPassThroughLstnrs.add(lstnr);
    }

    @Override
    public boolean removeTrySendHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.trySendHsmsMsgPassThroughLstnrs.remove(lstnr);
    }

    public void notifyTrySendHsmsMessagePassThrough(HsmsMessage msg) throws InterruptedException {
        this.trySendHsmsMsgQueue.put(msg);
    }

    private void executeTrySendHsmsMsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            HsmsMessage msg = this.trySendHsmsMsgQueue.take();
            this.trySendHsmsMsgPassThroughLstnrs.forEach(l -> l.passThrough(msg));
        });
    }

    @Override
    public boolean addSendedHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.sendedHsmsMsgPassThroughLstnrs.add(lstnr);
    }

    @Override
    public boolean removeSendedHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.sendedHsmsMsgPassThroughLstnrs.remove(lstnr);
    }

    public void notifySendedHsmsMessagePassThrough(HsmsMessage msg) throws InterruptedException {
        this.sendedHsmsMsgQueue.put(msg);
    }

    private void executeSendedHsmsMsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            HsmsMessage msg = this.sendedHsmsMsgQueue.take();
            this.sendedHsmsMsgPassThroughLstnrs.forEach(l -> l.passThrough(msg));
        });
    }

    @Override
    public boolean addReceiveHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.recvHsmsMsgPassThroughLstnrs.add(lstnr);
    }

    @Override
    public boolean removeReceiveHsmsMessagePassThroughListener(HsmsMessagePassThroughListener lstnr) {
        return this.recvHsmsMsgPassThroughLstnrs.remove(lstnr);
    }

    public void notifyReceiveHsmsMessagePassThrough(HsmsMessage msg) throws InterruptedException {
        this.recvHsmsMsgQueue.put(msg);
    }

    private void executeRecvHsmsMsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            HsmsMessage msg = this.recvHsmsMsgQueue.take();
            this.recvHsmsMsgPassThroughLstnrs.forEach(l -> l.passThrough(msg));
        });
    }
}

