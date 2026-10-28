/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import com.kuka.ecs.common.transport.secs.SecsConnectionLog;
import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AbstractSecsConnectionLog
extends AbstractSecsLog
implements SecsConnectionLog {
    private static final long serialVersionUID = 100182214359308094L;
    private final SocketAddress local;
    private final SocketAddress remote;
    private final boolean isConneting;
    private String cacheToStringValue;

    public AbstractSecsConnectionLog(CharSequence subject, LocalDateTime timestamp, SocketAddress local, SocketAddress remote, boolean isConnecting) {
        super(subject, timestamp);
        this.local = local;
        this.remote = remote;
        this.isConneting = isConnecting;
        this.cacheToStringValue = null;
    }

    public AbstractSecsConnectionLog(CharSequence subject, SocketAddress local, SocketAddress remote, boolean isConnecting) {
        super(subject);
        this.local = local;
        this.remote = remote;
        this.isConneting = isConnecting;
        this.cacheToStringValue = null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Optional<String> optionalValueString() {
        AbstractSecsConnectionLog abstractSecsConnectionLog = this;
        synchronized (abstractSecsConnectionLog) {
            if (this.cacheToStringValue == null) {
                ArrayList<String> ll = new ArrayList<String>();
                if (this.local != null) {
                    ll.add("local:" + this.local.toString());
                }
                if (this.remote != null) {
                    ll.add("remote:" + this.remote.toString());
                }
                ll.add("connecting:" + this.isConneting);
                this.cacheToStringValue = ll.stream().collect(Collectors.joining(", "));
            }
            if (this.cacheToStringValue.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(this.cacheToStringValue);
        }
    }

    @Override
    public SocketAddress local() {
        return this.local;
    }

    @Override
    public SocketAddress remote() {
        return this.remote;
    }

    @Override
    public boolean isConnecting() {
        return this.isConneting;
    }
}

