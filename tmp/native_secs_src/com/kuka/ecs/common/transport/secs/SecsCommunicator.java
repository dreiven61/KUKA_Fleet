/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.OpenAndCloseable;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.SecsCommunicatableStateChangeBiListener;
import com.kuka.ecs.common.transport.secs.SecsCommunicatableStateChangeListener;
import com.kuka.ecs.common.transport.secs.SecsLogListener;
import com.kuka.ecs.common.transport.secs.SecsMessagePassThroughListener;
import com.kuka.ecs.common.transport.secs.SecsMessageReceiveBiListener;
import com.kuka.ecs.common.transport.secs.SecsMessageReceiveListener;
import com.kuka.ecs.common.transport.secs.SecsMessageSendable;
import com.kuka.ecs.common.transport.secs.gem.Gem;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface SecsCommunicator
extends OpenAndCloseable,
SecsMessageSendable {
    public Gem gem();

    public boolean isCommunicatable();

    public void waitUntilCommunicatable() throws InterruptedException;

    public void waitUntilCommunicatable(long var1, TimeUnit var3) throws InterruptedException, TimeoutException;

    public void waitUntilCommunicatable(ReadOnlyTimeProperty var1) throws InterruptedException, TimeoutException;

    public void waitUntilNotCommunicatable() throws InterruptedException;

    public void waitUntilNotCommunicatable(long var1, TimeUnit var3) throws InterruptedException, TimeoutException;

    public void waitUntilNotCommunicatable(ReadOnlyTimeProperty var1) throws InterruptedException, TimeoutException;

    public void openAndWaitUntilCommunicatable() throws IOException, InterruptedException;

    public void openAndWaitUntilCommunicatable(long var1, TimeUnit var3) throws IOException, InterruptedException, TimeoutException;

    public void openAndWaitUntilCommunicatable(ReadOnlyTimeProperty var1) throws IOException, InterruptedException, TimeoutException;

    public boolean isEquip();

    public int deviceId();

    public int sessionId();

    public boolean addSecsMessageReceiveListener(SecsMessageReceiveListener var1);

    public boolean removeSecsMessageReceiveListener(SecsMessageReceiveListener var1);

    public boolean addSecsMessageReceiveListener(SecsMessageReceiveBiListener var1);

    public boolean removeSecsMessageReceiveListener(SecsMessageReceiveBiListener var1);

    public boolean addSecsLogListener(SecsLogListener var1);

    public boolean removeSecsLogListener(SecsLogListener var1);

    public boolean addSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeListener var1);

    public boolean removeSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeListener var1);

    public boolean addSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeBiListener var1);

    public boolean removeSecsCommunicatableStateChangeListener(SecsCommunicatableStateChangeBiListener var1);

    public boolean addTrySendMessagePassThroughListener(SecsMessagePassThroughListener var1);

    public boolean removeTrySendMessagePassThroughListener(SecsMessagePassThroughListener var1);

    public boolean addSendedMessagePassThroughListener(SecsMessagePassThroughListener var1);

    public boolean removeSendedMessagePassThroughListener(SecsMessagePassThroughListener var1);

    public boolean addReceiveMessagePassThroughListener(SecsMessagePassThroughListener var1);

    public boolean removeReceiveMessagePassThroughListener(SecsMessagePassThroughListener var1);
}

