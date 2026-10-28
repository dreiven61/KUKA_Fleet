/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import java.util.EventListener;

public interface SecsCommunicatableStateChangeListener
extends EventListener {
    public void changed(boolean var1);
}

