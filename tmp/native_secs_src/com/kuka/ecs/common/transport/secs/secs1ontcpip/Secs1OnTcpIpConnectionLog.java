/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1ontcpip;

import com.kuka.ecs.common.transport.secs.AbstractSecsConnectionLog;
import java.net.SocketAddress;
import java.time.LocalDateTime;

public final class Secs1OnTcpIpConnectionLog
extends AbstractSecsConnectionLog {
    private static final long serialVersionUID = -6683232225197363974L;
    private static final String subjectTryConnect = "SECS1-ON-TCP/IP Try-Connect";
    private static final String subjectConnected = "SECS1-ON-TCP/IP Connected";
    private static final String subjectDisconnected = "SECS1-ON-TCP/IP Disconnected";

    private Secs1OnTcpIpConnectionLog(CharSequence subject, LocalDateTime timestamp, SocketAddress local, SocketAddress remote, boolean isConnecting) {
        super(subject, timestamp, local, remote, isConnecting);
    }

    private Secs1OnTcpIpConnectionLog(CharSequence subject, SocketAddress local, SocketAddress remote, boolean isConnecting) {
        super(subject, local, remote, isConnecting);
    }

    public static Secs1OnTcpIpConnectionLog tryConnect(SocketAddress remote) {
        return new Secs1OnTcpIpConnectionLog(subjectTryConnect, null, remote, false);
    }

    public static Secs1OnTcpIpConnectionLog tryConnect(SocketAddress remote, LocalDateTime timestamp) {
        return new Secs1OnTcpIpConnectionLog(subjectTryConnect, timestamp, null, remote, false);
    }

    public static Secs1OnTcpIpConnectionLog connected(SocketAddress local, SocketAddress remote) {
        return new Secs1OnTcpIpConnectionLog(subjectConnected, local, remote, true);
    }

    public static Secs1OnTcpIpConnectionLog connected(SocketAddress local, SocketAddress remote, LocalDateTime timestamp) {
        return new Secs1OnTcpIpConnectionLog(subjectConnected, timestamp, local, remote, true);
    }

    public static Secs1OnTcpIpConnectionLog disconnected(SocketAddress local, SocketAddress remote) {
        return new Secs1OnTcpIpConnectionLog(subjectDisconnected, local, remote, false);
    }

    public static Secs1OnTcpIpConnectionLog disconnected(SocketAddress local, SocketAddress remote, LocalDateTime timestamp) {
        return new Secs1OnTcpIpConnectionLog(subjectDisconnected, timestamp, local, remote, false);
    }
}

