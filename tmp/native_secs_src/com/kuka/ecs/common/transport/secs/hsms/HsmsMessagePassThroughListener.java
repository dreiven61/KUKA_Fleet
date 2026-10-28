/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import java.util.EventListener;

public interface HsmsMessagePassThroughListener
extends EventListener {
    public void passThrough(HsmsMessage var1);
}

