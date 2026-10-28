/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageLengthBytesException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;

public class HsmsControlMessageLengthBytesUpperThanTenException
extends HsmsMessageLengthBytesException {
    private static final long serialVersionUID = -5662768171171603635L;
    private final HsmsMessageType type;

    public HsmsControlMessageLengthBytesUpperThanTenException(HsmsMessageType type, long length) {
        super("type: " + type.toString() + ", length: " + length, length);
        this.type = type;
    }

    public HsmsMessageType type() {
        return this.type;
    }
}

