/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;

public class Secs2IllegalDataFormatException
extends Secs2Exception {
    private static final long serialVersionUID = -2801232481812100207L;

    public Secs2IllegalDataFormatException() {
    }

    public Secs2IllegalDataFormatException(String message) {
        super(message);
    }

    public Secs2IllegalDataFormatException(Throwable cause) {
        super(cause);
    }

    public Secs2IllegalDataFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}

