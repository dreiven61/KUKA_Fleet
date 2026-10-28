/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;

public class HsmsRejectException
extends HsmsWaitReplyMessageException {
    private static final long serialVersionUID = -5627136181555783679L;

    public HsmsRejectException(SecsMessage primaryMessage) {
        super(primaryMessage);
    }

    public HsmsRejectException(SecsMessage primaryMessage, Throwable cause) {
        super(primaryMessage, cause);
    }
}

