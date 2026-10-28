/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.util;

import com.kuka.ecs.common.transport.secs.OpenAndCloseable;
import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsMessageSendable;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.gem.Gem;
import com.kuka.ecs.common.transport.secs.util.AbstractSecsCommunicatorEntity;
import com.kuka.ecs.common.transport.secs.util.EntityCommunicatableStateChangeListener;
import com.kuka.ecs.common.transport.secs.util.EntityMessageReceiveListener;
import java.io.IOException;
import java.util.Optional;

public interface SecsCommunicatorEntity
extends OpenAndCloseable,
SecsMessageSendable {
    @Override
    public void open() throws IOException;

    @Override
    public void close() throws IOException;

    public void setReplySxF0(boolean var1);

    public void setSendS9Fy(boolean var1);

    public boolean addCommunicatableStateChangeListener(EntityCommunicatableStateChangeListener var1);

    public boolean removeCommunicatableStateChangeListener(EntityCommunicatableStateChangeListener var1);

    public boolean addMessageReceiveListener(int var1, int var2, EntityMessageReceiveListener var3);

    public boolean removeMessageReceiveListener(int var1, int var2);

    public Optional<SecsMessage> sendS9F9(SecsWaitReplyMessageException var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Gem gem();

    public static SecsCommunicatorEntity newInstance(SecsCommunicator communicator) {
        return new AbstractSecsCommunicatorEntity(communicator){};
    }
}

