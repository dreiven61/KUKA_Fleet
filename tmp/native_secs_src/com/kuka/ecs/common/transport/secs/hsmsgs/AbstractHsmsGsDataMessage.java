/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsDataMessage;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;

public abstract class AbstractHsmsGsDataMessage
extends AbstractHsmsDataMessage {
    private static final long serialVersionUID = 1729869916863780151L;

    public AbstractHsmsGsDataMessage(byte[] header, Secs2 body) {
        super(header, body);
    }

    public AbstractHsmsGsDataMessage(byte[] header) {
        super(header);
    }
}

