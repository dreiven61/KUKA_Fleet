/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsException;

public class HsmsNotConnectedException
extends HsmsException {
    private static final long serialVersionUID = 183609995184389928L;

    public HsmsNotConnectedException() {
    }

    public HsmsNotConnectedException(Throwable cause) {
        super(cause);
    }
}

