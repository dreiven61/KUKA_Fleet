/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;

public class HsmsWaitReplyMessageException
extends SecsWaitReplyMessageException {
    private static final long serialVersionUID = 1959829976603850269L;

    public HsmsWaitReplyMessageException(SecsMessage primaryMessage) {
        super(primaryMessage);
    }

    public HsmsWaitReplyMessageException(SecsMessage primaryMessage, Throwable cause) {
        super(primaryMessage, cause);
    }
}

