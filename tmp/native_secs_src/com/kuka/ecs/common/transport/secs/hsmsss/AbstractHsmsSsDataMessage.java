/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsDataMessage;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;

public abstract class AbstractHsmsSsDataMessage
extends AbstractHsmsDataMessage {
    private static final long serialVersionUID = -7771560085653492974L;

    public AbstractHsmsSsDataMessage(byte[] header, Secs2 body) {
        super(header, body);
    }

    public AbstractHsmsSsDataMessage(byte[] header) {
        super(header);
    }
}

