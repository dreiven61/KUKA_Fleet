/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;

public enum HsmsMessageRejectReason {
    NOT_REJECT_REQ(-1),
    UNKNOWN(-1),
    NOT_SUPPORT_TYPE_S(1),
    NOT_SUPPORT_TYPE_P(2),
    TRANSACTION_NOT_OPEN(3),
    NOT_SELECTED(4);

    private final byte reason;

    private HsmsMessageRejectReason(byte reason) {
        this.reason = reason;
    }

    public byte reasonCode() {
        return this.reason;
    }

    public static HsmsMessageRejectReason get(byte b) {
        for (HsmsMessageRejectReason r : HsmsMessageRejectReason.values()) {
            if (r == UNKNOWN || r.reason != b) continue;
            return r;
        }
        return UNKNOWN;
    }

    public static HsmsMessageRejectReason get(HsmsMessage msg) {
        HsmsMessageType type = HsmsMessageType.get(msg);
        if (type == HsmsMessageType.REJECT_REQ) {
            byte[] head = msg.header10Bytes();
            byte b = head[3];
            return HsmsMessageRejectReason.get(b);
        }
        return NOT_REJECT_REQ;
    }
}

