/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;
import java.util.EventListener;

public interface Secs1MessagePassThroughListener
extends EventListener {
    public void passThrough(Secs1Message var1);
}

