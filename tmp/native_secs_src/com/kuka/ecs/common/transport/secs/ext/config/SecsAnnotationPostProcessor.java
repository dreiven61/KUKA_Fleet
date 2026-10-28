/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.commons.collections.CollectionUtils
 *  org.apache.commons.collections.map.MultiKeyMap
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.aop.framework.AopProxyUtils
 *  org.springframework.beans.BeansException
 *  org.springframework.beans.factory.config.BeanPostProcessor
 *  org.springframework.lang.Nullable
 *  org.springframework.util.Assert
 *  org.springframework.util.ReflectionUtils
 */
package com.kuka.ecs.common.transport.secs.ext.config;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.ext.annotation.Sf;
import com.kuka.ecs.common.transport.secs.ext.config.AbstractSecsMsgListener;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.MultiKeyMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class SecsAnnotationPostProcessor
implements BeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(SecsAnnotationPostProcessor.class);

    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Field handlers;
        Class ultimateTargetClass = AopProxyUtils.ultimateTargetClass((Object)bean);
        if (AbstractSecsMsgListener.class.isAssignableFrom(ultimateTargetClass) && Objects.nonNull(handlers = ReflectionUtils.findField(bean.getClass(), (String)"HANDLERS"))) {
            this.wrapperHandler(bean, handlers);
        }
        return bean;
    }

    private void wrapperHandler(Object bean, Field handlers) {
        handlers.setAccessible(true);
        MultiKeyMap value = (MultiKeyMap)ReflectionUtils.getField((Field)handlers, (Object)bean);
        Set<Method> methods = SecsAnnotationPostProcessor.findMethod(bean.getClass(), SecsMessage.class);
        if (CollectionUtils.isNotEmpty(methods)) {
            methods.forEach(method -> {
                Sf sf = method.getAnnotation(Sf.class);
                if (Objects.nonNull(method) && Objects.nonNull(sf)) {
                    Consumer<SecsMessage> consumer = i -> {
                        try {
                            method.setAccessible(true);
                            method.invoke(bean, i);
                        }
                        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                            log.error("invocke_error:{}", (Throwable)e);
                        }
                    };
                    value.put((Object)sf.s(), (Object)sf.f(), consumer);
                }
            });
        }
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Nullable
    public static Set<Method> findMethod(Class<?> clazz, Class<?> ... paramTypes) {
        Assert.notNull(clazz, (String)"Class must not be null");
        HashSet<Method> methods = new HashSet<Method>();
        for (Class<?> searchType = clazz; searchType != null; searchType = searchType.getSuperclass()) {
            List<Method> preMethods = Arrays.asList(ReflectionUtils.getAllDeclaredMethods(clazz));
            List list = preMethods.parallelStream().filter(method -> {
                boolean b = !method.getName().equals("received");
                boolean hasSameParams = SecsAnnotationPostProcessor.hasSameParams(method, paramTypes);
                boolean contains = methods.contains(method);
                return b && hasSameParams && !contains;
            }).collect(Collectors.toList());
            methods.addAll(list);
        }
        return methods;
    }

    private static boolean hasSameParams(Method method, Class<?>[] paramTypes) {
        return paramTypes.length == method.getParameterCount() && Arrays.equals(paramTypes, method.getParameterTypes());
    }
}

