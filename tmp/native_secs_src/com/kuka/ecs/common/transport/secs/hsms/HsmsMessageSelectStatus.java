/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;

public enum HsmsMessageSelectStatus {
    NOT_SELECT_RSP(-1),
    UNKNOWN(-1),
    SUCCESS(0),
    ACTIVED(1),
    NOT_READY(2),
    ALREADY_USED(3),
    ENTITY_UNKNOWN(4),
    ENTITY_ALREADY_USED(5),
    ENTITY_ACTIVED(6);

    private final byte status;

    private HsmsMessageSelectStatus(byte status) {
        this.status = status;
    }

    public byte statusCode() {
        return this.status;
    }

    public static HsmsMessageSelectStatus get(byte b) {
        for (HsmsMessageSelectStatus s : HsmsMessageSelectStatus.values()) {
            if (s == NOT_SELECT_RSP || s == UNKNOWN || s.status != b) continue;
            return s;
        }
        return UNKNOWN;
    }

    public static HsmsMessageSelectStatus get(HsmsMessage msg) {
        HsmsMessageType type = HsmsMessageType.get(msg);
        if (type == HsmsMessageType.SELECT_RSP) {
            byte[] head = msg.header10Bytes();
            byte b = head[3];
            return HsmsMessageSelectStatus.get(b);
        }
        return NOT_SELECT_RSP;
    }
}

