/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;

public class HsmsSendMessageException
extends SecsSendMessageException {
    private static final long serialVersionUID = 5687264135212088790L;

    public HsmsSendMessageException(HsmsMessage msg) {
        super(msg);
    }

    public HsmsSendMessageException(HsmsMessage msg, Throwable cause) {
        super(msg, cause);
    }
}

