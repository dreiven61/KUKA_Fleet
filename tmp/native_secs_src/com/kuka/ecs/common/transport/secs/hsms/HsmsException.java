/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsException;

public class HsmsException
extends SecsException {
    private static final long serialVersionUID = 6427923930733052896L;

    public HsmsException() {
    }

    public HsmsException(String message) {
        super(message);
    }

    public HsmsException(Throwable cause) {
        super(cause);
    }

    public HsmsException(String message, Throwable cause) {
        super(message, cause);
    }
}

