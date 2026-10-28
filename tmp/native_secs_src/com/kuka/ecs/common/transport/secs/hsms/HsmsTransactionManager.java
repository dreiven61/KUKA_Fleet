/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.AbstractSecsMessage;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HsmsTransactionManager<T extends AbstractHsmsMessage> {
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

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public T reply(T msg, long timeout, TimeUnit unit) throws InterruptedException {
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
            unit.timedWait(p, timeout);
            return p.replyMsg();
        }
    }

    public T reply(T msg, ReadOnlyTimeProperty timeout) throws InterruptedException {
        return this.reply(msg, timeout.getMilliSeconds(), TimeUnit.MILLISECONDS);
    }

    public T put(T msg) {
        Pack p = this.getPack(msg);
        if (p == null) {
            return msg;
        }
        p.putReplyMsg(msg);
        return null;
    }

    private class Pack {
        private T replyMsg = null;

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void putReplyMsg(T replyMsg) {
            Pack pack = this;
            synchronized (pack) {
                this.replyMsg = replyMsg;
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

