/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import java.util.EventListener;

public interface HsmsMessageReceiveListener
extends EventListener {
    public void received(AbstractHsmsMessage var1);
}

