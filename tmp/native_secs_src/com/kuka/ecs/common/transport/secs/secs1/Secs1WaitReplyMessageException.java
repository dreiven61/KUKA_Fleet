/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;

public class Secs1WaitReplyMessageException
extends SecsWaitReplyMessageException {
    private static final long serialVersionUID = -6010907057511916221L;

    public Secs1WaitReplyMessageException(Secs1Message primaryMessage) {
        super(primaryMessage);
    }

    public Secs1WaitReplyMessageException(Secs1Message primaryMessage, Throwable cause) {
        super(primaryMessage, cause);
    }
}

