/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;

public interface SecsMessage {
    public int getStream();

    public int getFunction();

    public boolean wbit();

    public Secs2 secs2();

    public int deviceId();

    public int sessionId();

    public byte[] header10Bytes();

    public String toJson();
}

