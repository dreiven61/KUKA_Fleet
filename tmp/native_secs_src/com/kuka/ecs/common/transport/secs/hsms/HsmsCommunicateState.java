/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

public enum HsmsCommunicateState {
    NOT_CONNECTED(false),
    NOT_SELECTED(false),
    SELECTED(true);

    private boolean communicatable;

    private HsmsCommunicateState(boolean f) {
        this.communicatable = f;
    }

    public boolean communicatable() {
        return this.communicatable;
    }
}

