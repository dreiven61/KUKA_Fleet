/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionModeIllegalStateException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageRejectReason;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageSelectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsTimeoutT7Exception;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsmsss.AbstractHsmsSsCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsConnectionLog;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsPassiveBindLog;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsPassiveReceiveNotSelectRequestException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class AbstractHsmsSsPassiveCommunicator
extends AbstractHsmsSsCommunicator {
    public AbstractHsmsSsPassiveCommunicator(HsmsSsCommunicatorConfig config) {
        super(Objects.requireNonNull(config));
    }

    @Override
    public void open() throws IOException {
        if (this.config().connectionMode().get() != HsmsConnectionMode.PASSIVE) {
            throw new HsmsConnectionModeIllegalStateException("NOT PASSIVE");
        }
        super.open();
        this.executorService().execute(() -> {
            try {
                ReadOnlyTimeProperty tp = this.config().rebindIfPassive();
                while (!this.isClosed()) {
                    this.openPassive();
                    if (this.isClosed()) {
                        return;
                    }
                    if (tp.gtZero()) {
                        tp.sleep();
                        continue;
                    }
                    return;
                }
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
        });
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void openPassive() throws InterruptedException {
        try (AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();){
            this.passiveAccepting(server);
            AsynchronousServerSocketChannel asynchronousServerSocketChannel = server;
            synchronized (asynchronousServerSocketChannel) {
                server.wait();
            }
        }
        catch (IOException e) {
            this.notifyLog(e);
        }
    }

    private void passiveAccepting(final AsynchronousServerSocketChannel server) throws IOException, InterruptedException {
        SocketAddress addr = this.config().socketAddress().getSocketAddress();
        this.notifyLog(HsmsSsPassiveBindLog.tryBind(addr));
        server.bind(addr);
        this.notifyLog(HsmsSsPassiveBindLog.binded(addr));
        server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>(){

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            @Override
            public void completed(AsynchronousSocketChannel channel, Void attachment) {
                server.accept(attachment, this);
                SocketAddress pLocal = null;
                SocketAddress pRemote = null;
                try {
                    try {
                        pLocal = channel.getLocalAddress();
                        pRemote = channel.getRemoteAddress();
                    }
                    catch (IOException e) {
                        AbstractHsmsSsPassiveCommunicator.this.notifyLog(e);
                        try {
                            channel.shutdownOutput();
                        }
                        catch (IOException iOException) {
                            // empty catch block
                        }
                        try {
                            channel.close();
                        }
                        catch (IOException iOException) {
                            // empty catch block
                        }
                        try {
                            AbstractHsmsSsPassiveCommunicator.this.notifyLog(HsmsSsConnectionLog.closed(pLocal, pRemote));
                        }
                        catch (InterruptedException interruptedException) {
                            // empty catch block
                        }
                        return;
                    }
                    AbstractHsmsSsPassiveCommunicator.this.notifyLog(HsmsSsConnectionLog.accepted(pLocal, pRemote));
                    AbstractHsmsSsPassiveCommunicator.this.completionAction(channel);
                }
                catch (InterruptedException interruptedException) {
                }
                finally {
                    try {
                        channel.shutdownOutput();
                    }
                    catch (IOException iOException) {}
                    try {
                        channel.close();
                    }
                    catch (IOException iOException) {}
                    try {
                        AbstractHsmsSsPassiveCommunicator.this.notifyLog(HsmsSsConnectionLog.closed(pLocal, pRemote));
                    }
                    catch (InterruptedException interruptedException) {}
                }
            }

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            @Override
            public void failed(Throwable t, Void attachment) {
                if (t instanceof RuntimeException) {
                    throw (RuntimeException)t;
                }
                if (!(t instanceof ClosedChannelException)) {
                    try {
                        AbstractHsmsSsPassiveCommunicator.this.notifyLog(t);
                    }
                    catch (InterruptedException interruptedException) {
                        // empty catch block
                    }
                }
                AsynchronousServerSocketChannel asynchronousServerSocketChannel = server;
                synchronized (asynchronousServerSocketChannel) {
                    server.notifyAll();
                }
            }
        });
    }

    private void completionAction(AsynchronousSocketChannel channel) throws InterruptedException {
        AbstractHsmsAsyncSocketChannel asyncChannel = this.buildAsyncSocketChannel(channel);
        List<Callable> tasks = Arrays.asList(() -> {
            try {
                try {
                    asyncChannel.reading();
                }
                catch (HsmsException e) {
                    this.notifyLog(e);
                }
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            return null;
        }, () -> {
            try {
                asyncChannel.waitingUntilShutdown();
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            return null;
        }, () -> {
            try {
                try {
                    this.mainTask(asyncChannel);
                }
                catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException e) {
                    this.notifyLog(e);
                }
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            return null;
        });
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

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private final void mainTask(AbstractHsmsAsyncSocketChannel asyncChannel) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        LinkedBlockingQueue<AbstractHsmsMessage> queue = new LinkedBlockingQueue<AbstractHsmsMessage>();
        asyncChannel.addHsmsMessageReceiveListener(msg -> {
            try {
                queue.put(msg);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
        });
        HsmsMessage msg2 = (HsmsMessage)this.config().timeout().t7().poll(queue);
        if (msg2 == null) {
            throw new HsmsTimeoutT7Exception();
        }
        switch (msg2.messageType()) {
            case SELECT_REQ: {
                if (this.getSession().setAsyncSocketChannel(asyncChannel)) {
                    try {
                        asyncChannel.sendSelectResponse(msg2, HsmsMessageSelectStatus.SUCCESS);
                        this.mainSelectedTask(asyncChannel, queue);
                        break;
                    }
                    finally {
                        this.getSession().unsetAsyncSocketChannel();
                    }
                }
                asyncChannel.sendSelectResponse(msg2, HsmsMessageSelectStatus.ALREADY_USED);
                break;
            }
            default: {
                throw new HsmsSsPassiveReceiveNotSelectRequestException();
            }
        }
    }

    private void mainSelectedTask(AbstractHsmsAsyncSocketChannel asyncChannel, BlockingQueue<AbstractHsmsMessage> queue) throws InterruptedException {
        List<Callable> tasks = Arrays.asList(() -> {
            try {
                try {
                    AbstractHsmsMessage msg;
                    block13: while (true) {
                        msg = (AbstractHsmsMessage)queue.take();
                        switch (msg.messageType()) {
                            case DATA: {
                                this.notifyReceiveMessage(msg);
                                continue block13;
                            }
                            case SELECT_REQ: {
                                asyncChannel.sendSelectResponse(msg, HsmsMessageSelectStatus.ACTIVED);
                                continue block13;
                            }
                            case DESELECT_REQ: 
                            case DESELECT_RSP: {
                                asyncChannel.sendRejectRequest(msg, HsmsMessageRejectReason.NOT_SUPPORT_TYPE_S);
                                continue block13;
                            }
                            case LINKTEST_REQ: {
                                asyncChannel.sendLinktestResponse(msg);
                                continue block13;
                            }
                            case SEPARATE_REQ: {
                                return null;
                            }
                            case REJECT_REQ: {
                                return null;
                            }
                            case SELECT_RSP: 
                            case LINKTEST_RSP: {
                                asyncChannel.sendRejectRequest(msg, HsmsMessageRejectReason.TRANSACTION_NOT_OPEN);
                                return null;
                            }
                        }
                        break;
                    }
                    if (HsmsMessageType.supportSType(msg)) {
                        if (HsmsMessageType.supportPType(msg)) return null;
                        asyncChannel.sendRejectRequest(msg, HsmsMessageRejectReason.NOT_SUPPORT_TYPE_P);
                        return null;
                    }
                    asyncChannel.sendRejectRequest(msg, HsmsMessageRejectReason.NOT_SUPPORT_TYPE_S);
                    return null;
                }
                catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException e) {
                    this.notifyLog(e);
                    return null;
                }
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            return null;
        }, () -> {
            try {
                try {
                    asyncChannel.linktesting();
                }
                catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException e) {
                    this.notifyLog(e);
                }
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            return null;
        });
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
}

