/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.context.ApplicationContext
 */
package com.kuka.ecs.common.transport.secs.utils;

import com.kuka.ecs.common.transport.secs.ByteArrayProperty;
import com.kuka.ecs.common.transport.secs.ext.config.HsmsProps;
import com.kuka.ecs.common.transport.secs.gem.ClockType;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicatorConfig;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public class SecsUtils {
    private static final Logger log = LoggerFactory.getLogger(SecsUtils.class);
    public static final Map<HsmsSsCommunicator, Boolean> CONNECTION_STATUS_FACTORY = new ConcurrentHashMap<HsmsSsCommunicator, Boolean>(16);
    public static final Map<HsmsSsCommunicator, Thread> CONNECTION_THREAD_FACTORY = new ConcurrentHashMap<HsmsSsCommunicator, Thread>(16);
    protected static HsmsSsCommunicator hsmsSsCommunicator;
    protected static Map<String, HsmsSsCommunicator> hsmsSsCommunicators;

    public static final boolean dataMessage(HsmsMessage msg) {
        return HsmsMessageType.get(msg) == HsmsMessageType.DATA;
    }

    public static final ByteArrayProperty changeDeviceid(int deviceId) {
        ByteArrayProperty sessionIdBytes = ByteArrayProperty.newInstance(new byte[]{0, 0});
        int v = deviceId;
        byte[] bs = new byte[]{(byte)(v >> 8), (byte)v};
        sessionIdBytes.set(bs);
        return sessionIdBytes;
    }

    public static int getNumbers(String str) {
        if (str == null || str.length() == 0) {
            return 0;
        }
        String regEx = "[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        String trim = m.replaceAll("").trim();
        Long parseLong = Long.valueOf(trim.substring(0, 8));
        if (parseLong < 0L) {
            parseLong = parseLong * -1L;
        }
        return parseLong.intValue();
    }

    public static final HsmsSsCommunicatorConfig getConfig(HsmsProps props) {
        HsmsSsCommunicatorConfig config = new HsmsSsCommunicatorConfig();
        config.socketAddress(new InetSocketAddress(props.getHost(), props.getPort()));
        config.connectionMode(HsmsConnectionMode.valueOf(props.getProtocol()));
        config.linktest(props.getLinktest());
        config.isEquip(props.getIsEquip());
        config.timeout().t3(props.getT3().floatValue());
        config.timeout().t5(props.getT5().floatValue());
        config.timeout().t6(props.getT6().floatValue());
        config.timeout().t7(props.getT7().floatValue());
        config.timeout().t8(props.getT8().floatValue());
        config.gem().mdln("MDLN-A");
        config.gem().softrev("000001");
        config.gem().clockType(ClockType.A16);
        config.timeout().t5(props.getT5().floatValue());
        config.timeout().t6(props.getT6().floatValue());
        config.timeout().t8(props.getT8().floatValue());
        return config;
    }

    public static boolean isActiveConnection(HsmsSsCommunicator communicator) {
        return CONNECTION_STATUS_FACTORY.getOrDefault(communicator, false);
    }

    public static Map<HsmsSsCommunicator, Boolean> getConnectionStatusFactory() {
        return CONNECTION_STATUS_FACTORY;
    }

    public static Map<HsmsSsCommunicator, Thread> getConnectionThreadFactory() {
        return CONNECTION_THREAD_FACTORY;
    }

    public static Boolean getConnectionStatus(HsmsSsCommunicator communicator) {
        return SecsUtils.getConnectionStatusFactory().get(communicator);
    }

    public static void initSession(ApplicationContext cxt) {
        if (cxt.containsBean("hsmsSsCommunicator")) {
            hsmsSsCommunicator = (HsmsSsCommunicator)cxt.getBean(HsmsSsCommunicator.class);
        }
        if (cxt.containsBean("hsmsSsCommunicators")) {
            hsmsSsCommunicators = (Map)cxt.getBean("hsmsSsCommunicators");
        }
    }

    public static HsmsSsCommunicator getHsmsSsCommunicator() {
        return hsmsSsCommunicator;
    }

    public static Map<String, HsmsSsCommunicator> getHsmsSsCommunicators() {
        return hsmsSsCommunicators;
    }
}

