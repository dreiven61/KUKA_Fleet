/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.util;

import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.sml.SmlMessage;
import com.kuka.ecs.common.transport.secs.util.EntityMessageSender;
import java.util.Optional;

public abstract class AbstractEntityMessageSender
implements EntityMessageSender {
    private final SecsCommunicator comm;
    private final Object syncDoSendS9F9 = new Object();
    private boolean doSendS9F9;

    public AbstractEntityMessageSender(SecsCommunicator communicator) {
        this.comm = communicator;
        this.doSendS9F9 = false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void setSendS9F9(boolean doSend) {
        Object object = this.syncDoSendS9F9;
        synchronized (object) {
            this.doSendS9F9 = doSend;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Optional<SecsMessage> sendS9F9(SecsWaitReplyMessageException e) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        SecsMessage refMsg = e.secsMessage().orElse(null);
        if (refMsg != null) {
            Object object = this.syncDoSendS9F9;
            synchronized (object) {
                if (this.doSendS9F9) {
                    return this.comm.gem().s9f9(refMsg);
                }
            }
        }
        return Optional.empty();
    }

    public void sendS9F9IgnoreException(SecsWaitReplyMessageException e) throws InterruptedException {
        try {
            this.sendS9F9(e);
        }
        catch (SecsException secsException) {
            // empty catch block
        }
    }

    @Override
    public Optional<SecsMessage> send(int strm, int func, boolean wbit) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        try {
            return this.comm.send(strm, func, wbit);
        }
        catch (SecsWaitReplyMessageException e) {
            this.sendS9F9IgnoreException(e);
            throw e;
        }
    }

    @Override
    public Optional<SecsMessage> send(int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        try {
            return this.comm.send(strm, func, wbit, secs2);
        }
        catch (SecsWaitReplyMessageException e) {
            this.sendS9F9IgnoreException(e);
            throw e;
        }
    }

    @Override
    public Optional<SecsMessage> send(SecsMessage primaryMsg, int strm, int func, boolean wbit) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        try {
            return this.comm.send(primaryMsg, strm, func, wbit);
        }
        catch (SecsWaitReplyMessageException e) {
            this.sendS9F9IgnoreException(e);
            throw e;
        }
    }

    @Override
    public Optional<SecsMessage> send(SecsMessage primaryMsg, int strm, int func, boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        try {
            return this.comm.send(primaryMsg, strm, func, wbit, secs2);
        }
        catch (SecsWaitReplyMessageException e) {
            this.sendS9F9IgnoreException(e);
            throw e;
        }
    }

    @Override
    public Optional<SecsMessage> send(SmlMessage sml) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        try {
            return this.comm.send(sml);
        }
        catch (SecsWaitReplyMessageException e) {
            this.sendS9F9IgnoreException(e);
            throw e;
        }
    }

    @Override
    public Optional<SecsMessage> send(SecsMessage primaryMsg, SmlMessage sml) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        try {
            return this.comm.send(primaryMsg, sml);
        }
        catch (SecsWaitReplyMessageException e) {
            this.sendS9F9IgnoreException(e);
            throw e;
        }
    }
}

