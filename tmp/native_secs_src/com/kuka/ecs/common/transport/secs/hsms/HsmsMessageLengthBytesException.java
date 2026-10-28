/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsException;

public class HsmsMessageLengthBytesException
extends HsmsException {
    private static final long serialVersionUID = -3670973419196584240L;
    private final long length;

    public HsmsMessageLengthBytesException(long length) {
        this.length = length;
    }

    public HsmsMessageLengthBytesException(String message, long length) {
        super(message);
        this.length = length;
    }

    public long length() {
        return this.length;
    }
}

