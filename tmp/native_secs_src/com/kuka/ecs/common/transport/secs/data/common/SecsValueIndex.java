/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.data.common;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(value=RetentionPolicy.RUNTIME)
public @interface SecsValueIndex {
    public int[] value();
}

