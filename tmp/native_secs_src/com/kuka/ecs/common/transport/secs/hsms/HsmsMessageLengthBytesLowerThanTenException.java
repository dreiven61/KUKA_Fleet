/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageLengthBytesException;

public class HsmsMessageLengthBytesLowerThanTenException
extends HsmsMessageLengthBytesException {
    private static final long serialVersionUID = -5199965558586918070L;

    public HsmsMessageLengthBytesLowerThanTenException(long length) {
        super("length: " + length, length);
    }
}

