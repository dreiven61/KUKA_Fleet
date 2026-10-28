/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

public final class HsmsGsPassiveBindLog
extends AbstractSecsLog {
    private static final long serialVersionUID = -7606695183029225489L;
    private final SocketAddress addr;
    private final boolean isBinded;
    private final boolean isClosed;
    private String cacheToValueString;
    private static final String subjectTryBind = "HSMS-GS Passive Try-Bind";
    private static final String subjectBinded = "HSMS-GS Passive Binded";
    private static final String subjectClosed = "HSMS-GS Passive Closed";

    public HsmsGsPassiveBindLog(CharSequence subject, LocalDateTime timestamp, SocketAddress addr, boolean isBinded, boolean isClosed) {
        super(subject, timestamp, addr);
        this.addr = addr;
        this.isBinded = isBinded;
        this.isClosed = isClosed;
        this.cacheToValueString = null;
    }

    public HsmsGsPassiveBindLog(CharSequence subject, SocketAddress addr, boolean isBinded, boolean isClosed) {
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
        HsmsGsPassiveBindLog hsmsGsPassiveBindLog = this;
        synchronized (hsmsGsPassiveBindLog) {
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

    public static HsmsGsPassiveBindLog tryBind(SocketAddress addr) {
        return new HsmsGsPassiveBindLog(subjectTryBind, addr, false, false);
    }

    public static HsmsGsPassiveBindLog tryBind(SocketAddress addr, LocalDateTime timestamp) {
        return new HsmsGsPassiveBindLog(subjectTryBind, timestamp, addr, false, false);
    }

    public static HsmsGsPassiveBindLog binded(SocketAddress addr) {
        return new HsmsGsPassiveBindLog(subjectBinded, addr, true, false);
    }

    public static HsmsGsPassiveBindLog binded(SocketAddress addr, LocalDateTime timestamp) {
        return new HsmsGsPassiveBindLog(subjectBinded, timestamp, addr, true, false);
    }

    public static HsmsGsPassiveBindLog closed(SocketAddress addr) {
        return new HsmsGsPassiveBindLog(subjectClosed, addr, false, true);
    }

    public static HsmsGsPassiveBindLog closed(SocketAddress addr, LocalDateTime timestamp) {
        return new HsmsGsPassiveBindLog(subjectClosed, timestamp, addr, false, true);
    }
}

