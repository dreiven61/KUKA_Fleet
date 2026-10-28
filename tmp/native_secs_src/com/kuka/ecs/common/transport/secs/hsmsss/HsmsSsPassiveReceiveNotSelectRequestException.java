/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.hsms.HsmsException;

public class HsmsSsPassiveReceiveNotSelectRequestException
extends HsmsException {
    private static final long serialVersionUID = -5410805951789181837L;

    public HsmsSsPassiveReceiveNotSelectRequestException() {
    }

    public HsmsSsPassiveReceiveNotSelectRequestException(String message) {
        super(message);
    }

    public HsmsSsPassiveReceiveNotSelectRequestException(Throwable cause) {
        super(cause);
    }

    public HsmsSsPassiveReceiveNotSelectRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}

