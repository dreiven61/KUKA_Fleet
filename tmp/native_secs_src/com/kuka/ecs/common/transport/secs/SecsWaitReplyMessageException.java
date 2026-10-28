/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;

public class SecsWaitReplyMessageException
extends SecsException {
    private static final long serialVersionUID = -443398822888562392L;

    public SecsWaitReplyMessageException() {
    }

    public SecsWaitReplyMessageException(String message) {
        super(message);
    }

    public SecsWaitReplyMessageException(Throwable cause) {
        super(cause);
    }

    public SecsWaitReplyMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecsWaitReplyMessageException(SecsMessage primaryMessage) {
        super(primaryMessage);
    }

    public SecsWaitReplyMessageException(SecsMessage primaryMessage, Throwable cause) {
        super(primaryMessage, cause);
    }
}

