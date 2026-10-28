/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;

public class HsmsTooBigSendMessageException
extends HsmsSendMessageException {
    private static final long serialVersionUID = -876996703664224152L;

    public HsmsTooBigSendMessageException(HsmsMessage msg) {
        super(msg);
    }
}

