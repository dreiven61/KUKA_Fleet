/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.Property;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsAsyncSocketChannel;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsCommunicator;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageDeselectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageSelectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSessionNotSelectedException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.util.Optional;

public abstract class AbstractHsmsSession
extends AbstractHsmsCommunicator
implements HsmsSession {
    private final Property<AbstractHsmsAsyncSocketChannel> channel = Property.newInstance(null);
    private final Object syncChannel = new Object();

    public AbstractHsmsSession(AbstractHsmsCommunicatorConfig config) {
        super(config);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean setAsyncSocketChannel(AbstractHsmsAsyncSocketChannel channel) {
        Object object = this.syncChannel;
        synchronized (object) {
            if (this.channel.get() == null) {
                this.channel.set(channel);
                return true;
            }
            return false;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean unsetAsyncSocketChannel() {
        Object object = this.syncChannel;
        synchronized (object) {
            if (this.channel.get() == null) {
                return false;
            }
            this.channel.set(null);
            return true;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean equalsAsyncSocketChannel(AbstractHsmsAsyncSocketChannel channel) {
        Object object = this.syncChannel;
        synchronized (object) {
            AbstractHsmsAsyncSocketChannel ref = (AbstractHsmsAsyncSocketChannel)this.channel.get();
            if (ref != null) {
                return ref == channel;
            }
            return false;
        }
    }

    public void waitUntilUnsetAsyncSocketChannel() throws InterruptedException {
        this.channel.waitUntil(null);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected Optional<AbstractHsmsAsyncSocketChannel> optionalAsyncSocketChannel() {
        Object object = this.syncChannel;
        synchronized (object) {
            AbstractHsmsAsyncSocketChannel x = (AbstractHsmsAsyncSocketChannel)this.channel.get();
            return x == null ? Optional.empty() : Optional.of(x);
        }
    }

    protected AbstractHsmsAsyncSocketChannel asyncSocketChannel() throws HsmsSessionNotSelectedException {
        return this.optionalAsyncSocketChannel().orElseThrow(HsmsSessionNotSelectedException::new);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean notifyReceiveHsmsMessage(AbstractHsmsMessage msg) throws InterruptedException {
        Object object = this.syncChannel;
        synchronized (object) {
            if (this.channel == null) {
                return false;
            }
            this.notifyReceiveMessage(msg);
            return true;
        }
    }

    @Override
    public Optional<SecsMessage> templateSend(int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.asyncSocketChannel().send(this, strm, func, wbit, secs2).map(m -> m);
    }

    @Override
    public Optional<SecsMessage> templateSend(SecsMessage primaryMsg, int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.asyncSocketChannel().send(primaryMsg, strm, func, wbit, secs2).map(m -> m);
    }

    @Override
    public Optional<HsmsMessage> send(HsmsMessage msg) throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        return this.asyncSocketChannel().sendHsmsMessage(msg);
    }

    @Override
    public boolean linktest() throws InterruptedException {
        try {
            Optional<HsmsMessage> op = this.asyncSocketChannel().sendLinktestRequest(this);
            boolean f = op.isPresent();
            if (f) {
                return true;
            }
        }
        catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException secsException) {
            // empty catch block
        }
        this.optionalAsyncSocketChannel().ifPresent(AbstractHsmsAsyncSocketChannel::shutdown);
        return false;
    }

    @Override
    public boolean select() throws InterruptedException {
        try {
            return this.asyncSocketChannel().sendSelectRequest(this).map(HsmsMessageSelectStatus::get).filter(status -> {
                switch (status) {
                    case SUCCESS: 
                    case ACTIVED: 
                    case ENTITY_ACTIVED: {
                        return true;
                    }
                }
                return false;
            }).isPresent();
        }
        catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException secsException) {
            return false;
        }
    }

    @Override
    public boolean deselect() throws InterruptedException {
        try {
            boolean f = this.asyncSocketChannel().sendDeselectRequest(this).map(HsmsMessageDeselectStatus::get).filter(status -> {
                switch (status) {
                    case SUCCESS: 
                    case NO_SELECTED: {
                        return true;
                    }
                }
                return false;
            }).isPresent();
            if (f) {
                this.optionalAsyncSocketChannel().ifPresent(AbstractHsmsAsyncSocketChannel::shutdown);
                return true;
            }
        }
        catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException secsException) {
            // empty catch block
        }
        return false;
    }

    @Override
    public boolean separate() throws InterruptedException {
        try {
            this.asyncSocketChannel().sendSeparateRequest(this);
            this.optionalAsyncSocketChannel().ifPresent(AbstractHsmsAsyncSocketChannel::shutdown);
            return true;
        }
        catch (HsmsException | HsmsSendMessageException | HsmsWaitReplyMessageException secsException) {
            return false;
        }
    }

    public int hashCode() {
        return this.sessionId();
    }

    public boolean equals(Object o) {
        if (o != null && o instanceof AbstractHsmsSession) {
            return ((AbstractHsmsSession)o).sessionId() == this.sessionId();
        }
        return false;
    }
}

