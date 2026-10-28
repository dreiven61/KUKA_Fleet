/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;

public enum HsmsMessageDeselectStatus {
    NOT_DESELECT_RSP(-1),
    UNKNOWN(-1),
    SUCCESS(0),
    NO_SELECTED(1),
    FAILED(2);

    private final byte status;

    private HsmsMessageDeselectStatus(byte status) {
        this.status = status;
    }

    public byte statusCode() {
        return this.status;
    }

    public static HsmsMessageDeselectStatus get(byte b) {
        for (HsmsMessageDeselectStatus s : HsmsMessageDeselectStatus.values()) {
            if (s == NOT_DESELECT_RSP || s == UNKNOWN || s.status != b) continue;
            return s;
        }
        return UNKNOWN;
    }

    public static HsmsMessageDeselectStatus get(HsmsMessage msg) {
        HsmsMessageType type = HsmsMessageType.get(msg);
        if (type == HsmsMessageType.DESELECT_RSP) {
            byte[] head = msg.header10Bytes();
            byte b = head[3];
            return HsmsMessageDeselectStatus.get(b);
        }
        return NOT_DESELECT_RSP;
    }
}

