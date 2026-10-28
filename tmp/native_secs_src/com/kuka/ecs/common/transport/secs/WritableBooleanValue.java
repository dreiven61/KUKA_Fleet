/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.WritableValue;

public interface WritableBooleanValue
extends WritableValue<Boolean> {
    @Override
    public void set(boolean var1);
}

