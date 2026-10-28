/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.data.common;

public enum SecsErcCommand {
    TRANSFER("TRANSFER", "\u642c\u8fd0");

    String name;
    String desc;

    private SecsErcCommand(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public String getName() {
        return this.name;
    }

    public String getDesc() {
        return this.desc;
    }
}

