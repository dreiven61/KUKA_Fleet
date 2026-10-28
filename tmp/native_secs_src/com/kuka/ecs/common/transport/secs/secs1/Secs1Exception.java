/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.SecsException;

public class Secs1Exception
extends SecsException {
    private static final long serialVersionUID = -4498265114805329024L;

    public Secs1Exception() {
    }

    public Secs1Exception(String message) {
        super(message);
    }

    public Secs1Exception(Throwable cause) {
        super(cause);
    }

    public Secs1Exception(String message, Throwable cause) {
        super(message, cause);
    }
}

