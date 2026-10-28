/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicator;

public interface HsmsSession
extends HsmsCommunicator {
    public boolean select() throws InterruptedException;

    public boolean deselect() throws InterruptedException;

    public boolean separate() throws InterruptedException;
}

