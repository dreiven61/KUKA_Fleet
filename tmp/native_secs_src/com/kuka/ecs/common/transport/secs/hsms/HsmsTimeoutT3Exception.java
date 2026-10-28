/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;

public class HsmsTimeoutT3Exception
extends HsmsWaitReplyMessageException {
    private static final long serialVersionUID = -8380997736755238819L;

    public HsmsTimeoutT3Exception(SecsMessage primaryMessage) {
        super(primaryMessage);
    }

    public HsmsTimeoutT3Exception(SecsMessage primaryMessage, Throwable cause) {
        super(primaryMessage, cause);
    }
}

