/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Exception;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendMessageException;
import java.util.HashMap;
import java.util.Map;

public final class Secs1SendMessageManager {
    private final Map<Integer, Result> map = new HashMap<Integer, Result>();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void clear() {
        Map<Integer, Result> map = this.map;
        synchronized (map) {
            this.map.clear();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void enter(AbstractSecs1Message msg) {
        Map<Integer, Result> map = this.map;
        synchronized (map) {
            this.map.put(msg.systemBytesKey(), new Result());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void exit(AbstractSecs1Message msg) {
        Map<Integer, Result> map = this.map;
        synchronized (map) {
            this.map.remove(msg.systemBytesKey());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private Result getResult(Integer key) {
        Map<Integer, Result> map = this.map;
        synchronized (map) {
            return this.map.get(key);
        }
    }

    private Result getResult(AbstractSecs1Message msg) {
        return this.getResult(msg.systemBytesKey());
    }

    public void waitUntilSended(AbstractSecs1Message msg) throws Secs1SendMessageException, InterruptedException {
        Result r = this.getResult(msg);
        if (r == null) {
            throw new IllegalStateException("message not enter");
        }
        Result result = r;
        synchronized (result) {
            while (true) {
                if (r.isSended()) {
                    return;
                }
                Secs1Exception e = r.except();
                if (e != null) {
                    throw new Secs1SendMessageException(msg, (Throwable)e);
                }
                r.wait();
            }
        }
    }

    public void putSended(AbstractSecs1Message msg) {
        Result r = this.getResult(msg);
        if (r != null) {
            r.setSended();
        }
    }

    public void putException(AbstractSecs1Message msg, Secs1Exception e) {
        Result r = this.getResult(msg);
        if (r != null) {
            r.setExcept(e);
        }
    }

    private class Result {
        private boolean sended = false;
        private Secs1Exception except = null;

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void setSended() {
            Result result = this;
            synchronized (result) {
                this.sended = true;
                this.notifyAll();
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public boolean isSended() {
            Result result = this;
            synchronized (result) {
                return this.sended;
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void setExcept(Secs1Exception e) {
            Result result = this;
            synchronized (result) {
                this.except = e;
                this.notifyAll();
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public Secs1Exception except() {
            Result result = this;
            synchronized (result) {
                return this.except;
            }
        }
    }
}

