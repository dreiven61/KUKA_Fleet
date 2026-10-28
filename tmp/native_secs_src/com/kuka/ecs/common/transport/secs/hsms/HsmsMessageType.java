/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;

public enum HsmsMessageType {
    UNDEFINED(-128, -128),
    DATA(0, 0),
    SELECT_REQ(0, 1),
    SELECT_RSP(0, 2),
    DESELECT_REQ(0, 3),
    DESELECT_RSP(0, 4),
    LINKTEST_REQ(0, 5),
    LINKTEST_RSP(0, 6),
    REJECT_REQ(0, 7),
    SEPARATE_REQ(0, 9);

    private final byte p;
    private final byte s;

    private HsmsMessageType(byte p, byte s) {
        this.p = p;
        this.s = s;
    }

    public byte pType() {
        return this.p;
    }

    public byte sType() {
        return this.s;
    }

    public static HsmsMessageType get(byte p, byte s) {
        for (HsmsMessageType t : HsmsMessageType.values()) {
            if (t == UNDEFINED || t.p != p || t.s != s) continue;
            return t;
        }
        return UNDEFINED;
    }

    public static HsmsMessageType get(HsmsMessage msg) {
        byte[] head = msg.header10Bytes();
        byte p = head[4];
        byte s = head[5];
        return HsmsMessageType.get(p, s);
    }

    public static boolean supportPType(byte p) {
        for (HsmsMessageType t : HsmsMessageType.values()) {
            if (t == UNDEFINED || t.p != p) continue;
            return true;
        }
        return false;
    }

    public static boolean supportPType(HsmsMessage msg) {
        byte[] head = msg.header10Bytes();
        byte p = head[4];
        return HsmsMessageType.supportPType(p);
    }

    public static boolean supportSType(byte s) {
        for (HsmsMessageType t : HsmsMessageType.values()) {
            if (t == UNDEFINED || t.s != s) continue;
            return true;
        }
        return false;
    }

    public static boolean supportSType(HsmsMessage msg) {
        byte[] head = msg.header10Bytes();
        byte s = head[5];
        return HsmsMessageType.supportPType(s);
    }
}

