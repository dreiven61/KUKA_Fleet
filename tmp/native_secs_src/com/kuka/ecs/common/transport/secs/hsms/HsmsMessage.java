/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;

public interface HsmsMessage
extends SecsMessage {
    public HsmsMessageType messageType();

    public boolean isDataMessage();

    public byte pType();

    public byte sType();
}

