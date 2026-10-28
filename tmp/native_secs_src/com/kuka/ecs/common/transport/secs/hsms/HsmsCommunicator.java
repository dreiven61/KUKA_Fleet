/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicateStateChangeListener;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessagePassThroughListener;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import java.util.Optional;

public interface HsmsCommunicator
extends SecsCommunicator {
    public boolean addHsmsCommunicateStateChangeListener(HsmsCommunicateStateChangeListener var1);

    public boolean removeHsmsCommunicateStateChangeListener(HsmsCommunicateStateChangeListener var1);

    public boolean linktest() throws InterruptedException;

    public boolean addTrySendHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public boolean removeTrySendHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public boolean addSendedHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public boolean removeSendedHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public boolean addReceiveHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public boolean removeReceiveHsmsMessagePassThroughListener(HsmsMessagePassThroughListener var1);

    public Optional<HsmsMessage> send(HsmsMessage var1) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;
}

