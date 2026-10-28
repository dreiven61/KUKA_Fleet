/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Communicator;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Message;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1MessageBlock;
import com.kuka.ecs.common.transport.secs.secs1.ByteAndSecs1MessageQueue;
import com.kuka.ecs.common.transport.secs.secs1.ByteOrSecs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Exception;
import com.kuka.ecs.common.transport.secs.secs1.Secs1IllegalLengthByteCircuitControlLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBlockPack;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBuilder;
import com.kuka.ecs.common.transport.secs.secs1.Secs1NotReceiveAckCircuitControlLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1NotReceiveNextBlockEnqCircuitControlLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1ReceiveMessageBlockLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1ReceiveMessageLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1RetryCircuitControlLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1RetryOverException;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendByteException;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendMessageException;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendMessageManager;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendedMessageBlockLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendedMessageLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SumCheckMismatchCirsuitControlLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1TimeoutT1CircuitControlLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1TimeoutT2AckCircuitControlLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1TimeoutT2LengthByteCircuitColtrolLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1TimeoutT3Exception;
import com.kuka.ecs.common.transport.secs.secs1.Secs1TimeoutT4CircuitControlLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1TransactionManager;
import com.kuka.ecs.common.transport.secs.secs1.Secs1TrySendMessageBlockLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1TrySendMessageLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1WaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import java.util.LinkedList;
import java.util.Optional;

