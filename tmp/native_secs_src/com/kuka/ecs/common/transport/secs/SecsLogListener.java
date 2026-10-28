/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.SecsLog;
import java.util.EventListener;

public interface SecsLogListener
extends EventListener {
    public void received(SecsLog var1);
}

