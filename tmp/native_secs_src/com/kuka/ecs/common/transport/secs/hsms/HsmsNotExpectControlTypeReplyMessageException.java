/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;

public class HsmsNotExpectControlTypeReplyMessageException
extends HsmsWaitReplyMessageException {
    private static final long serialVersionUID = -3694450588050886894L;

    public HsmsNotExpectControlTypeReplyMessageException(SecsMessage primaryMessage) {
        super(primaryMessage);
    }

    public HsmsNotExpectControlTypeReplyMessageException(SecsMessage primaryMessage, Throwable cause) {
        super(primaryMessage, cause);
    }
}