public abstract class AbstractSecs1Circuit
implements Runnable {
    protected static final byte ENQ = 5;
    protected static final byte EOT = 4;
    protected static final byte ACK = 6;
    protected static final byte NAK = 21;
    private final ByteAndSecs1MessageQueue queue = new ByteAndSecs1MessageQueue();
    private final Secs1SendMessageManager sendMgr = new Secs1SendMessageManager();
    private final Secs1TransactionManager<AbstractSecs1Message, AbstractSecs1MessageBlock> transMgr = new Secs1TransactionManager();
    private final AbstractSecs1Communicator comm;
    private final LinkedList<AbstractSecs1MessageBlock> cacheBlocks = new LinkedList();

    public AbstractSecs1Circuit(AbstractSecs1Communicator communicator) {
        this.comm = communicator;
    }

    @Override
    public void run() {
        try {
            while (true) {
                this.enter();
            }
        }
        catch (InterruptedException interruptedException) {
            return;
        }
    }

    public void putByte(byte b) throws InterruptedException {
        this.queue.putByte(b);
    }

    public void putBytes(byte[] bs) throws InterruptedException {
        this.queue.putBytes(bs);
    }

    private void sendBytes(byte[] bs) throws Secs1SendByteException, InterruptedException {
        this.comm.sendBytes(bs);
    }

    private void sendByte(byte b) throws Secs1SendByteException, InterruptedException {
        this.sendBytes(new byte[]{b});
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Optional<Secs1Message> send(AbstractSecs1Message msg) throws Secs1SendMessageException, Secs1WaitReplyMessageException, Secs1Exception, InterruptedException {
        try {
            this.sendMgr.enter(msg);
            this.comm.notifyTrySendMessagePassThrough(msg);
            this.comm.notifyTrySendSecs1MessagePassThrough(msg);
            this.comm.notifyLog(new Secs1TrySendMessageLog(msg));
            if (msg.wbit()) {
                try {
                    this.transMgr.enter(msg);
                    this.queue.putSecs1Message(msg);
                    this.sendMgr.waitUntilSended(msg);
                    AbstractSecs1Message r = this.transMgr.waitReply(msg, this.comm.config().timeout().t3());
                    if (r == null) {
                        throw new Secs1TimeoutT3Exception(msg);
                    }
                    Optional<Secs1Message> optional = Optional.of(r);
                    return optional;
                }
                finally {
                    this.transMgr.exit(msg);
                }
            }
            this.queue.putSecs1Message(msg);
        }
        catch (Secs1SendMessageException e) {
            this.comm.notifyLog(e);
            throw e;
        }
        catch (Secs1WaitReplyMessageException e) {
            this.comm.notifyLog(e);
            throw e;
        }
        finally {
            this.sendMgr.exit(msg);
        }
        this.sendMgr.waitUntilSended(msg);
        Optional<Secs1Message> r = Optional.empty();
        return r;
    }

    private void enter() throws InterruptedException {
        ByteOrSecs1Message v = this.queue.takeByteOrSecs1Message();
        Secs1MessageBlockPack pack = v.message();
        if (pack == null) {
            if (v.isENQ()) {
                try {
                    this.receiving();
                }
                catch (Secs1Exception e) {
                    this.comm.notifyLog(e);
                }
            }
        } else {
            try {
                int retry = 0;
                block6: while (retry <= this.comm.config().retry().intValue()) {
                    Byte b;
                    this.sendByte((byte)5);
                    do {
                        if ((b = this.queue.pollByte(this.comm.config().timeout().t2())) == null) {
                            this.comm.notifyLog(Secs1RetryCircuitControlLog.newInstance(retry));
                            ++retry;
                            continue block6;
                        }
                        if (b != 5 || this.comm.config().isMaster().booleanValue()) continue;
                        try {
                            this.receiving();
                        }
                        catch (SecsException e) {
                            this.comm.notifyLog(e);
                        }
                        retry = 0;
                        pack.reset();
                        continue block6;
                    } while (b != 4);
                    if (this.sending(pack.present())) {
                        if (pack.ebit()) {
                            this.sendMgr.putSended(pack.message());
                            this.comm.notifySendedMessagePassThrough(pack.message());
                            this.comm.notifySendedSecs1MessagePassThrough(pack.message());
                            this.comm.notifyLog(new Secs1SendedMessageLog(pack.message()));
                            return;
                        }
                        pack.next();
                        retry = 0;
                        continue;
                    }
                    this.comm.notifyLog(Secs1RetryCircuitControlLog.newInstance(retry));
                    ++retry;
                }
                this.sendMgr.putException(pack.message(), new Secs1RetryOverException());
            }
            catch (Secs1Exception e) {
                this.sendMgr.putException(pack.message(), e);
                this.comm.notifyLog(e);
            }
        }
    }

    private boolean sending(AbstractSecs1MessageBlock block) throws Secs1Exception, InterruptedException {
        this.comm.notifyLog(new Secs1TrySendMessageBlockLog(block));
        this.sendBytes(block.getBytes());
        Byte b = this.queue.pollByte(this.comm.config().timeout().t2());
        if (b == null) {
            this.comm.notifyLog(Secs1TimeoutT2AckCircuitControlLog.newInstance(block));
            return false;
        }
        if (b == 6) {
            this.comm.notifyLog(new Secs1SendedMessageBlockLog(block));
            return true;
        }
        this.comm.notifyLog(Secs1NotReceiveAckCircuitControlLog.newInstance(block, b));
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void receiving() throws Secs1Exception, InterruptedException {
        int r;
        this.sendByte((byte)4);
        byte[] bs = new byte[257];
        int r2 = this.queue.pollBytes(bs, 0, 1, this.comm.config().timeout().t2());
        if (r2 <= 0) {
            this.sendByte((byte)21);
            this.comm.notifyLog(Secs1TimeoutT2LengthByteCircuitColtrolLog.newInstance());
            return;
        }
        int len = bs[0] & 0xFF;
        if (len < 10 || len > 254) {
            this.queue.garbageBytes(this.comm.config().timeout().t1());
            this.sendByte((byte)21);
            this.comm.notifyLog(Secs1IllegalLengthByteCircuitControlLog.newInstance(len));
            return;
        }
        int m = len + 3;
        for (int pos = 1; pos < m; pos += r) {
            r = this.queue.pollBytes(bs, pos, m, this.comm.config().timeout().t1());
            if (r > 0) continue;
            this.sendByte((byte)21);
            this.comm.notifyLog(Secs1TimeoutT1CircuitControlLog.newInstance(pos));
            return;
        }
        AbstractSecs1MessageBlock block = new AbstractSecs1MessageBlock(bs){
            private static final long serialVersionUID = -1187993676063154279L;
        };
        if (!block.checkSum()) {
            this.queue.garbageBytes(this.comm.config().timeout().t1());
            this.sendByte((byte)21);
            this.comm.notifyLog(Secs1SumCheckMismatchCirsuitControlLog.newInstance());
            return;
        }
        this.sendByte((byte)6);
        this.comm.notifyLog(new Secs1ReceiveMessageBlockLog(block));
        if (this.comm.config().isCheckMessageBlockDeviceId().booleanValue() && block.deviceId() != this.comm.config().deviceId().intValue()) {
            return;
        }
        if (this.cacheBlocks.isEmpty()) {
            this.cacheBlocks.add(block);
        } else {
            AbstractSecs1MessageBlock prev = this.cacheBlocks.getLast();
            if (prev.equalsSystemBytes(block)) {
                if (prev.isNextBlock(block)) {
                    this.cacheBlocks.add(block);
                }
            } else {
                this.cacheBlocks.clear();
                this.cacheBlocks.add(block);
            }
        }
        if (block.ebit()) {
            try {
                AbstractSecs1Message s1msg = Secs1MessageBuilder.fromBlocks(this.cacheBlocks);
                AbstractSecs1Message m2 = this.transMgr.put(s1msg);
                if (m2 != null) {
                    this.comm.notifyReceiveMessage(m2);
                }
                this.comm.notifyReceiveMessagePassThrough(s1msg);
                this.comm.notifyReceiveSecs1MessagePassThrough(s1msg);
                this.comm.notifyLog(new Secs1ReceiveMessageLog(s1msg));
            }
            catch (Secs2Exception e) {
                this.comm.notifyLog(e);
            }
            finally {
                this.cacheBlocks.clear();
            }
        } else {
            this.transMgr.resetTimer(block);
            Byte b = this.queue.pollByte(this.comm.config().timeout().t4());
            if (b == null) {
                this.comm.notifyLog(Secs1TimeoutT4CircuitControlLog.newInstance(block));
            } else if (b == 5) {
                this.receiving();
            } else {
                this.comm.notifyLog(Secs1NotReceiveNextBlockEnqCircuitControlLog.newInstance(block, b));
            }
        }
    }
}

