/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;

public class SecsSendMessageException
extends SecsException {
    private static final long serialVersionUID = 7063333511988028224L;

    public SecsSendMessageException() {
    }

    public SecsSendMessageException(String message) {
        super(message);
    }

    public SecsSendMessageException(Throwable cause) {
        super(cause);
    }

    public SecsSendMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecsSendMessageException(SecsMessage msg) {
        super(msg);
    }

    public SecsSendMessageException(SecsMessage msg, Throwable cause) {
        super(msg, cause);
    }
}

