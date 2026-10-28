/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.util;

import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import java.util.EventListener;

public interface EntityMessageReceiveListener
extends EventListener {
    public void received(SecsMessage var1, SecsCommunicator var2) throws Secs2Exception, InterruptedException;
}

