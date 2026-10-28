/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.util;

import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import com.kuka.ecs.common.transport.secs.util.EntityCommunicatableStateChangeListener;
import com.kuka.ecs.common.transport.secs.util.EntityEventAdapter;
import com.kuka.ecs.common.transport.secs.util.EntityMessageReceiveListener;
import com.kuka.ecs.common.transport.secs.util.SxFyAndEntityMessageReceiveListener;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractEntityEventAdapter
implements EntityEventAdapter {
    private final Collection<EntityCommunicatableStateChangeListener> communicatableLstners = new CopyOnWriteArrayList<EntityCommunicatableStateChangeListener>();
    private final Set<SxFyAndEntityMessageReceiveListener> msgRecvLstnrs = new CopyOnWriteArraySet<SxFyAndEntityMessageReceiveListener>();
    private final Object syncReplySxF0 = new Object();
    private boolean isReplySxF0 = false;
    private final Object syncReplyS9Fy = new Object();
    private boolean isReplyS9Fy = false;

    @Override
    public void changed(boolean communicatable, SecsCommunicator communicator) {
        for (EntityCommunicatableStateChangeListener l : this.communicatableLstners) {
            l.changed(communicatable, communicator);
        }
    }

    @Override
    public void received(SecsMessage message, SecsCommunicator communicator) {
        try {
            if (AbstractEntityEventAdapter.equalsDeviceIdOrSessionId(message, communicator)) {
                int strm = message.getStream();
                int func = message.getFunction();
                EntityMessageReceiveListener l = this.msgRecvLstnrs.stream().filter(x -> x.getStream() == strm).filter(x -> x.getFunction() == func).map(x -> x.getMessageReceiveListener()).findAny().orElse(null);
                if (l == null) {
                    boolean wbit = message.wbit();
                    if (this.msgRecvLstnrs.stream().anyMatch(x -> x.getStream() == strm)) {
                        if (wbit && this.isReplySxF0()) {
                            communicator.send(message, strm, 0, false);
                        }
                        if (this.isReplyS9Fy()) {
                            communicator.gem().s9f5(message);
                        }
                    } else {
                        if (wbit && this.isReplySxF0()) {
                            communicator.send(message, 0, 0, false);
                        }
                        if (this.isReplyS9Fy()) {
                            communicator.gem().s9f3(message);
                        }
                    }
                } else {
                    try {
                        l.received(message, communicator);
                    }
                    catch (Secs2Exception e) {
                        communicator.gem().s9f7(message);
                    }
                }
            } else if (this.isReplyS9Fy()) {
                communicator.gem().s9f1(message);
            }
        }
        catch (InterruptedException interruptedException) {
        }
        catch (SecsException secsException) {
            // empty catch block
        }
    }

    private static boolean equalsDeviceIdOrSessionId(SecsMessage message, SecsCommunicator communicator) {
        int msgId = message.deviceId();
        int commId = communicator.deviceId();
        if (msgId < 0) {
            msgId = message.sessionId();
        }
        if (commId < 0) {
            commId = communicator.sessionId();
        }
        if (msgId < 0 || commId < 0) {
            return false;
        }
        return msgId == commId;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void setReplySxF0(boolean doReply) {
        Object object = this.syncReplySxF0;
        synchronized (object) {
            this.isReplySxF0 = doReply;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected boolean isReplySxF0() {
        Object object = this.syncReplySxF0;
        synchronized (object) {
            return this.isReplySxF0;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void setReplyS9Fy(boolean doReply) {
        Object object = this.syncReplyS9Fy;
        synchronized (object) {
            this.isReplyS9Fy = doReply;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected boolean isReplyS9Fy() {
        Object object = this.syncReplyS9Fy;
        synchronized (object) {
            return this.isReplyS9Fy;
        }
    }

    @Override
    public boolean addCommunicatableStateChangeListener(EntityCommunicatableStateChangeListener listener) {
        return this.communicatableLstners.add(listener);
    }

    @Override
    public boolean removeCommunicatableStateChangeListener(EntityCommunicatableStateChangeListener listener) {
        return this.communicatableLstners.remove(listener);
    }

    @Override
    public boolean addMessageReceiveListener(int strm, int func, EntityMessageReceiveListener listener) {
        return this.msgRecvLstnrs.add(new SxFyAndEntityMessageReceiveListener(strm, func, listener));
    }

    @Override
    public boolean removeMessageReceiveListener(int strm, int func) {
        return this.msgRecvLstnrs.removeIf(l -> l.getStream() == strm && l.getFunction() == func);
    }

    @Override
    public void adaptToSecsCommunicator(SecsCommunicator comm) {
        comm.addSecsCommunicatableStateChangeListener(this);
        comm.addSecsMessageReceiveListener(this);
    }
}

