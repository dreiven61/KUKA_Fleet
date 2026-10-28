/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.AbstractSecsConnectionLog;
import java.net.SocketAddress;
import java.time.LocalDateTime;

public final class HsmsGsConnectionLog
extends AbstractSecsConnectionLog {
    private static final long serialVersionUID = -2682204681452230464L;
    private static final String subjectAccepted = "HSMS-GS Accepted";
    private static final String subjectTryConnect = "HSMS-GS Try-Connect";
    private static final String subjectConnected = "HSMS-GS Connected";
    private static final String subjectChannelClosed = "HSMS-GS Channel-Closed";

    private HsmsGsConnectionLog(CharSequence subject, LocalDateTime timestamp, SocketAddress local, SocketAddress remote, boolean isConnecting) {
        super(subject, timestamp, local, remote, isConnecting);
    }

    private HsmsGsConnectionLog(CharSequence subject, SocketAddress local, SocketAddress remote, boolean isConnecting) {
        super(subject, local, remote, isConnecting);
    }

    public static HsmsGsConnectionLog accepted(SocketAddress local, SocketAddress remote) {
        return new HsmsGsConnectionLog(subjectAccepted, local, remote, true);
    }

    public static HsmsGsConnectionLog accepted(SocketAddress local, SocketAddress remote, LocalDateTime timestamp) {
        return new HsmsGsConnectionLog(subjectAccepted, timestamp, local, remote, true);
    }

    public static HsmsGsConnectionLog tryConnect(SocketAddress remote) {
        return new HsmsGsConnectionLog(subjectTryConnect, null, remote, false);
    }

    public static HsmsGsConnectionLog tryConnect(SocketAddress remote, LocalDateTime timestamp) {
        return new HsmsGsConnectionLog(subjectTryConnect, timestamp, null, remote, false);
    }

    public static HsmsGsConnectionLog connected(SocketAddress local, SocketAddress remote) {
        return new HsmsGsConnectionLog(subjectConnected, local, remote, true);
    }

    public static HsmsGsConnectionLog connected(SocketAddress local, SocketAddress remote, LocalDateTime timestamp) {
        return new HsmsGsConnectionLog(subjectConnected, timestamp, local, remote, true);
    }

    public static HsmsGsConnectionLog closed(SocketAddress local, SocketAddress remote) {
        return new HsmsGsConnectionLog(subjectChannelClosed, local, remote, false);
    }

    public static HsmsGsConnectionLog closed(SocketAddress local, SocketAddress remote, LocalDateTime timestamp) {
        return new HsmsGsConnectionLog(subjectChannelClosed, timestamp, local, remote, false);
    }
}

