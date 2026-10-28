/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.OpenAndCloseable;
import com.kuka.ecs.common.transport.secs.SecsCommunicatableStateChangeBiListener;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsLogListener;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsMessageReceiveBiListener;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessagePassThroughListener;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSession;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsActiveCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsPassiveCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsUnknownSessionIdException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.sml.SmlMessage;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public interface HsmsGsCommunicator
extends OpenAndCloseable {
    public static HsmsGsCommunicator newInstance(HsmsGsCommunicatorConfig config) {
        HsmsConnectionMode mode = config.connectionMode().get();
        switch (mode) {
            case PASSIVE: {
                return new AbstractHsmsGsPassiveCommunicator(config){};
            }
            case ACTIVE: {
                return new AbstractHsmsGsActiveCommunicator(config){};
            }
        }
        throw new IllegalStateException("undefined connecton-mode: " + (Object)((Object)mode));
    }

    public static HsmsGsCommunicator open(HsmsGsCommunicatorConfig config) throws IOException {
        HsmsGsCommunicator inst = HsmsGsCommunicator.newInstance(config);
        try {
            inst.open();
        }
        catch (IOException e) {
            try {
                inst.close();
            }
            catch (IOException iOException) {
                // empty catch block
            }
            throw e;
        }
        return inst;
    }

    public Set<HsmsSession> getSessions();

    public HsmsSession getSession(int var1) throws HsmsGsUnknownSessionIdException;

    public boolean existSession(int var1);

    public Optional<SecsMessage> send(int var1, int var2, int var3, boolean var4) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> send(int var1, int var2, int var3, boolean var4, Secs2 var5) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> send(int var1, SecsMessage var2, int var3, int var4, boolean var5) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> send(int var1, SecsMessage var2, int var3, int var4, boolean var5, Secs2 var6) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> send(int var1, SmlMessage var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<? extends SecsMessage> send(int var1, SecsMessage var2, SmlMessage var3) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public boolean addSecsMessageReceiveListener(SecsMessageReceiveBiListener var1);

    public boolean removeSecsMessageReceiveListener(SecsMessageReceiveBiListener var1);

    public boolean addSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeBiListener var1);

    public boolean removeSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeBiListener var1);

    public boolean addSecsLogListener(SecsLogListener var1);

    public boolean removeSecsLogListener(SecsLogListener var1);

    public boolean addTrySendHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public boolean removeTrySendHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public boolean addSendedHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public boolean removeSendedHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public boolean addReceiveHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public boolean removeReceiveHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);
}

