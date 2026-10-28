/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicateState;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionModeIllegalStateException;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsConnectionLog;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsPassiveBindLog;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;

public abstract class AbstractHsmsGsPassiveCommunicator
extends AbstractHsmsGsCommunicator {
    public AbstractHsmsGsPassiveCommunicator(HsmsGsCommunicatorConfig config) {
        super(config);
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
            this.getAbstractHsmsSessions().forEach(s -> s.notifyHsmsCommunicateState(HsmsCommunicateState.NOT_CONNECTED));
            this.passiveAccepting(server);
            AsynchronousServerSocketChannel asynchronousServerSocketChannel = server;
            synchronized (asynchronousServerSocketChannel) {
                server.wait();
            }
        }
        catch (IOException e) {
            this.notifyLog(e);
        }
        finally {
            this.getAbstractHsmsSessions().forEach(s -> s.notifyHsmsCommunicateState(HsmsCommunicateState.NOT_CONNECTED));
        }
    }

    private void passiveAccepting(final AsynchronousServerSocketChannel server) throws IOException, InterruptedException {
        SocketAddress addr = this.config().socketAddress().getSocketAddress();
        this.notifyLog(HsmsGsPassiveBindLog.tryBind(addr));
        server.bind(addr);
        this.notifyLog(HsmsGsPassiveBindLog.binded(addr));
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
                        AbstractHsmsGsPassiveCommunicator.this.notifyLog(e);
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
                            AbstractHsmsGsPassiveCommunicator.this.notifyLog(HsmsGsConnectionLog.closed(pLocal, pRemote));
                        }
                        catch (InterruptedException interruptedException) {
                            // empty catch block
                        }
                        return;
                    }
                    AbstractHsmsGsPassiveCommunicator.this.notifyLog(HsmsGsConnectionLog.accepted(pLocal, pRemote));
                    AbstractHsmsGsPassiveCommunicator.this.completionAction(channel);
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
                        AbstractHsmsGsPassiveCommunicator.this.notifyLog(HsmsGsConnectionLog.closed(pLocal, pRemote));
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
                        AbstractHsmsGsPassiveCommunicator.this.notifyLog(t);
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
}

