/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsControlMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsms.HsmsControlMessageLengthBytesUpperThanTenException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsDetectTerminateException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageBuilder;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageDeselectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageLengthBytesLowerThanTenException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageReceiveListener;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageRejectReason;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageSelectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;
import com.kuka.ecs.common.transport.secs.hsms.HsmsNotExpectControlTypeReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsReceiveMessageLog;
import com.kuka.ecs.common.transport.secs.hsms.HsmsRejectException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendedMessageLog;
import com.kuka.ecs.common.transport.secs.hsms.HsmsTimeoutT3Exception;
import com.kuka.ecs.common.transport.secs.hsms.HsmsTimeoutT6Exception;
import com.kuka.ecs.common.transport.secs.hsms.HsmsTimeoutT8Exception;
import com.kuka.ecs.common.transport.secs.hsms.HsmsTooBigSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsTransactionManager;
import com.kuka.ecs.common.transport.secs.hsms.HsmsTrySendMessageLog;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BuildException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesPack;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesPackBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public abstract class AbstractHsmsAsyncSocketChannel
implements HsmsAsyncSocketChannel {
    private final AsynchronousSocketChannel channel;
    private static final byte[] length4Zero = new byte[]{0, 0, 0, 0};
    private static final long defaultReceiveBodySize = 1024L;
    private final Object syncShutdown = new Object();
    private final Collection<HsmsMessageReceiveListener> recvHsmsMsgLstnrs = new CopyOnWriteArrayList<HsmsMessageReceiveListener>();
    private static final int defaultSendBufferSize = 65536;
    private final Object syncSendMsg = new Object();

    public AbstractHsmsAsyncSocketChannel(AsynchronousSocketChannel channel) {
        this.channel = channel;
    }

    @Override
    public void reading() throws HsmsException, InterruptedException {
        try {
            ByteBuffer lengthBuffer = ByteBuffer.allocate(8);
            ByteBuffer headerBuffer = ByteBuffer.allocate(10);
            ArrayList<ByteBuffer> bodyBuffers = new ArrayList<ByteBuffer>();
            while (true) {
                ((Buffer)lengthBuffer).clear();
                lengthBuffer.put(length4Zero);
                this.readingBufferFirst(lengthBuffer);
                this.resetLinktestTimer();
                while (lengthBuffer.hasRemaining()) {
                    this.readingBuffer(lengthBuffer);
                }
                ((Buffer)lengthBuffer).flip();
                long msgLength = lengthBuffer.getLong();
                if (msgLength < 10L) {
                    throw new HsmsMessageLengthBytesLowerThanTenException(msgLength);
                }
                ((Buffer)headerBuffer).clear();
                while (headerBuffer.hasRemaining()) {
                    this.readingBuffer(headerBuffer);
                }
                bodyBuffers.clear();
                long rem = msgLength - 10L;
                while (rem > 0L) {
                    long bodySize = this.prototypeDefaultReceiveBodySize();
                    int size = (int)(rem > bodySize ? bodySize : rem);
                    ByteBuffer bf2 = ByteBuffer.allocate(size);
                    while (bf2.hasRemaining()) {
                        rem -= (long)this.readingBuffer(bf2);
                    }
                    bodyBuffers.add(bf2);
                    this.resetLinktestTimer();
                }
                ((Buffer)headerBuffer).flip();
                byte[] headerBytes = new byte[10];
                headerBuffer.get(headerBytes);
                List<byte[]> bodyBytes = bodyBuffers.stream().map(bf -> {
                    ((Buffer)bf).flip();
                    byte[] bs = new byte[bf.remaining()];
                    bf.get(bs);
                    return bs;
                }).collect(Collectors.toList());
                AbstractHsmsMessage msg = this.buildMessageFromBytes(headerBytes, bodyBytes);
                HsmsMessageType type = msg.messageType();
                if (!this.prototypeCheckControlMessageLength(type, msgLength)) {
                    throw new HsmsControlMessageLengthBytesUpperThanTenException(type, msgLength);
                }
                AbstractHsmsMessage r = this.transactionManager().put(msg);
                if (r != null) {
                    this.recvHsmsMsgLstnrs.forEach(l -> l.received(r));
                }
                this.notifyReceiveHsmsMessagePassThrough(msg);
                this.notifyLog(new HsmsReceiveMessageLog(msg));
            }
        }
        catch (Secs2BytesParseException e) {
            throw new HsmsException(e);
        }
        catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
            if (!(t instanceof ClosedChannelException)) {
                throw new HsmsException(t);
            }
            return;
        }
    }

    protected long prototypeDefaultReceiveBodySize() {
        return 1024L;
    }

    protected boolean prototypeCheckControlMessageLength(HsmsMessageType type, long length) {
        switch (type) {
            case SELECT_REQ: 
            case SELECT_RSP: 
            case DESELECT_REQ: 
            case DESELECT_RSP: 
            case LINKTEST_REQ: 
            case LINKTEST_RSP: 
            case REJECT_REQ: 
            case SEPARATE_REQ: {
                return length == 10L;
            }
        }
        return true;
    }

    private int readingBufferFirst(ByteBuffer buffer) throws HsmsDetectTerminateException, ExecutionException, InterruptedException {
        Future<Integer> f = this.channel.read(buffer);
        try {
            int r = f.get();
            if (r < 0) {
                throw new HsmsDetectTerminateException();
            }
            return r;
        }
        catch (InterruptedException e) {
            f.cancel(true);
            throw e;
        }
    }

    private int readingBuffer(ByteBuffer buffer) throws HsmsTimeoutT8Exception, HsmsDetectTerminateException, ExecutionException, InterruptedException {
        Future<Integer> f = this.channel.read(buffer);
        try {
            int r = this.timeoutT8().future(f);
            if (r < 0) {
                throw new HsmsDetectTerminateException();
            }
            return r;
        }
        catch (TimeoutException e) {
            f.cancel(true);
            throw new HsmsTimeoutT8Exception(e);
        }
        catch (InterruptedException e) {
            f.cancel(true);
            throw e;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void shutdown() {
        Object object = this.syncShutdown;
        synchronized (object) {
            this.syncShutdown.notifyAll();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void waitingUntilShutdown() throws InterruptedException {
        Object object = this.syncShutdown;
        synchronized (object) {
            this.syncShutdown.wait();
        }
    }

    protected abstract void notifyLog(AbstractSecsLog var1) throws InterruptedException;

    protected abstract void notifyTrySendHsmsMsgPassThrough(HsmsMessage var1) throws InterruptedException;

    protected abstract void notifySendedHsmsMsgPassThrough(HsmsMessage var1) throws InterruptedException;

    protected abstract void notifyReceiveHsmsMessagePassThrough(HsmsMessage var1) throws InterruptedException;

    @Override
    public boolean addHsmsMessageReceiveListener(HsmsMessageReceiveListener l) {
        return this.recvHsmsMsgLstnrs.add(l);
    }

    @Override
    public boolean removeHsmsMessageReceiveListener(HsmsMessageReceiveListener l) {
        return this.recvHsmsMsgLstnrs.remove(l);
    }

    protected abstract HsmsMessageBuilder messageBuilder();

    protected abstract AbstractHsmsMessage buildMessageFromBytes(byte[] var1, List<byte[]> var2) throws Secs2BytesParseException;

    protected abstract AbstractHsmsMessage buildMessageFromMessage(HsmsMessage var1);

    protected abstract HsmsTransactionManager<AbstractHsmsMessage> transactionManager();

    protected abstract ReadOnlyTimeProperty timeoutT3();

    protected abstract ReadOnlyTimeProperty timeoutT6();

    protected abstract ReadOnlyTimeProperty timeoutT8();

    protected abstract void resetLinktestTimer();

    @Override
    public Optional<HsmsMessage> sendSelectRequest(AbstractHsmsSession session) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        AbstractHsmsControlMessage msg = this.messageBuilder().buildSelectRequest(session);
        Optional<HsmsMessage> r = this.sendAndReplyHsmsMessage(msg);
        HsmsMessageType type = r.get().messageType();
        if (type == HsmsMessageType.REJECT_REQ) {
            throw new HsmsRejectException(msg);
        }
        if (type != HsmsMessageType.SELECT_RSP) {
            throw new HsmsNotExpectControlTypeReplyMessageException(msg);
        }
        return r;
    }

    @Override
    public Optional<HsmsMessage> sendSelectResponse(HsmsMessage primaryMsg, HsmsMessageSelectStatus status) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.sendAndReplyHsmsMessage(this.messageBuilder().buildSelectResponse(primaryMsg, status));
    }

    @Override
    public Optional<HsmsMessage> sendDeselectRequest(AbstractHsmsSession session) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        AbstractHsmsControlMessage msg = this.messageBuilder().buildDeselectRequest(session);
        Optional<HsmsMessage> r = this.sendAndReplyHsmsMessage(msg);
        HsmsMessageType type = r.get().messageType();
        if (type == HsmsMessageType.REJECT_REQ) {
            throw new HsmsRejectException(msg);
        }
        if (type != HsmsMessageType.DESELECT_RSP) {
            throw new HsmsNotExpectControlTypeReplyMessageException(msg);
        }
        return r;
    }

    @Override
    public Optional<HsmsMessage> sendDeselectResponse(HsmsMessage primaryMsg, HsmsMessageDeselectStatus status) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.sendAndReplyHsmsMessage(this.messageBuilder().buildDeselectResponse(primaryMsg, status));
    }

    @Override
    public Optional<HsmsMessage> sendLinktestRequest(AbstractHsmsSession session) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        AbstractHsmsControlMessage msg = this.messageBuilder().buildLinktestRequest(session);
        Optional<HsmsMessage> r = this.sendAndReplyHsmsMessage(msg);
        HsmsMessageType type = r.get().messageType();
        if (type == HsmsMessageType.REJECT_REQ) {
            throw new HsmsRejectException(msg);
        }
        if (type != HsmsMessageType.LINKTEST_RSP) {
            throw new HsmsNotExpectControlTypeReplyMessageException(msg);
        }
        return r;
    }

    @Override
    public Optional<HsmsMessage> sendLinktestResponse(HsmsMessage primaryMsg) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.sendAndReplyHsmsMessage(this.messageBuilder().buildLinktestResponse(primaryMsg));
    }

    @Override
    public Optional<HsmsMessage> sendRejectRequest(HsmsMessage referenceMsg, HsmsMessageRejectReason reason) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.sendAndReplyHsmsMessage(this.messageBuilder().buildRejectRequest(referenceMsg, reason));
    }

    @Override
    public Optional<HsmsMessage> sendSeparateRequest(AbstractHsmsSession session) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.sendAndReplyHsmsMessage(this.messageBuilder().buildSeparateRequest(session));
    }

    @Override
    public Optional<HsmsMessage> send(AbstractHsmsSession session, int strm, int func, boolean wbit, Secs2 secs2) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.sendDataMsg(this.messageBuilder().buildDataMessage(session, strm, func, wbit, secs2));
    }

    @Override
    public Optional<HsmsMessage> send(SecsMessage primaryMsg, int strm, int func, boolean wbit, Secs2 secs2) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.sendDataMsg(this.messageBuilder().buildDataMessage(primaryMsg, strm, func, wbit, secs2));
    }

    private Optional<HsmsMessage> sendDataMsg(AbstractHsmsMessage msg) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        Optional<HsmsMessage> r = this.sendAndReplyHsmsMessage(msg);
        HsmsMessageType type = r.map(HsmsMessage::messageType).orElse(null);
        if (type != null) {
            if (type == HsmsMessageType.REJECT_REQ) {
                throw new HsmsRejectException(msg);
            }
            if (type != HsmsMessageType.DATA) {
                throw new HsmsNotExpectControlTypeReplyMessageException(msg);
            }
        }
        return r;
    }

    @Override
    public Optional<HsmsMessage> sendHsmsMessage(HsmsMessage msg) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.sendAndReplyHsmsMessage(this.buildMessageFromMessage(msg));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private Optional<HsmsMessage> sendAndReplyHsmsMessage(AbstractHsmsMessage msg) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        switch (msg.messageType()) {
            case DATA: {
                if (msg.wbit()) {
                    try {
                        this.transactionManager().enter(msg);
                        this.sendOnlyHsmsMessage(msg);
                        AbstractHsmsMessage r = this.transactionManager().reply(msg, this.timeoutT3());
                        if (r == null) {
                            throw new HsmsTimeoutT3Exception(msg);
                        }
                        Optional<HsmsMessage> optional = Optional.of(r);
                        return optional;
                    }
                    finally {
                        this.transactionManager().exit(msg);
                    }
                }
                this.sendOnlyHsmsMessage(msg);
                return Optional.empty();
            }
            case SELECT_REQ: 
            case DESELECT_REQ: 
            case LINKTEST_REQ: {
                try {
                    this.transactionManager().enter(msg);
                    this.sendOnlyHsmsMessage(msg);
                    AbstractHsmsMessage r = this.transactionManager().reply(msg, this.timeoutT6());
                    if (r == null) {
                        throw new HsmsTimeoutT6Exception(msg);
                    }
                    Optional<HsmsMessage> optional = Optional.of(r);
                    return optional;
                }
                finally {
                    this.transactionManager().exit(msg);
                }
            }
        }
        this.sendOnlyHsmsMessage(msg);
        return Optional.empty();
    }

    protected int prototypeDefaultSendBufferSize() {
        return 65536;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void sendOnlyHsmsMessage(AbstractHsmsMessage msg) throws HsmsSendMessageException, HsmsException, InterruptedException {
        this.notifyTrySendHsmsMsgPassThrough(msg);
        this.notifyLog(new HsmsTrySendMessageLog(msg));
        Object object = this.syncSendMsg;
        synchronized (object) {
            try {
                Secs2BytesPack pack = Secs2BytesPackBuilder.build(1024, msg.secs2());
                long len = pack.size() + 10L;
                if (len > 0xFFFFFFFFL || len < 10L) {
                    throw new HsmsTooBigSendMessageException(msg);
                }
                long msglen = len + 4L;
                if (msglen > (long)this.prototypeDefaultSendBufferSize()) {
                    ByteBuffer buffer = ByteBuffer.allocate(14);
                    buffer.put((byte)(len >> 24));
                    buffer.put((byte)(len >> 16));
                    buffer.put((byte)(len >> 8));
                    buffer.put((byte)len);
                    buffer.put(msg.header10Bytes());
                    ((Buffer)buffer).flip();
                    this.sendByteBuffer(buffer, msg);
                    for (byte[] bs : pack.getBytes()) {
                        ByteBuffer buffer2 = ByteBuffer.allocate(bs.length);
                        buffer2.put(bs);
                        ((Buffer)buffer2).flip();
                        this.sendByteBuffer(buffer2, msg);
                    }
                } else {
                    ByteBuffer buffer = ByteBuffer.allocate((int)msglen);
                    buffer.put((byte)(len >> 24));
                    buffer.put((byte)(len >> 16));
                    buffer.put((byte)(len >> 8));
                    buffer.put((byte)len);
                    buffer.put(msg.header10Bytes());
                    for (byte[] bs : pack.getBytes()) {
                        buffer.put(bs);
                    }
                    ((Buffer)buffer).flip();
                    this.sendByteBuffer(buffer, msg);
                }
            }
            catch (Secs2BuildException e) {
                throw new HsmsSendMessageException(msg);
            }
        }
        this.notifySendedHsmsMsgPassThrough(msg);
        this.notifyLog(new HsmsSendedMessageLog(msg));
    }

    private void sendByteBuffer(ByteBuffer buffer, HsmsMessage msg) throws HsmsSendMessageException, HsmsException, InterruptedException {
        while (buffer.hasRemaining()) {
            Future<Integer> f = this.channel.write(buffer);
            try {
                int w = f.get();
                if (w > 0) continue;
                throw new HsmsSendMessageException(msg, (Throwable)new HsmsDetectTerminateException());
            }
            catch (InterruptedException e) {
                f.cancel(true);
                throw e;
            }
            catch (ExecutionException e) {
                Throwable t = e.getCause();
                if (t instanceof RuntimeException) {
                    throw (RuntimeException)t;
                }
                throw new HsmsSendMessageException(msg, t);
            }
        }
        this.resetLinktestTimer();
    }
}

