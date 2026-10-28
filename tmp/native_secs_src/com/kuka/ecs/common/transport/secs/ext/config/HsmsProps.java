/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.ext.config;

import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import java.beans.ConstructorProperties;
import java.io.Serializable;

public class HsmsProps
implements Serializable {
    private static final long serialVersionUID = 1L;
    private String host;
    private int port;
    private String protocol;
    private String logSubjectHeader;
    private Boolean isEquip;
    private Boolean logDetail;
    private float linktest;
    private Float t3;
    private Float t5;
    private Float t6;
    private Float t7;
    private Float t8;
    private Float rebindIfPassive;

    private static String $default$host() {
        return "0.0.0.0";
    }

    private static int $default$port() {
        return 10068;
    }

    private static String $default$protocol() {
        return HsmsConnectionMode.PASSIVE.name();
    }

    private static String $default$logSubjectHeader() {
        return "";
    }

    private static Boolean $default$isEquip() {
        return false;
    }

    private static Boolean $default$logDetail() {
        return false;
    }

    private static float $default$linktest() {
        return 6.0f;
    }

    private static Float $default$t3() {
        return Float.valueOf(45.0f);
    }

    private static Float $default$t5() {
        return Float.valueOf(10.0f);
    }

    private static Float $default$t6() {
        return Float.valueOf(5.0f);
    }

    private static Float $default$t7() {
        return Float.valueOf(10.0f);
    }

    private static Float $default$t8() {
        return Float.valueOf(5.0f);
    }

    private static Float $default$rebindIfPassive() {
        return Float.valueOf(6.0f);
    }

    public static HsmsPropsBuilder builder() {
        return new HsmsPropsBuilder();
    }

    @ConstructorProperties(value={"host", "port", "protocol", "logSubjectHeader", "isEquip", "logDetail", "linktest", "t3", "t5", "t6", "t7", "t8", "rebindIfPassive"})
    public HsmsProps(String host, int port, String protocol, String logSubjectHeader, Boolean isEquip, Boolean logDetail, float linktest, Float t3, Float t5, Float t6, Float t7, Float t8, Float rebindIfPassive) {
        this.host = host;
        this.port = port;
        this.protocol = protocol;
        this.logSubjectHeader = logSubjectHeader;
        this.isEquip = isEquip;
        this.logDetail = logDetail;
        this.linktest = linktest;
        this.t3 = t3;
        this.t5 = t5;
        this.t6 = t6;
        this.t7 = t7;
        this.t8 = t8;
        this.rebindIfPassive = rebindIfPassive;
    }

    public HsmsProps() {
        this.host = HsmsProps.$default$host();
        this.port = HsmsProps.$default$port();
        this.protocol = HsmsProps.$default$protocol();
        this.logSubjectHeader = HsmsProps.$default$logSubjectHeader();
        this.isEquip = HsmsProps.$default$isEquip();
        this.logDetail = HsmsProps.$default$logDetail();
        this.linktest = HsmsProps.$default$linktest();
        this.t3 = HsmsProps.$default$t3();
        this.t5 = HsmsProps.$default$t5();
        this.t6 = HsmsProps.$default$t6();
        this.t7 = HsmsProps.$default$t7();
        this.t8 = HsmsProps.$default$t8();
        this.rebindIfPassive = HsmsProps.$default$rebindIfPassive();
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public String getLogSubjectHeader() {
        return this.logSubjectHeader;
    }

    public Boolean getIsEquip() {
        return this.isEquip;
    }

    public Boolean getLogDetail() {
        return this.logDetail;
    }

    public float getLinktest() {
        return this.linktest;
    }

    public Float getT3() {
        return this.t3;
    }

    public Float getT5() {
        return this.t5;
    }

    public Float getT6() {
        return this.t6;
    }

    public Float getT7() {
        return this.t7;
    }

    public Float getT8() {
        return this.t8;
    }

    public Float getRebindIfPassive() {
        return this.rebindIfPassive;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void setLogSubjectHeader(String logSubjectHeader) {
        this.logSubjectHeader = logSubjectHeader;
    }

    public void setIsEquip(Boolean isEquip) {
        this.isEquip = isEquip;
    }

    public void setLogDetail(Boolean logDetail) {
        this.logDetail = logDetail;
    }

    public void setLinktest(float linktest) {
        this.linktest = linktest;
    }

    public void setT3(Float t3) {
        this.t3 = t3;
    }

    public void setT5(Float t5) {
        this.t5 = t5;
    }

    public void setT6(Float t6) {
        this.t6 = t6;
    }

    public void setT7(Float t7) {
        this.t7 = t7;
    }

    public void setT8(Float t8) {
        this.t8 = t8;
    }

    public void setRebindIfPassive(Float rebindIfPassive) {
        this.rebindIfPassive = rebindIfPassive;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof HsmsProps)) {
            return false;
        }
        HsmsProps other = (HsmsProps)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (this.getPort() != other.getPort()) {
            return false;
        }
        if (Float.compare(this.getLinktest(), other.getLinktest()) != 0) {
            return false;
        }
        Boolean this$isEquip = this.getIsEquip();
        Boolean other$isEquip = other.getIsEquip();
        if (this$isEquip == null ? other$isEquip != null : !((Object)this$isEquip).equals(other$isEquip)) {
            return false;
        }
        Boolean this$logDetail = this.getLogDetail();
        Boolean other$logDetail = other.getLogDetail();
        if (this$logDetail == null ? other$logDetail != null : !((Object)this$logDetail).equals(other$logDetail)) {
            return false;
        }
        Float this$t3 = this.getT3();
        Float other$t3 = other.getT3();
        if (this$t3 == null ? other$t3 != null : !((Object)this$t3).equals(other$t3)) {
            return false;
        }
        Float this$t5 = this.getT5();
        Float other$t5 = other.getT5();
        if (this$t5 == null ? other$t5 != null : !((Object)this$t5).equals(other$t5)) {
            return false;
        }
        Float this$t6 = this.getT6();
        Float other$t6 = other.getT6();
        if (this$t6 == null ? other$t6 != null : !((Object)this$t6).equals(other$t6)) {
            return false;
        }
        Float this$t7 = this.getT7();
        Float other$t7 = other.getT7();
        if (this$t7 == null ? other$t7 != null : !((Object)this$t7).equals(other$t7)) {
            return false;
        }
        Float this$t8 = this.getT8();
        Float other$t8 = other.getT8();
        if (this$t8 == null ? other$t8 != null : !((Object)this$t8).equals(other$t8)) {
            return false;
        }
        Float this$rebindIfPassive = this.getRebindIfPassive();
        Float other$rebindIfPassive = other.getRebindIfPassive();
        if (this$rebindIfPassive == null ? other$rebindIfPassive != null : !((Object)this$rebindIfPassive).equals(other$rebindIfPassive)) {
            return false;
        }
        String this$host = this.getHost();
        String other$host = other.getHost();
        if (this$host == null ? other$host != null : !this$host.equals(other$host)) {
            return false;
        }
        String this$protocol = this.getProtocol();
        String other$protocol = other.getProtocol();
        if (this$protocol == null ? other$protocol != null : !this$protocol.equals(other$protocol)) {
            return false;
        }
        String this$logSubjectHeader = this.getLogSubjectHeader();
        String other$logSubjectHeader = other.getLogSubjectHeader();
        return !(this$logSubjectHeader == null ? other$logSubjectHeader != null : !this$logSubjectHeader.equals(other$logSubjectHeader));
    }

    protected boolean canEqual(Object other) {
        return other instanceof HsmsProps;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        result = result * 59 + this.getPort();
        result = result * 59 + Float.floatToIntBits(this.getLinktest());
        Boolean $isEquip = this.getIsEquip();
        result = result * 59 + ($isEquip == null ? 43 : ((Object)$isEquip).hashCode());
        Boolean $logDetail = this.getLogDetail();
        result = result * 59 + ($logDetail == null ? 43 : ((Object)$logDetail).hashCode());
        Float $t3 = this.getT3();
        result = result * 59 + ($t3 == null ? 43 : ((Object)$t3).hashCode());
        Float $t5 = this.getT5();
        result = result * 59 + ($t5 == null ? 43 : ((Object)$t5).hashCode());
        Float $t6 = this.getT6();
        result = result * 59 + ($t6 == null ? 43 : ((Object)$t6).hashCode());
        Float $t7 = this.getT7();
        result = result * 59 + ($t7 == null ? 43 : ((Object)$t7).hashCode());
        Float $t8 = this.getT8();
        result = result * 59 + ($t8 == null ? 43 : ((Object)$t8).hashCode());
        Float $rebindIfPassive = this.getRebindIfPassive();
        result = result * 59 + ($rebindIfPassive == null ? 43 : ((Object)$rebindIfPassive).hashCode());
        String $host = this.getHost();
        result = result * 59 + ($host == null ? 43 : $host.hashCode());
        String $protocol = this.getProtocol();
        result = result * 59 + ($protocol == null ? 43 : $protocol.hashCode());
        String $logSubjectHeader = this.getLogSubjectHeader();
        result = result * 59 + ($logSubjectHeader == null ? 43 : $logSubjectHeader.hashCode());
        return result;
    }

    public String toString() {
        return "HsmsProps(host=" + this.getHost() + ", port=" + this.getPort() + ", protocol=" + this.getProtocol() + ", logSubjectHeader=" + this.getLogSubjectHeader() + ", isEquip=" + this.getIsEquip() + ", logDetail=" + this.getLogDetail() + ", linktest=" + this.getLinktest() + ", t3=" + this.getT3() + ", t5=" + this.getT5() + ", t6=" + this.getT6() + ", t7=" + this.getT7() + ", t8=" + this.getT8() + ", rebindIfPassive=" + this.getRebindIfPassive() + ")";
    }

    public static class HsmsPropsBuilder {
        private boolean host$set;
        private String host$value;
        private boolean port$set;
        private int port$value;
        private boolean protocol$set;
        private String protocol$value;
        private boolean logSubjectHeader$set;
        private String logSubjectHeader$value;
        private boolean isEquip$set;
        private Boolean isEquip$value;
        private boolean logDetail$set;
        private Boolean logDetail$value;
        private boolean linktest$set;
        private float linktest$value;
        private boolean t3$set;
        private Float t3$value;
        private boolean t5$set;
        private Float t5$value;
        private boolean t6$set;
        private Float t6$value;
        private boolean t7$set;
        private Float t7$value;
        private boolean t8$set;
        private Float t8$value;
        private boolean rebindIfPassive$set;
        private Float rebindIfPassive$value;

        HsmsPropsBuilder() {
        }

        public HsmsPropsBuilder host(String host) {
            this.host$value = host;
            this.host$set = true;
            return this;
        }

        public HsmsPropsBuilder port(int port) {
            this.port$value = port;
            this.port$set = true;
            return this;
        }

        public HsmsPropsBuilder protocol(String protocol) {
            this.protocol$value = protocol;
            this.protocol$set = true;
            return this;
        }

        public HsmsPropsBuilder logSubjectHeader(String logSubjectHeader) {
            this.logSubjectHeader$value = logSubjectHeader;
            this.logSubjectHeader$set = true;
            return this;
        }

        public HsmsPropsBuilder isEquip(Boolean isEquip) {
            this.isEquip$value = isEquip;
            this.isEquip$set = true;
            return this;
        }

        public HsmsPropsBuilder logDetail(Boolean logDetail) {
            this.logDetail$value = logDetail;
            this.logDetail$set = true;
            return this;
        }

        public HsmsPropsBuilder linktest(float linktest) {
            this.linktest$value = linktest;
            this.linktest$set = true;
            return this;
        }

        public HsmsPropsBuilder t3(Float t3) {
            this.t3$value = t3;
            this.t3$set = true;
            return this;
        }

        public HsmsPropsBuilder t5(Float t5) {
            this.t5$value = t5;
            this.t5$set = true;
            return this;
        }

        public HsmsPropsBuilder t6(Float t6) {
            this.t6$value = t6;
            this.t6$set = true;
            return this;
        }

        public HsmsPropsBuilder t7(Float t7) {
            this.t7$value = t7;
            this.t7$set = true;
            return this;
        }

        public HsmsPropsBuilder t8(Float t8) {
            this.t8$value = t8;
            this.t8$set = true;
            return this;
        }

        public HsmsPropsBuilder rebindIfPassive(Float rebindIfPassive) {
            this.rebindIfPassive$value = rebindIfPassive;
            this.rebindIfPassive$set = true;
            return this;
        }

        public HsmsProps build() {
            String host$value = this.host$value;
            if (!this.host$set) {
                host$value = HsmsProps.$default$host();
            }
            int port$value = this.port$value;
            if (!this.port$set) {
                port$value = HsmsProps.$default$port();
            }
            String protocol$value = this.protocol$value;
            if (!this.protocol$set) {
                protocol$value = HsmsProps.$default$protocol();
            }
            String logSubjectHeader$value = this.logSubjectHeader$value;
            if (!this.logSubjectHeader$set) {
                logSubjectHeader$value = HsmsProps.$default$logSubjectHeader();
            }
            Boolean isEquip$value = this.isEquip$value;
            if (!this.isEquip$set) {
                isEquip$value = HsmsProps.$default$isEquip();
            }
            Boolean logDetail$value = this.logDetail$value;
            if (!this.logDetail$set) {
                logDetail$value = HsmsProps.$default$logDetail();
            }
            float linktest$value = this.linktest$value;
            if (!this.linktest$set) {
                linktest$value = HsmsProps.$default$linktest();
            }
            Float t3$value = this.t3$value;
            if (!this.t3$set) {
                t3$value = HsmsProps.$default$t3();
            }
            Float t5$value = this.t5$value;
            if (!this.t5$set) {
                t5$value = HsmsProps.$default$t5();
            }
            Float t6$value = this.t6$value;
            if (!this.t6$set) {
                t6$value = HsmsProps.$default$t6();
            }
            Float t7$value = this.t7$value;
            if (!this.t7$set) {
                t7$value = HsmsProps.$default$t7();
            }
            Float t8$value = this.t8$value;
            if (!this.t8$set) {
                t8$value = HsmsProps.$default$t8();
            }
            Float rebindIfPassive$value = this.rebindIfPassive$value;
            if (!this.rebindIfPassive$set) {
                rebindIfPassive$value = HsmsProps.$default$rebindIfPassive();
            }
            return new HsmsProps(host$value, port$value, protocol$value, logSubjectHeader$value, isEquip$value, logDetail$value, linktest$value, t3$value, t5$value, t6$value, t7$value, t8$value, rebindIfPassive$value);
        }

        public String toString() {
            return "HsmsProps.HsmsPropsBuilder(host$value=" + this.host$value + ", port$value=" + this.port$value + ", protocol$value=" + this.protocol$value + ", logSubjectHeader$value=" + this.logSubjectHeader$value + ", isEquip$value=" + this.isEquip$value + ", logDetail$value=" + this.logDetail$value + ", linktest$value=" + this.linktest$value + ", t3$value=" + this.t3$value + ", t5$value=" + this.t5$value + ", t6$value=" + this.t6$value + ", t7$value=" + this.t7$value + ", t8$value=" + this.t8$value + ", rebindIfPassive$value=" + this.rebindIfPassive$value + ")";
        }
    }
}

