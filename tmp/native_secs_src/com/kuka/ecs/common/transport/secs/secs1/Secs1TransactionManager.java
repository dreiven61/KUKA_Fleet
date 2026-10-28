/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.AbstractSecsMessage;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Message;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1MessageBlock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Secs1TransactionManager<T extends AbstractSecs1Message, U extends AbstractSecs1MessageBlock> {
    private final Map<Integer, Pack> map = new HashMap<Integer, Pack>();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void clear() {
        Map<Integer, Pack> map = this.map;
        synchronized (map) {
            this.map.clear();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void enter(T primaryMsg) {
        Map<Integer, Pack> map = this.map;
        synchronized (map) {
            this.map.put(((AbstractSecsMessage)primaryMsg).systemBytesKey(), new Pack());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void exit(T primaryMsg) {
        Map<Integer, Pack> map = this.map;
        synchronized (map) {
            this.map.remove(((AbstractSecsMessage)primaryMsg).systemBytesKey());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private Pack getPack(Integer key) {
        Map<Integer, Pack> map = this.map;
        synchronized (map) {
            return this.map.get(key);
        }
    }

    private Pack getPack(T msg) {
        return this.getPack(((AbstractSecsMessage)msg).systemBytesKey());
    }

    public T waitReply(T msg, ReadOnlyTimeProperty timeout) throws InterruptedException {
        return this.waitReply(msg, timeout.getMilliSeconds(), TimeUnit.MILLISECONDS);
    }

    public T waitReply(T msg, long timeout, TimeUnit unit) throws InterruptedException {
        Pack p = this.getPack(msg);
        if (p == null) {
            return null;
        }
        Pack pack = p;
        synchronized (pack) {
            Object r = p.replyMsg();
            if (r != null) {
                return r;
            }
            do {
                unit.timedWait(p, timeout);
                r = p.replyMsg();
                if (r == null) continue;
                return r;
            } while (p.isTimerResetted());
            return null;
        }
    }

    public T put(T msg) {
        Pack p = this.getPack(msg);
        if (p == null) {
            return msg;
        }
        p.putReplyMsg(msg);
        return null;
    }

    public void resetTimer(AbstractSecs1MessageBlock block) {
        Pack p = this.getPack(block.systemBytesKey());
        if (p != null) {
            p.resetTimer();
        }
    }

    private final class Pack {
        private boolean timerResetted = false;
        private T replyMsg = null;

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void resetTimer() {
            Pack pack = this;
            synchronized (pack) {
                this.timerResetted = true;
                this.notifyAll();
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public boolean isTimerResetted() {
            Pack pack = this;
            synchronized (pack) {
                boolean f = this.timerResetted;
                this.timerResetted = false;
                return f;
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void putReplyMsg(T msg) {
            Pack pack = this;
            synchronized (pack) {
                this.replyMsg = msg;
                this.notifyAll();
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public T replyMsg() {
            Pack pack = this;
            synchronized (pack) {
                return this.replyMsg;
            }
        }
    }
}

