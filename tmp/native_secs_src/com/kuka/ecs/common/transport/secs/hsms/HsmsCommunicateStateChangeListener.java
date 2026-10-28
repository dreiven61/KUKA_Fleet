/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicateState;
import java.util.EventListener;

public interface HsmsCommunicateStateChangeListener
extends EventListener {
    public void changed(HsmsCommunicateState var1);
}

