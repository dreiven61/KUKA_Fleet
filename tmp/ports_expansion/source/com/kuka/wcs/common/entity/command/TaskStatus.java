/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.wcs.common.entity.command;

public enum TaskStatus {
    SUCCESS(1),
    FAILURE(0);

    int code;

    public int getCode() {
        return this.code;
    }

    private TaskStatus(int code) {
        this.code = code;
    }
}
