/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

public class DynamicEventReportException
extends Exception {
    private static final long serialVersionUID = -8074400313735676602L;

    public DynamicEventReportException() {
    }

    public DynamicEventReportException(String message) {
        super(message);
    }

    public DynamicEventReportException(Throwable cause) {
        super(cause);
    }

    public DynamicEventReportException(String message, Throwable cause) {
        super(message, cause);
    }

    public DynamicEventReportException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

