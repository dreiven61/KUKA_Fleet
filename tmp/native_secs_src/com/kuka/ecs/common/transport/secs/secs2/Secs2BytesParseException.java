/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;

public class Secs2BytesParseException
extends Secs2Exception {
    private static final long serialVersionUID = -4597428673299213854L;

    public Secs2BytesParseException() {
    }

    public Secs2BytesParseException(String message) {
        super(message);
    }

    public Secs2BytesParseException(Throwable cause) {
        super(cause);
    }

    public Secs2BytesParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

