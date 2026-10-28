/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.hsms.HsmsException;

public class HsmsGsUnknownSessionIdException
extends HsmsException {
    private static final long serialVersionUID = -3532958513890338098L;

    public HsmsGsUnknownSessionIdException(int id) {
        super("Unknown Session-ID: " + id);
    }
}

