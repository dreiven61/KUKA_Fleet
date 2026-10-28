/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1WaitReplyMessageException;

public class Secs1TimeoutT3Exception
extends Secs1WaitReplyMessageException {
    private static final long serialVersionUID = -2366554276167734382L;

    public Secs1TimeoutT3Exception(Secs1Message primaryMessage) {
        super(primaryMessage);
    }

    public Secs1TimeoutT3Exception(Secs1Message primaryMessage, Throwable cause) {
        super(primaryMessage, cause);
    }
}

