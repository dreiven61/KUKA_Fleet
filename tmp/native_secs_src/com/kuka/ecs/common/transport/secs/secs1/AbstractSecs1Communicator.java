/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.AbstractSecsCommunicator;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Circuit;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1CommunicatorConfig;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1MessageBuilder;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Communicator;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Exception;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBuilder;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessagePassThroughListener;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendByteException;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendMessageException;
import com.kuka.ecs.common.transport.secs.secs1.Secs1WaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class AbstractSecs1Communicator
extends AbstractSecsCommunicator
implements Secs1Communicator {
    private final AbstractSecs1CommunicatorConfig config;
    private final Secs1MessageBuilder msgBuilder;
    private final AbstractSecs1Circuit circuit;
    private final Collection<Secs1MessagePassThroughListener> trySendSecs1MsgPassThroughLstnrs = new CopyOnWriteArrayList<Secs1MessagePassThroughListener>();
    private final BlockingQueue<Secs1Message> trySendSecs1MsgPassThroughQueue = new LinkedBlockingQueue<Secs1Message>();
    private final Collection<Secs1MessagePassThroughListener> sendedSecs1MsgPassThroughLstnrs = new CopyOnWriteArrayList<Secs1MessagePassThroughListener>();
    private final BlockingQueue<Secs1Message> sendedSecs1MsgPassThroughQueue = new LinkedBlockingQueue<Secs1Message>();
    private final Collection<Secs1MessagePassThroughListener> recvSecs1MsgPassThroughLstnrs = new CopyOnWriteArrayList<Secs1MessagePassThroughListener>();
    private final BlockingQueue<Secs1Message> recvSecs1MsgPassThroughQueue = new LinkedBlockingQueue<Secs1Message>();

    public AbstractSecs1Communicator(AbstractSecs1CommunicatorConfig config) {
        super(config);
        this.config = config;
        this.msgBuilder = new AbstractSecs1MessageBuilder(this){};
        this.circuit = new AbstractSecs1Circuit(this){};
    }

    public AbstractSecs1CommunicatorConfig config() {
        return this.config;
    }

    public Secs1MessageBuilder messageBuilder() {
        return this.msgBuilder;
    }

    @Override
    public int deviceId() {
        return this.config.deviceId().intValue();
    }

    @Override
    public int sessionId() {
        return -1;
    }

    @Override
    public void open() throws IOException {
        super.open();
        this.executeTrySendSecs1MsgPassThroughQueueTask();
        this.executeSendedSecs1MsgPassThroughQueueTask();
        this.executeRecvSecs1MsgPassThroughQueueTask();
        this.executorService().execute(this.circuit);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    @Override
    public Optional<SecsMessage> templateSend(int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.circuit.send(this.messageBuilder().build(strm, func, wbit, secs2)).map(m -> m);
    }

    @Override
    public Optional<SecsMessage> templateSend(SecsMessage primaryMsg, int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.circuit.send(this.messageBuilder().build(primaryMsg, strm, func, wbit, secs2)).map(m -> m);
    }

    @Override
    public Optional<Secs1Message> send(Secs1Message msg) throws Secs1SendMessageException, Secs1WaitReplyMessageException, Secs1Exception, InterruptedException {
        return this.circuit.send(Secs1MessageBuilder.fromMessage(msg));
    }

    protected void putByte(byte b) throws InterruptedException {
        this.circuit.putByte(b);
    }

    protected void putBytes(byte[] bs) throws InterruptedException {
        this.circuit.putBytes(bs);
    }

    public abstract void sendBytes(byte[] var1) throws Secs1SendByteException, InterruptedException;

    @Override
    public boolean addTrySendSecs1MessagePassThroughListener(Secs1MessagePassThroughListener lstnr) {
        return this.trySendSecs1MsgPassThroughLstnrs.add(lstnr);
    }

    @Override
    public boolean removeTrySendSecs1MessagePassThroughListener(Secs1MessagePassThroughListener lstnr) {
        return this.trySendSecs1MsgPassThroughLstnrs.remove(lstnr);
    }

    public void notifyTrySendSecs1MessagePassThrough(Secs1Message msg) throws InterruptedException {
        this.trySendSecs1MsgPassThroughQueue.put(msg);
    }

    private void executeTrySendSecs1MsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            Secs1Message msg = this.trySendSecs1MsgPassThroughQueue.take();
            this.trySendSecs1MsgPassThroughLstnrs.forEach(l -> l.passThrough(msg));
        });
    }

    @Override
    public boolean addSendedSecs1MessagePassThroughListener(Secs1MessagePassThroughListener lstnr) {
        return this.sendedSecs1MsgPassThroughLstnrs.add(lstnr);
    }

    @Override
    public boolean removeSendedSecs1MessagePassThroughListener(Secs1MessagePassThroughListener lstnr) {
        return this.sendedSecs1MsgPassThroughLstnrs.remove(lstnr);
    }

    public void notifySendedSecs1MessagePassThrough(Secs1Message msg) throws InterruptedException {
        this.sendedSecs1MsgPassThroughQueue.put(msg);
    }

    private void executeSendedSecs1MsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            Secs1Message msg = this.sendedSecs1MsgPassThroughQueue.take();
            this.sendedSecs1MsgPassThroughLstnrs.forEach(l -> l.passThrough(msg));
        });
    }

    @Override
    public boolean addReceiveSecs1MessagePassThroughListener(Secs1MessagePassThroughListener lstnr) {
        return this.recvSecs1MsgPassThroughLstnrs.add(lstnr);
    }

    @Override
    public boolean removeReceiveSecs1MessagePassThroughListener(Secs1MessagePassThroughListener lstnr) {
        return this.recvSecs1MsgPassThroughLstnrs.add(lstnr);
    }

    public void notifyReceiveSecs1MessagePassThrough(Secs1Message msg) throws InterruptedException {
        this.recvSecs1MsgPassThroughQueue.put(msg);
    }

    private void executeRecvSecs1MsgPassThroughQueueTask() {
        this.executeLoopTask(() -> {
            Secs1Message msg = this.recvSecs1MsgPassThroughQueue.take();
            this.recvSecs1MsgPassThroughLstnrs.forEach(l -> l.passThrough(msg));
        });
    }
}

