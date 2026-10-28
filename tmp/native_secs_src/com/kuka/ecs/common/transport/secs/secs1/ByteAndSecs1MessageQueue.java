/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Message;
import com.kuka.ecs.common.transport.secs.secs1.ByteOrSecs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBlockPack;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendMessageException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class ByteAndSecs1MessageQueue {
    private final Object sync = new Object();
    private final BlockingQueue<Byte> bb = new LinkedBlockingQueue<Byte>();
    private final Queue<Secs1MessageBlockPack> mm = new LinkedList<Secs1MessageBlockPack>();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void clear() {
        Object object = this.sync;
        synchronized (object) {
            this.bb.clear();
            this.mm.clear();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void putByte(byte b) throws InterruptedException {
        Object object = this.sync;
        synchronized (object) {
            this.bb.add(b);
            this.sync.notifyAll();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void putBytes(byte[] bs) throws InterruptedException {
        Object object = this.sync;
        synchronized (object) {
            for (byte b : bs) {
                this.bb.add(b);
            }
            this.sync.notifyAll();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void putSecs1Message(AbstractSecs1Message msg) throws Secs1SendMessageException, InterruptedException {
        Object object = this.sync;
        synchronized (object) {
            this.mm.add(new Secs1MessageBlockPack(msg));
            this.sync.notifyAll();
        }
    }

    public ByteOrSecs1Message takeByteOrSecs1Message() throws InterruptedException {
        Object object = this.sync;
        synchronized (object) {
            while (true) {
                Secs1MessageBlockPack msgPack;
                if ((msgPack = this.mm.poll()) != null) {
                    return new ByteOrSecs1Message(null, msgPack);
                }
                Byte b = this.pollByte();
                if (b != null) {
                    return new ByteOrSecs1Message(b, null);
                }
                this.sync.wait();
            }
        }
    }

    public Byte pollByte() {
        return (Byte)this.bb.poll();
    }

    public Byte pollByte(long timeout, TimeUnit unit) throws InterruptedException {
        return this.bb.poll(timeout, unit);
    }

    public Byte pollByte(ReadOnlyTimeProperty v) throws InterruptedException {
        return v.poll(this.bb);
    }

    public int pollBytes(byte[] bs, int pos, int len, long timeout, TimeUnit unit) throws InterruptedException {
        Byte b;
        int r = 0;
        for (int i = pos; i < len && (b = this.bb.poll(timeout, unit)) != null; ++i) {
            bs[i] = b;
            ++r;
        }
        return r;
    }

    public int pollBytes(byte[] bs, int pos, int len, ReadOnlyTimeProperty timeout) throws InterruptedException {
        return this.pollBytes(bs, pos, len, timeout.getMilliSeconds(), TimeUnit.MILLISECONDS);
    }

    public void garbageBytes(long timeout, TimeUnit unit) throws InterruptedException {
        Byte b;
        this.bb.clear();
        while ((b = this.bb.poll(timeout, unit)) != null) {
        }
    }

    public void garbageBytes(ReadOnlyTimeProperty timeout) throws InterruptedException {
        this.garbageBytes(timeout.getMilliSeconds(), TimeUnit.MILLISECONDS);
    }
}

