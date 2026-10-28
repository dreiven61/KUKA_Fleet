/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.WritableValue;

public interface WritableStringValue
extends WritableValue<String> {
    @Override
    public void set(CharSequence var1);
}

