/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicateState;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionModeIllegalStateException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageRejectReason;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageSelectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsmsss.AbstractHsmsSsCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsConnectionLog;
import java.io.IOException;
import java.net.SocketAddress;
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

public abstract class AbstractHsmsSsActiveCommunicator
extends AbstractHsmsSsCommunicator {
    public AbstractHsmsSsActiveCommunicator(HsmsSsCommunicatorConfig config) {
        super(Objects.requireNonNull(config));
    }

    @Override
    public void open() throws IOException {
        if (this.config().connectionMode().get() != HsmsConnectionMode.ACTIVE) {
            throw new HsmsConnectionModeIllegalStateException("NOT ACTIVE");
        }
        super.open();
        this.executorService().execute(() -> {
            try {
                while (!this.isClosed()) {
                    this.activeCircuit();
                    if (this.isClosed()) {
                        return;
                    }
                    this.config().timeout().t5().sleep();
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
    protected void activeCircuit() throws InterruptedException {
        try (final AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();){
            SocketAddress socketAddr = this.config().socketAddress().getSocketAddress();
            try {
                this.notifyLog(HsmsSsConnectionLog.tryConnect(socketAddr));
                channel.connect(socketAddr, null, new CompletionHandler<Void, Void>(){

                    /*
                     * WARNING - Removed try catching itself - possible behaviour change.
                     */
                    @Override
                    public void completed(Void none, Void attachment) {
                        SocketAddress pLocal = null;
                        SocketAddress pRemote = null;
                        try {
                            try {
                                pLocal = channel.getLocalAddress();
                                pRemote = channel.getRemoteAddress();
                                AbstractHsmsSsActiveCommunicator.this.notifyLog(HsmsSsConnectionLog.connected(pLocal, pRemote));
                                AbstractHsmsSsActiveCommunicator.this.completionAction(channel);
                            }
                            catch (IOException e) {
                                AbstractHsmsSsActiveCommunicator.this.notifyLog(e);
                            }
                        }
                        catch (InterruptedException interruptedException) {
                        }
                        finally {
                            try {
                                channel.shutdownOutput();
                            }
                            catch (IOException iOException) {}
                            AsynchronousSocketChannel asynchronousSocketChannel = channel;
                            synchronized (asynchronousSocketChannel) {
                                channel.notifyAll();
                            }
                            try {
                                AbstractHsmsSsActiveCommunicator.this.notifyLog(HsmsSsConnectionLog.closed(pLocal, pRemote));
                            }
                            catch (InterruptedException interruptedException) {}
                        }
                    }

                    /*
                     * WARNING - Removed try catching itself - possible behaviour change.
                     */
                    @Override
                    public void failed(Throwable t, Void attachment) {
                        if (!(t instanceof ClosedChannelException)) {
                            try {
                                AbstractHsmsSsActiveCommunicator.this.notifyLog(t);
                            }
                            catch (InterruptedException interruptedException) {
                                // empty catch block
                            }
                        }
                        AsynchronousSocketChannel asynchronousSocketChannel = channel;
                        synchronized (asynchronousSocketChannel) {
                            channel.notifyAll();
                        }
                    }
                });
                AsynchronousSocketChannel asynchronousSocketChannel = channel;
                synchronized (asynchronousSocketChannel) {
                    channel.wait();
                }
            }
            finally {
                this.notifyHsmsCommunicateState(HsmsCommunicateState.NOT_CONNECTED);
            }
        }
        catch (IOException e) {
            this.notifyLog(e);
        }
    }

    private void completionAction(AsynchronousSocketChannel channel) throws InterruptedException {
        AbstractHsmsAsyncSocketChannel asyncChannel = this.buildAsyncSocketChannel(channel);
        try {
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
        this.notifyHsmsCommunicateState(HsmsCommunicateState.NOT_SELECTED);
        HsmsMessageSelectStatus selectStatus = asyncChannel.sendSelectRequest(this.getSession()).map(HsmsMessageSelectStatus::get).orElse(HsmsMessageSelectStatus.NOT_SELECT_RSP);
        switch (selectStatus) {
            case SUCCESS: 
            case ACTIVED: {
                if (!this.getSession().setAsyncSocketChannel(asyncChannel)) break;
                try {
                    this.notifyHsmsCommunicateState(HsmsCommunicateState.SELECTED);
                    this.mainSelectedTask(asyncChannel, queue);
                    break;
                }
                finally {
                    this.getSession().unsetAsyncSocketChannel();
                }
            }
        }
    }

    private void mainSelectedTask(AbstractHsmsAsyncSocketChannel asyncChannel, BlockingQueue<AbstractHsmsMessage> queue) throws InterruptedException {
        List<Callable> tasks = Arrays.asList(() -> {
            try {
                try {
                    while (true) {
                        AbstractHsmsMessage msg = (AbstractHsmsMessage)queue.take();
                        switch (msg.messageType()) {
                            case DATA: {
                                this.notifyReceiveMessage(msg);
                                break;
                            }
                            case SELECT_REQ: 
                            case DESELECT_REQ: {
                                asyncChannel.sendRejectRequest(msg, HsmsMessageRejectReason.NOT_SUPPORT_TYPE_S);
                                break;
                            }
                            case LINKTEST_REQ: {
                                asyncChannel.sendLinktestResponse(msg);
                                break;
                            }
                            case SEPARATE_REQ: {
                                return null;
                            }
                        }
                    }
                }
                catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException e) {
                    this.notifyLog(e);
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

