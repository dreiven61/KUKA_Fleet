/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.WritableValue;

public interface WritableNumberValue
extends WritableValue<Number> {
    @Override
    public void set(Number var1);

    @Override
    public void set(int var1);

    @Override
    public void set(long var1);

    @Override
    public void set(float var1);

    @Override
    public void set(double var1);
}

