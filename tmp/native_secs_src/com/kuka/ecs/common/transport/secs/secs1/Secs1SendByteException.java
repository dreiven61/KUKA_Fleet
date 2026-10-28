/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.secs1.Secs1Exception;

public class Secs1SendByteException
extends Secs1Exception {
    private static final long serialVersionUID = 1248896164196481979L;

    public Secs1SendByteException() {
    }

    public Secs1SendByteException(String message) {
        super(message);
    }

    public Secs1SendByteException(Throwable cause) {
        super(cause);
    }

    public Secs1SendByteException(String message, Throwable cause) {
        super(message, cause);
    }
}

