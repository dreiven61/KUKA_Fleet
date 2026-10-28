/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.ext.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value={ElementType.METHOD})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface Sf {
    public int s();

    public int f();
}

