/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.boot.context.properties.ConfigurationProperties
 */
package com.kuka.ecs.common.transport.secs.ext.config;

import com.kuka.ecs.common.transport.secs.ext.config.HsmsProps;
import java.beans.ConstructorProperties;
import java.io.Serializable;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(ignoreInvalidFields=true, prefix="additions.devices.hsms")
public class HsmsCplexProps
implements Serializable {
    private static final long serialVersionUID = 1L;
    private Boolean mutiple;
    private Boolean enabled;
    private HsmsProps props;
    private Map<String, HsmsProps> sessions;

    private static Boolean $default$mutiple() {
        return false;
    }

    public static HsmsCplexPropsBuilder builder() {
        return new HsmsCplexPropsBuilder();
    }

    @ConstructorProperties(value={"mutiple", "enabled", "props", "sessions"})
    public HsmsCplexProps(Boolean mutiple, Boolean enabled, HsmsProps props, Map<String, HsmsProps> sessions) {
        this.mutiple = mutiple;
        this.enabled = enabled;
        this.props = props;
        this.sessions = sessions;
    }

    public HsmsCplexProps() {
        this.mutiple = HsmsCplexProps.$default$mutiple();
    }

    public Boolean getMutiple() {
        return this.mutiple;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public HsmsProps getProps() {
        return this.props;
    }

    public Map<String, HsmsProps> getSessions() {
        return this.sessions;
    }

    public void setMutiple(Boolean mutiple) {
        this.mutiple = mutiple;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setProps(HsmsProps props) {
        this.props = props;
    }

    public void setSessions(Map<String, HsmsProps> sessions) {
        this.sessions = sessions;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof HsmsCplexProps)) {
            return false;
        }
        HsmsCplexProps other = (HsmsCplexProps)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Boolean this$mutiple = this.getMutiple();
        Boolean other$mutiple = other.getMutiple();
        if (this$mutiple == null ? other$mutiple != null : !((Object)this$mutiple).equals(other$mutiple)) {
            return false;
        }
        Boolean this$enabled = this.getEnabled();
        Boolean other$enabled = other.getEnabled();
        if (this$enabled == null ? other$enabled != null : !((Object)this$enabled).equals(other$enabled)) {
            return false;
        }
        HsmsProps this$props = this.getProps();
        HsmsProps other$props = other.getProps();
        if (this$props == null ? other$props != null : !((Object)this$props).equals(other$props)) {
            return false;
        }
        Map<String, HsmsProps> this$sessions = this.getSessions();
        Map<String, HsmsProps> other$sessions = other.getSessions();
        return !(this$sessions == null ? other$sessions != null : !((Object)this$sessions).equals(other$sessions));
    }

    protected boolean canEqual(Object other) {
        return other instanceof HsmsCplexProps;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Boolean $mutiple = this.getMutiple();
        result = result * 59 + ($mutiple == null ? 43 : ((Object)$mutiple).hashCode());
        Boolean $enabled = this.getEnabled();
        result = result * 59 + ($enabled == null ? 43 : ((Object)$enabled).hashCode());
        HsmsProps $props = this.getProps();
        result = result * 59 + ($props == null ? 43 : ((Object)$props).hashCode());
        Map<String, HsmsProps> $sessions = this.getSessions();
        result = result * 59 + ($sessions == null ? 43 : ((Object)$sessions).hashCode());
        return result;
    }

    public String toString() {
        return "HsmsCplexProps(mutiple=" + this.getMutiple() + ", enabled=" + this.getEnabled() + ", props=" + this.getProps() + ", sessions=" + this.getSessions() + ")";
    }

    public static class HsmsCplexPropsBuilder {
        private boolean mutiple$set;
        private Boolean mutiple$value;
        private Boolean enabled;
        private HsmsProps props;
        private Map<String, HsmsProps> sessions;

        HsmsCplexPropsBuilder() {
        }

        public HsmsCplexPropsBuilder mutiple(Boolean mutiple) {
            this.mutiple$value = mutiple;
            this.mutiple$set = true;
            return this;
        }

        public HsmsCplexPropsBuilder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public HsmsCplexPropsBuilder props(HsmsProps props) {
            this.props = props;
            return this;
        }

        public HsmsCplexPropsBuilder sessions(Map<String, HsmsProps> sessions) {
            this.sessions = sessions;
            return this;
        }

        public HsmsCplexProps build() {
            Boolean mutiple$value = this.mutiple$value;
            if (!this.mutiple$set) {
                mutiple$value = HsmsCplexProps.$default$mutiple();
            }
            return new HsmsCplexProps(mutiple$value, this.enabled, this.props, this.sessions);
        }

        public String toString() {
            return "HsmsCplexProps.HsmsCplexPropsBuilder(mutiple$value=" + this.mutiple$value + ", enabled=" + this.enabled + ", props=" + this.props + ", sessions=" + this.sessions + ")";
        }
    }
}

