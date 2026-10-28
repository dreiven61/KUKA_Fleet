/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1ontcpip;

import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Communicator;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendByteException;
import com.kuka.ecs.common.transport.secs.secs1ontcpip.Secs1OnTcpIpCommunicator;
import com.kuka.ecs.common.transport.secs.secs1ontcpip.Secs1OnTcpIpCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.secs1ontcpip.Secs1OnTcpIpConnectionLog;
import com.kuka.ecs.common.transport.secs.secs1ontcpip.Secs1OnTcpIpDetectTerminateException;
import com.kuka.ecs.common.transport.secs.secs1ontcpip.Secs1OnTcpIpNotConnectedException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class AbstractSecs1OnTcpIpCommunicator
extends AbstractSecs1Communicator
implements Secs1OnTcpIpCommunicator {
    private final Secs1OnTcpIpCommunicatorConfig secs1OnTcpIpConfig;
    private final List<AsynchronousSocketChannel> channels = new ArrayList<AsynchronousSocketChannel>();

    public AbstractSecs1OnTcpIpCommunicator(Secs1OnTcpIpCommunicatorConfig config) {
        super(Objects.requireNonNull(config));
        this.secs1OnTcpIpConfig = config;
    }

    @Override
    public void open() throws IOException {
        super.open();
        this.executorService().execute(() -> {
            try {
                while (!this.isClosed()) {
                    this.connect();
                    if (this.isClosed()) {
                        return;
                    }
                    this.secs1OnTcpIpConfig.reconnectSeconds().sleep();
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
    private void connect() throws InterruptedException {
        try (final AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();){
            SocketAddress socketAddr = this.secs1OnTcpIpConfig.socketAddress().getSocketAddress();
            this.notifyLog(Secs1OnTcpIpConnectionLog.tryConnect(socketAddr));
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
                            AbstractSecs1OnTcpIpCommunicator.this.addChannel(channel);
                            AbstractSecs1OnTcpIpCommunicator.this.notifyLog(Secs1OnTcpIpConnectionLog.connected(pLocal, pRemote));
                            List<Callable> tasks = Arrays.asList(() -> {
                                AbstractSecs1OnTcpIpCommunicator.this.reading(channel);
                                return null;
                            });
                            AbstractSecs1OnTcpIpCommunicator.this.executeInvokeAny(tasks);
                        }
                        catch (IOException e) {
                            AbstractSecs1OnTcpIpCommunicator.this.notifyLog(e);
                        }
                        catch (ExecutionException e) {
                            Throwable t = e.getCause();
                            if (t instanceof RuntimeException) {
                                throw (RuntimeException)t;
                            }
                            AbstractSecs1OnTcpIpCommunicator.this.notifyLog(t);
                        }
                        finally {
                            AbstractSecs1OnTcpIpCommunicator.this.removeChannel(channel);
                            try {
                                channel.shutdownOutput();
                            }
                            catch (IOException e) {}
                            try {
                                AbstractSecs1OnTcpIpCommunicator.this.notifyLog(Secs1OnTcpIpConnectionLog.disconnected(pLocal, pRemote));
                            }
                            catch (InterruptedException e) {}
                            AsynchronousSocketChannel e = channel;
                            synchronized (e) {
                                channel.notifyAll();
                            }
                        }
                    }
                    catch (InterruptedException interruptedException) {
                        // empty catch block
                    }
                }

                /*
                 * WARNING - Removed try catching itself - possible behaviour change.
                 */
                @Override
                public void failed(Throwable t, Void attachment) {
                    if (!(t instanceof ClosedChannelException)) {
                        try {
                            AbstractSecs1OnTcpIpCommunicator.this.notifyLog(t);
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

    /*
     * Exception decompiling
     */
    private void reading(AsynchronousSocketChannel channel) throws Exception {
        /*
         * This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.
         * 
         * org.benf.cfr.reader.util.ConfusedCFRException: Tried to end blocks [0[TRYBLOCK]], but top level block is 5[UNCONDITIONALDOLOOP]
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.processEndingBlocks(Op04StructuredStatement.java:435)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.buildNestedBlocks(Op04StructuredStatement.java:484)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement.createInitialStructuredBlock(Op03SimpleStatement.java:736)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisInner(CodeAnalyser.java:850)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisOrWrapFail(CodeAnalyser.java:278)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysis(CodeAnalyser.java:201)
         *     at org.benf.cfr.reader.entities.attributes.AttributeCode.analyse(AttributeCode.java:94)
         *     at org.benf.cfr.reader.entities.Method.analyse(Method.java:531)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseMid(ClassFile.java:1055)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseTop(ClassFile.java:942)
         *     at org.benf.cfr.reader.Driver.doJarVersionTypes(Driver.java:257)
         *     at org.benf.cfr.reader.Driver.doJar(Driver.java:139)
         *     at org.benf.cfr.reader.CfrDriverImpl.analyse(CfrDriverImpl.java:76)
         *     at org.benf.cfr.reader.Main.main(Main.java:54)
         */
        throw new IllegalStateException("Decompilation failed");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean addChannel(AsynchronousSocketChannel channel) {
        List<AsynchronousSocketChannel> list = this.channels;
        synchronized (list) {
            boolean f = this.channels.add(channel);
            this.notifyCommunicatableStateChange(!this.channels.isEmpty());
            return f;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean removeChannel(AsynchronousSocketChannel channel) {
        List<AsynchronousSocketChannel> list = this.channels;
        synchronized (list) {
            boolean f = this.channels.remove(channel);
            this.notifyCommunicatableStateChange(!this.channels.isEmpty());
            return f;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private AsynchronousSocketChannel getChannel() throws Secs1OnTcpIpNotConnectedException {
        List<AsynchronousSocketChannel> list = this.channels;
        synchronized (list) {
            if (this.channels.isEmpty()) {
                throw new Secs1OnTcpIpNotConnectedException();
            }
            return this.channels.get(0);
        }
    }

    @Override
    public void sendBytes(byte[] bs) throws Secs1SendByteException, InterruptedException {
        AsynchronousSocketChannel channel = this.getChannel();
        ByteBuffer buffer = ByteBuffer.allocate(bs.length);
        buffer.put(bs);
        ((Buffer)buffer).flip();
        while (buffer.hasRemaining()) {
            Future<Integer> f = channel.write(buffer);
            try {
                int w = f.get();
                if (w > 0) continue;
                throw new Secs1OnTcpIpDetectTerminateException();
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
                throw new Secs1SendByteException(t);
            }
        }
    }
}

