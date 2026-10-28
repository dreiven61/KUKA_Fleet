/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.sml;

public class SmlParseException
extends Exception {
    private static final long serialVersionUID = 6933932663095380472L;

    public SmlParseException() {
    }

    public SmlParseException(String message) {
        super(message);
    }

    public SmlParseException(Throwable cause) {
        super(cause);
    }

    public SmlParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public SmlParseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

