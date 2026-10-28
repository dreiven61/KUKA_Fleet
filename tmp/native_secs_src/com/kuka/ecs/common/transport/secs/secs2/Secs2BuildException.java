/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;

public class Secs2BuildException
extends Secs2Exception {
    private static final long serialVersionUID = -7679941208943175508L;

    public Secs2BuildException() {
    }

    public Secs2BuildException(String message) {
        super(message);
    }

    public Secs2BuildException(Throwable cause) {
        super(cause);
    }

    public Secs2BuildException(String message, Throwable cause) {
        super(message, cause);
    }
}

