/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2BuildException;

public class Secs2LengthByteOutOfRangeException
extends Secs2BuildException {
    private static final long serialVersionUID = -4790954656700659624L;

    public Secs2LengthByteOutOfRangeException() {
    }

    public Secs2LengthByteOutOfRangeException(String message) {
        super(message);
    }

    public Secs2LengthByteOutOfRangeException(Throwable cause) {
        super(cause);
    }

    public Secs2LengthByteOutOfRangeException(String message, Throwable cause) {
        super(message, cause);
    }
}

