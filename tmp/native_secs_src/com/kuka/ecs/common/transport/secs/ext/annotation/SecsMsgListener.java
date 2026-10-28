/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.core.annotation.AliasFor
 *  org.springframework.stereotype.Component
 */
package com.kuka.ecs.common.transport.secs.ext.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

@Component
@Target(value={ElementType.TYPE})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface SecsMsgListener {
    public String id() default "";

    public boolean global() default true;

    @AliasFor(annotation=Component.class)
    public String value() default "";
}

