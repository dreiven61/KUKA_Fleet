/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

public final class HsmsSsPassiveBindLog
extends AbstractSecsLog {
    private static final long serialVersionUID = -560884385997725703L;
    private final SocketAddress addr;
    private final boolean isBinded;
    private final boolean isClosed;
    private String cacheToValueString;
    private static final String subjectTryBind = "HSMS-SS Passive Try-Bind";
    private static final String subjectBinded = "HSMS-SS Passive Binded";
    private static final String subjectClosed = "HSMS-SS Passive Closed";

    public HsmsSsPassiveBindLog(CharSequence subject, LocalDateTime timestamp, SocketAddress addr, boolean isBinded, boolean isClosed) {
        super(subject, timestamp, addr);
        this.addr = addr;
        this.isBinded = isBinded;
        this.isClosed = isClosed;
        this.cacheToValueString = null;
    }

    public HsmsSsPassiveBindLog(CharSequence subject, SocketAddress addr, boolean isBinded, boolean isClosed) {
        super(subject, addr);
        this.addr = addr;
        this.isBinded = isBinded;
        this.isClosed = isClosed;
        this.cacheToValueString = null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Optional<String> optionalValueString() {
        HsmsSsPassiveBindLog hsmsSsPassiveBindLog = this;
        synchronized (hsmsSsPassiveBindLog) {
            if (this.cacheToValueString == null) {
                ArrayList<String> ll = new ArrayList<String>();
                if (this.addr != null) {
                    ll.add(this.addr.toString());
                }
                if (this.isClosed) {
                    ll.add("closed");
                } else {
                    ll.add("binded:" + this.isBinded);
                }
                this.cacheToValueString = ll.stream().collect(Collectors.joining(", "));
            }
            if (this.cacheToValueString.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(this.cacheToValueString);
        }
    }

    public SocketAddress socketAddress() {
        return this.addr;
    }

    public boolean isBinded() {
        return this.isBinded;
    }

    public boolean isClosed() {
        return this.isClosed;
    }

    public static HsmsSsPassiveBindLog tryBind(SocketAddress addr) {
        return new HsmsSsPassiveBindLog(subjectTryBind, addr, false, false);
    }

    public static HsmsSsPassiveBindLog tryBind(SocketAddress addr, LocalDateTime timestamp) {
        return new HsmsSsPassiveBindLog(subjectTryBind, timestamp, addr, false, false);
    }

    public static HsmsSsPassiveBindLog binded(SocketAddress addr) {
        return new HsmsSsPassiveBindLog(subjectBinded, addr, true, false);
    }

    public static HsmsSsPassiveBindLog binded(SocketAddress addr, LocalDateTime timestamp) {
        return new HsmsSsPassiveBindLog(subjectBinded, timestamp, addr, true, false);
    }

    public static HsmsSsPassiveBindLog closed(SocketAddress addr) {
        return new HsmsSsPassiveBindLog(subjectClosed, addr, false, true);
    }

    public static HsmsSsPassiveBindLog closed(SocketAddress addr, LocalDateTime timestamp) {
        return new HsmsSsPassiveBindLog(subjectClosed, timestamp, addr, false, true);
    }
}

