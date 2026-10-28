/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1SendMessageException;

public class Secs1TooBigSendMessageException
extends Secs1SendMessageException {
    private static final long serialVersionUID = 7790874216559023571L;

    public Secs1TooBigSendMessageException(Secs1Message msg) {
        super(msg);
    }

    public Secs1TooBigSendMessageException(Secs1Message msg, Throwable cause) {
        super(msg, cause);
    }
}

