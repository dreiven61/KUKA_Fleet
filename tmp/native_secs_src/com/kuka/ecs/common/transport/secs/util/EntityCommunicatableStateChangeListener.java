/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.util;

import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import java.util.EventListener;

public interface EntityCommunicatableStateChangeListener
extends EventListener {
    public void changed(boolean var1, SecsCommunicator var2);
}

