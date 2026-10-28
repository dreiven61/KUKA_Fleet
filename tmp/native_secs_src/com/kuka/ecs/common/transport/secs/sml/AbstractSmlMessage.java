/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.sml;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.sml.SmlMessage;
import java.io.Serializable;

public abstract class AbstractSmlMessage
implements SmlMessage,
Serializable {
    private static final long serialVersionUID = 6167166233801516804L;
    private final int strm;
    private final int func;
    private final boolean wbit;
    private final Secs2 secs2;
    private final Object sync = new Object();
    private String cacheToString;
    protected static final String BR = System.lineSeparator();

    protected AbstractSmlMessage(int strm, int func, boolean wbit, Secs2 secs2) {
        this.strm = strm;
        this.func = func;
        this.wbit = wbit;
        this.secs2 = secs2;
        this.cacheToString = null;
    }

    @Override
    public int getStream() {
        return this.strm;
    }

    @Override
    public int getFunction() {
        return this.func;
    }

    @Override
    public boolean wbit() {
        return this.wbit;
    }

    @Override
    public Secs2 secs2() {
        return this.secs2;
    }

    public String toString() {
        return this.proxyToString();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected String proxyToString() {
        Object object = this.sync;
        synchronized (object) {
            if (this.cacheToString == null) {
                String body;
                StringBuilder sb = new StringBuilder("S").append(this.strm).append("F").append(this.func);
                if (this.wbit) {
                    sb.append(" W");
                }
                if (!(body = this.secs2.toString()).isEmpty()) {
                    sb.append(BR).append(body);
                }
                this.cacheToString = sb.append(".").toString();
            }
            return this.cacheToString;
        }
    }
}

