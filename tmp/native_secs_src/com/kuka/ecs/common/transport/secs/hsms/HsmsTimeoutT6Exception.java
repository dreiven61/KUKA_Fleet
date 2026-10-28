/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;

public class HsmsTimeoutT6Exception
extends HsmsWaitReplyMessageException {
    private static final long serialVersionUID = -4799649738977591873L;

    public HsmsTimeoutT6Exception(SecsMessage primaryMessage) {
        super(primaryMessage);
    }

    public HsmsTimeoutT6Exception(SecsMessage primaryMessage, Throwable cause) {
        super(primaryMessage, cause);
    }
}

