/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsControlMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsDataMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageBuilder;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageDeselectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageRejectReason;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageSelectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractHsmsMessageBuilder
implements HsmsMessageBuilder {
    private final AtomicInteger autoNum = new AtomicInteger(0);

    protected byte[] getAutoNumber2Bytes() {
        int n = this.autoNum.incrementAndGet();
        return new byte[]{(byte)(n >> 8), (byte)n};
    }

    protected byte[] getSessionId2Bytes(AbstractHsmsSession session) {
        int n = session.sessionId();
        return new byte[]{(byte)(n >> 8), (byte)n};
    }

    protected byte[] getSystem4Bytes(AbstractHsmsSession session) {
        byte[] aa = this.getAutoNumber2Bytes();
        if (session.isEquip()) {
            byte[] ss = this.getSessionId2Bytes(session);
            return new byte[]{ss[0], ss[1], aa[0], aa[1]};
        }
        return new byte[]{0, 0, aa[0], aa[1]};
    }

    @Override
    public AbstractHsmsControlMessage buildSelectResponse(HsmsMessage primaryMsg, HsmsMessageSelectStatus status) {
        byte[] bs = primaryMsg.header10Bytes();
        byte[] header = new byte[]{bs[0], bs[1], 0, status.statusCode(), HsmsMessageType.SELECT_RSP.pType(), HsmsMessageType.SELECT_RSP.sType(), bs[6], bs[7], bs[8], bs[9]};
        return this.buildHsmsControlMessage(header, Secs2.empty());
    }

    @Override
    public AbstractHsmsControlMessage buildDeselectResponse(HsmsMessage primaryMsg, HsmsMessageDeselectStatus status) {
        byte[] bs = primaryMsg.header10Bytes();
        byte[] header = new byte[]{bs[0], bs[1], 0, status.statusCode(), HsmsMessageType.DESELECT_RSP.pType(), HsmsMessageType.DESELECT_RSP.sType(), bs[6], bs[7], bs[8], bs[9]};
        return this.buildHsmsControlMessage(header, Secs2.empty());
    }

    @Override
    public AbstractHsmsControlMessage buildLinktestRequest(AbstractHsmsSession session) {
        byte[] sysbytes = this.getSystem4Bytes(session);
        byte[] header = new byte[]{-1, -1, 0, 0, HsmsMessageType.LINKTEST_REQ.pType(), HsmsMessageType.LINKTEST_REQ.sType(), sysbytes[0], sysbytes[1], sysbytes[2], sysbytes[3]};
        return this.buildHsmsControlMessage(header, Secs2.empty());
    }

    @Override
    public AbstractHsmsControlMessage buildLinktestResponse(HsmsMessage primaryMsg) {
        byte[] bs = primaryMsg.header10Bytes();
        byte[] header = new byte[]{bs[0], bs[1], 0, 0, HsmsMessageType.LINKTEST_RSP.pType(), HsmsMessageType.LINKTEST_RSP.sType(), bs[6], bs[7], bs[8], bs[9]};
        return this.buildHsmsControlMessage(header, Secs2.empty());
    }

    @Override
    public AbstractHsmsControlMessage buildRejectRequest(HsmsMessage referenceMsg, HsmsMessageRejectReason reason) {
        byte b2;
        byte[] bs = referenceMsg.header10Bytes();
        switch (reason) {
            case NOT_SUPPORT_TYPE_P: {
                b2 = bs[4];
                break;
            }
            default: {
                b2 = bs[5];
            }
        }
        byte[] header = new byte[]{bs[0], bs[1], b2, reason.reasonCode(), HsmsMessageType.REJECT_REQ.pType(), HsmsMessageType.REJECT_REQ.sType(), bs[6], bs[7], bs[8], bs[9]};
        return this.buildHsmsControlMessage(header, Secs2.empty());
    }

    @Override
    public AbstractHsmsDataMessage buildDataMessage(AbstractHsmsSession session, int strm, int func, boolean wbit) {
        return this.buildDataMessage(session, strm, func, wbit, (Secs2)Secs2.empty());
    }

    @Override
    public AbstractHsmsDataMessage buildDataMessage(AbstractHsmsSession session, int strm, int func, boolean wbit, Secs2 body) {
        byte[] sessionId2Bytes = this.getSessionId2Bytes(session);
        byte[] sysbytes = this.getSystem4Bytes(session);
        byte[] header = new byte[]{sessionId2Bytes[0], sessionId2Bytes[1], (byte)(strm & 0x7F), (byte)func, HsmsMessageType.DATA.pType(), HsmsMessageType.DATA.sType(), sysbytes[0], sysbytes[1], sysbytes[2], sysbytes[3]};
        if (wbit) {
            header[2] = (byte)(header[2] | 0xFFFFFF80);
        }
        return this.buildHsmsDataMessage(header, body);
    }

    @Override
    public AbstractHsmsDataMessage buildDataMessage(SecsMessage primaryMsg, int strm, int func, boolean wbit) {
        return this.buildDataMessage(primaryMsg, strm, func, wbit, (Secs2)Secs2.empty());
    }

    @Override
    public AbstractHsmsDataMessage buildDataMessage(SecsMessage primaryMsg, int strm, int func, boolean wbit, Secs2 body) {
        byte[] bs = primaryMsg.header10Bytes();
        byte[] header = new byte[]{bs[0], bs[1], (byte)(strm & 0x7F), (byte)func, HsmsMessageType.DATA.pType(), HsmsMessageType.DATA.sType(), bs[6], bs[7], bs[8], bs[9]};
        if (wbit) {
            header[2] = (byte)(header[2] | 0xFFFFFF80);
        }
        return this.buildHsmsDataMessage(header, body);
    }

    protected abstract AbstractHsmsControlMessage buildHsmsControlMessage(byte[] var1, Secs2 var2);

    protected abstract AbstractHsmsDataMessage buildHsmsDataMessage(byte[] var1, Secs2 var2);
}

