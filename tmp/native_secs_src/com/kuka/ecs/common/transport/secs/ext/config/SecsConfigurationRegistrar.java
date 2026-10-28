/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.beans.factory.config.BeanDefinition
 *  org.springframework.beans.factory.support.BeanDefinitionBuilder
 *  org.springframework.beans.factory.support.BeanDefinitionRegistry
 *  org.springframework.context.annotation.ImportBeanDefinitionRegistrar
 *  org.springframework.core.type.AnnotationMetadata
 */
package com.kuka.ecs.common.transport.secs.ext.config;

import com.kuka.ecs.common.transport.secs.ext.config.SecsAnnotationPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

public class SecsConfigurationRegistrar
implements ImportBeanDefinitionRegistrar {
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        String name = SecsAnnotationPostProcessor.class.getName();
        boolean containsBeanDefinition = registry.containsBeanDefinition(name);
        if (!containsBeanDefinition) {
            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(SecsAnnotationPostProcessor.class);
            registry.registerBeanDefinition(name, (BeanDefinition)beanDefinitionBuilder.getBeanDefinition());
        }
    }
}

