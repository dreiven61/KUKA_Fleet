/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Exception;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessagePassThroughListener;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendMessageException;
import com.kuka.ecs.common.transport.secs.secs1.Secs1WaitReplyMessageException;
import java.util.Optional;

public interface Secs1Communicator
extends SecsCommunicator {
    public Optional<Secs1Message> send(Secs1Message var1) throws Secs1SendMessageException, Secs1WaitReplyMessageException, Secs1Exception, InterruptedException;

    public boolean addTrySendSecs1MessagePassThroughListener(Secs1MessagePassThroughListener var1);

    public boolean removeTrySendSecs1MessagePassThroughListener(Secs1MessagePassThroughListener var1);

    public boolean addSendedSecs1MessagePassThroughListener(Secs1MessagePassThroughListener var1);

    public boolean removeSendedSecs1MessagePassThroughListener(Secs1MessagePassThroughListener var1);

    public boolean addReceiveSecs1MessagePassThroughListener(Secs1MessagePassThroughListener var1);

    public boolean removeReceiveSecs1MessagePassThroughListener(Secs1MessagePassThroughListener var1);
}

