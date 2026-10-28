/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.commons.collections.map.MultiKeyMap
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.core.annotation.Order
 */
package com.kuka.ecs.common.transport.secs.ext.config;

import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsLog;
import com.kuka.ecs.common.transport.secs.SecsLogListener;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.ext.annotation.SecsMsgListener;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsReceiveMessageLog;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicator;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.utils.SecsUtils;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.apache.commons.collections.map.MultiKeyMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

@Order(value=0x7FFFFFFF)
@SecsMsgListener
public abstract class AbstractSecsMsgListener
implements SecsLogListener {
    private static final Logger log = LoggerFactory.getLogger(AbstractSecsMsgListener.class);
    public static final ExecutorService EXEC = Executors.newCachedThreadPool();
    public static final String HANDLERS_STR = "HANDLERS";
    protected static HsmsSsCommunicator hsmsSsCommunicator;
    protected static Map<String, HsmsSsCommunicator> hsmsSsCommunicators;
    private final MultiKeyMap HANDLERS = new MultiKeyMap();

    public boolean logAll() {
        return false;
    }

    @Override
    public void received(SecsLog event) {
        Object object;
        HsmsMessage msg;
        HsmsReceiveMessageLog logInfo;
        Optional<Object> value;
        if (event instanceof HsmsReceiveMessageLog && (value = (logInfo = (HsmsReceiveMessageLog)event).value()).isPresent() && SecsUtils.dataMessage(msg = (HsmsMessage)(object = value.get()))) {
            Consumer consumer = (Consumer)this.HANDLERS.get((Object)msg.getStream(), (Object)msg.getFunction());
            if (Objects.nonNull(consumer)) {
                EXEC.submit(() -> consumer.accept(msg));
            } else if (this.logAll()) {
                log.error("inhandler:{}, received_ignore_data_msg:{}", (Object)this.getClass().getName(), (Object)msg);
            }
        }
    }

    public static Optional<SecsMessage> reply(HsmsSsCommunicator hsmsSsCommunicator, SecsMessage primary, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return hsmsSsCommunicator.send(primary, primary.getStream(), primary.getFunction() + 1, wbit, secs2);
    }

    public static Optional<SecsMessage> reply(SecsMessage primary, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return AbstractSecsMsgListener.reply(hsmsSsCommunicator, primary, wbit, secs2);
    }

    public static Optional<SecsMessage> reply(SecsMessage primary, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return AbstractSecsMsgListener.reply(hsmsSsCommunicator, primary, false, secs2);
    }

    public static Optional<SecsMessage> send(int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return hsmsSsCommunicator.send(strm, func, wbit, secs2);
    }

    public static Optional<SecsMessage> send(SecsMessage primaryMsg, int strm, int func, boolean wbit) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return hsmsSsCommunicator.send(primaryMsg, strm, func, wbit);
    }

    public static final boolean communicatable() {
        boolean communicatable = hsmsSsCommunicator.isCommunicatable();
        return communicatable;
    }

    public static final boolean waitCommunicatable() {
        if (AbstractSecsMsgListener.communicatable()) {
            return true;
        }
        try {
            hsmsSsCommunicator.openAndWaitUntilCommunicatable();
            return true;
        }
        catch (IOException | InterruptedException e) {
            log.error(e.getMessage());
            return false;
        }
    }

    public static void setHsmsSsCommunicator(HsmsSsCommunicator hsmsSsCommunicator) {
        AbstractSecsMsgListener.hsmsSsCommunicator = hsmsSsCommunicator;
    }

    public static void setHsmsSsCommunicators(Map<String, HsmsSsCommunicator> hsmsSsCommunicators) {
        AbstractSecsMsgListener.hsmsSsCommunicators = hsmsSsCommunicators;
    }
}

