/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;

public class Secs1SendMessageException
extends SecsSendMessageException {
    private static final long serialVersionUID = 8277809939091038496L;

    public Secs1SendMessageException(Secs1Message msg) {
        super(msg);
    }

    public Secs1SendMessageException(Secs1Message msg, Throwable cause) {
        super(msg, cause);
    }
}

