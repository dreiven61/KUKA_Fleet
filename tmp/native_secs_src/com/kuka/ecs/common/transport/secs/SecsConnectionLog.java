/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.SecsLog;
import java.net.SocketAddress;

public interface SecsConnectionLog
extends SecsLog {
    public SocketAddress local();

    public SocketAddress remote();

    public boolean isConnecting();
}

