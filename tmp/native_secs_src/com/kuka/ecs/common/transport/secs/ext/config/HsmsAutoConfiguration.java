/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.commons.collections.CollectionUtils
 *  org.apache.commons.collections.MapUtils
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.aop.framework.AopProxyUtils
 *  org.springframework.beans.BeansException
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 *  org.springframework.boot.context.properties.EnableConfigurationProperties
 *  org.springframework.context.ApplicationContext
 *  org.springframework.context.ApplicationContextAware
 *  org.springframework.context.annotation.Bean
 *  org.springframework.context.annotation.Configuration
 *  org.springframework.context.annotation.Import
 *  org.springframework.util.StringUtils
 */
package com.kuka.ecs.common.transport.secs.ext.config;

import com.kuka.ecs.common.transport.secs.SecsLogListener;
import com.kuka.ecs.common.transport.secs.ext.annotation.SecsMsgListener;
import com.kuka.ecs.common.transport.secs.ext.config.AbstractSecsMsgListener;
import com.kuka.ecs.common.transport.secs.ext.config.HsmsCplexProps;
import com.kuka.ecs.common.transport.secs.ext.config.HsmsProps;
import com.kuka.ecs.common.transport.secs.ext.config.SecsConfigurationRegistrar;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.utils.SecsUtils;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

@ConditionalOnProperty(prefix="additions.devices.hsms", name={"enabled"}, havingValue="true", matchIfMissing=false)
@Configuration(proxyBeanMethods=true)
@Import(value={SecsConfigurationRegistrar.class})
@EnableConfigurationProperties(value={HsmsCplexProps.class})
public class HsmsAutoConfiguration
implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(HsmsAutoConfiguration.class);
    @Autowired
    private HsmsCplexProps hsmsCplexProps;
    @Autowired(required=false)
    private List<AbstractSecsMsgListener> list;

    @ConditionalOnExpression(value="${!hsmsCplexProps.mutiple:true}")
    @Bean(value={"hsmsSsCommunicator"})
    public HsmsSsCommunicator hsmsSsCommunicator() {
        HsmsProps hsmsProps = this.hsmsCplexProps.getProps();
        HsmsSsCommunicator comm = this.getCommunicator(hsmsProps, null);
        Runnable run = () -> {
            try {
                comm.open();
            }
            catch (Exception e) {
                log.debug("open_error==>:");
            }
        };
        Thread thread = new Thread(run, "hsms-commonunite");
        thread.setDaemon(true);
        thread.start();
        this.setList(this.list, comm);
        return comm;
    }

    private void setList(List<AbstractSecsMsgListener> list2, HsmsSsCommunicator comm) {
        if (CollectionUtils.isEmpty(list2)) {
            return;
        }
        list2.parallelStream().forEach(lsn -> AbstractSecsMsgListener.setHsmsSsCommunicator(comm));
    }

    private void setList(List<AbstractSecsMsgListener> list2, Map<String, HsmsSsCommunicator> comm) {
        if (CollectionUtils.isEmpty(list2)) {
            return;
        }
        list2.parallelStream().forEach(lsn -> AbstractSecsMsgListener.setHsmsSsCommunicators(comm));
    }

    @ConditionalOnProperty(prefix="additions.devices.hsms", name={"mutiple"}, havingValue="true", matchIfMissing=false)
    @Bean(value={"hsmsSsCommunicators"})
    public Map<String, HsmsSsCommunicator> hsmsSsCommunicators() {
        Map<String, HsmsProps> map = this.hsmsCplexProps.getSessions();
        ConcurrentHashMap<String, HsmsSsCommunicator> res = new ConcurrentHashMap<String, HsmsSsCommunicator>();
        if (MapUtils.isNotEmpty(map)) {
            map.entrySet().parallelStream().forEach(item -> {
                HsmsSsCommunicator comm = this.getCommunicator((HsmsProps)item.getValue(), (String)item.getKey());
                res.put((String)item.getKey(), comm);
            });
        }
        this.setList(this.list, res);
        return res;
    }

    private HsmsSsCommunicator getCommunicator(HsmsProps hsmsProps, String key) {
        HsmsSsCommunicatorConfig config = SecsUtils.getConfig(hsmsProps);
        if (HsmsConnectionMode.PASSIVE.name().equals(hsmsProps.getProtocol())) {
            config.rebindIfPassive(hsmsProps.getRebindIfPassive().floatValue());
        }
        HsmsSsCommunicator comm = HsmsSsCommunicator.newInstance(config);
        if (hsmsProps.getLogDetail().booleanValue()) {
            comm.addSecsLogListener(detail -> {
                if (detail instanceof HsmsMessage && SecsUtils.dataMessage((HsmsMessage)((Object)detail))) {
                    log.debug("secs2_logs_detail==>:");
                    log.debug(Objects.toString(detail));
                }
            });
        }
        if (CollectionUtils.isNotEmpty(this.list)) {
            this.list.parallelStream().forEach(lsn -> {
                try {
                    Class ultimateTargetClass = AopProxyUtils.ultimateTargetClass((Object)lsn);
                    SecsMsgListener annotation = ultimateTargetClass.getAnnotation(SecsMsgListener.class);
                    if (Objects.nonNull(annotation) && StringUtils.isEmpty((Object)annotation.id()) && annotation.global()) {
                        comm.addSecsLogListener((SecsLogListener)lsn);
                    } else if (Objects.nonNull(annotation) && !StringUtils.isEmpty((Object)key) && key.equalsIgnoreCase(annotation.id())) {
                        comm.addSecsLogListener((SecsLogListener)lsn);
                    } else {
                        comm.addSecsLogListener((SecsLogListener)lsn);
                    }
                }
                catch (Exception e) {
                    log.error("error:{}", (Throwable)e);
                }
            });
        }
        comm.addSecsCommunicatableStateChangeListener(state -> SecsUtils.getConnectionStatusFactory().put(comm, state));
        return comm;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SecsUtils.initSession(applicationContext);
    }
}

