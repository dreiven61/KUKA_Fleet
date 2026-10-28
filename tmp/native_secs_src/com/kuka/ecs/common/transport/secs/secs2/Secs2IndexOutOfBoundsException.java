/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;

public class Secs2IndexOutOfBoundsException
extends Secs2Exception {
    private static final long serialVersionUID = -9048679220913013292L;

    public Secs2IndexOutOfBoundsException() {
    }

    public Secs2IndexOutOfBoundsException(String message) {
        super(message);
    }

    public Secs2IndexOutOfBoundsException(Throwable cause) {
        super(cause);
    }

    public Secs2IndexOutOfBoundsException(String message, Throwable cause) {
        super(message, cause);
    }
}

