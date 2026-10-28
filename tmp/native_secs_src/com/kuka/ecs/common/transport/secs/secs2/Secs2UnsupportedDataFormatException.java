/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;

public class Secs2UnsupportedDataFormatException
extends Secs2BytesParseException {
    private static final long serialVersionUID = -305868137866435475L;

    public Secs2UnsupportedDataFormatException() {
    }

    public Secs2UnsupportedDataFormatException(String message) {
        super(message);
    }

    public Secs2UnsupportedDataFormatException(Throwable cause) {
        super(cause);
    }

    public Secs2UnsupportedDataFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}

