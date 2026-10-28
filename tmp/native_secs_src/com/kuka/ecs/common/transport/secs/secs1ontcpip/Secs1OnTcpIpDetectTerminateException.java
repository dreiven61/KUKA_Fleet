/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1ontcpip;

import com.kuka.ecs.common.transport.secs.secs1.Secs1SendByteException;

public class Secs1OnTcpIpDetectTerminateException
extends Secs1SendByteException {
    private static final long serialVersionUID = 4719709951319519171L;

    public Secs1OnTcpIpDetectTerminateException() {
    }

    public Secs1OnTcpIpDetectTerminateException(String message) {
        super(message);
    }

    public Secs1OnTcpIpDetectTerminateException(Throwable cause) {
        super(cause);
    }

    public Secs1OnTcpIpDetectTerminateException(String message, Throwable cause) {
        super(message, cause);
    }
}

