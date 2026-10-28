/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.util;

import com.kuka.ecs.common.transport.secs.SecsCommunicatableStateChangeBiListener;
import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import com.kuka.ecs.common.transport.secs.SecsMessageReceiveBiListener;
import com.kuka.ecs.common.transport.secs.util.AbstractEntityEventAdapter;
import com.kuka.ecs.common.transport.secs.util.EntityCommunicatableStateChangeListener;
import com.kuka.ecs.common.transport.secs.util.EntityMessageReceiveListener;

public interface EntityEventAdapter
extends SecsCommunicatableStateChangeBiListener,
SecsMessageReceiveBiListener {
    public void setReplySxF0(boolean var1);

    public void setReplyS9Fy(boolean var1);

    public boolean addCommunicatableStateChangeListener(EntityCommunicatableStateChangeListener var1);

    public boolean removeCommunicatableStateChangeListener(EntityCommunicatableStateChangeListener var1);

    public boolean addMessageReceiveListener(int var1, int var2, EntityMessageReceiveListener var3);

    public boolean removeMessageReceiveListener(int var1, int var2);

    public void adaptToSecsCommunicator(SecsCommunicator var1);

    public static EntityEventAdapter newInstance() {
        return new AbstractEntityEventAdapter(){};
    }
}

