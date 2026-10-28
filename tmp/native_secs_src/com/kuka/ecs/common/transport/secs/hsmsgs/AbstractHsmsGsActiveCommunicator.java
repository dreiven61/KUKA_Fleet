/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicateState;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionModeIllegalStateException;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsConnectionLog;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;

public abstract class AbstractHsmsGsActiveCommunicator
extends AbstractHsmsGsCommunicator {
    public AbstractHsmsGsActiveCommunicator(HsmsGsCommunicatorConfig config) {
        super(config);
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

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected void activeCircuit() throws InterruptedException {
        try (final AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();){
            SocketAddress socketAddr = this.config().socketAddress().getSocketAddress();
            this.notifyLog(HsmsGsConnectionLog.tryConnect(socketAddr));
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
                            AbstractHsmsGsActiveCommunicator.this.notifyLog(HsmsGsConnectionLog.connected(pLocal, pRemote));
                            try {
                                AbstractHsmsGsActiveCommunicator.this.getAbstractHsmsSessions().forEach(s -> s.notifyHsmsCommunicateState(HsmsCommunicateState.NOT_SELECTED));
                                AbstractHsmsGsActiveCommunicator.this.completionAction(channel);
                            }
                            finally {
                                AbstractHsmsGsActiveCommunicator.this.getAbstractHsmsSessions().forEach(s -> s.notifyHsmsCommunicateState(HsmsCommunicateState.NOT_CONNECTED));
                            }
                        }
                        catch (IOException e) {
                            AbstractHsmsGsActiveCommunicator.this.notifyLog(e);
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
                            AbstractHsmsGsActiveCommunicator.this.notifyLog(HsmsGsConnectionLog.closed(pLocal, pRemote));
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
                            AbstractHsmsGsActiveCommunicator.this.notifyLog(t);
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
        catch (IOException e) {
            this.notifyLog(e);
        }
    }
}

