/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.context.annotation.ComponentScan
 *  org.springframework.context.annotation.ComponentScan$Filter
 *  org.springframework.context.annotation.FilterType
 *  org.springframework.context.annotation.Import
 */
package com.kuka.ecs.common.transport.secs.ext.annotation;

import com.kuka.ecs.common.transport.secs.ext.config.AbstractSecsMsgListener;
import com.kuka.ecs.common.transport.secs.ext.config.SecsConfigurationRegistrar;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@ComponentScan(includeFilters={@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE, classes={AbstractSecsMsgListener.class})})
@Target(value={ElementType.TYPE})
@Retention(value=RetentionPolicy.RUNTIME)
@Documented
@Import(value={SecsConfigurationRegistrar.class})
public @interface EnableSecs {
}

