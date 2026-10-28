/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import java.util.EventListener;

public interface SecsMessageReceiveListener
extends EventListener {
    public void received(SecsMessage var1);
}

