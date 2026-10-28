/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsControlMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsDataMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessageBuilder;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageType;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsControlMessage;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsDataMessage;
import com.kuka.ecs.common.transport.secs.hsmsgs.HsmsGsMessageBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParsers;
import java.util.List;

public abstract class AbstractHsmsGsMessageBuilder
extends AbstractHsmsMessageBuilder
implements HsmsGsMessageBuilder {
    @Override
    public AbstractHsmsControlMessage buildSelectRequest(AbstractHsmsSession session) {
        byte[] ss = this.getSessionId2Bytes(session);
        byte[] sysbytes = this.getSystem4Bytes(session);
        byte[] header = new byte[]{ss[0], ss[1], 0, 0, HsmsMessageType.SELECT_REQ.pType(), HsmsMessageType.SELECT_REQ.sType(), sysbytes[0], sysbytes[1], sysbytes[2], sysbytes[3]};
        return this.buildHsmsControlMessage(header, Secs2.empty());
    }

    @Override
    public AbstractHsmsControlMessage buildDeselectRequest(AbstractHsmsSession session) {
        byte[] ss = this.getSessionId2Bytes(session);
        byte[] sysbytes = this.getSystem4Bytes(session);
        byte[] header = new byte[]{ss[0], ss[1], 0, 0, HsmsMessageType.DESELECT_REQ.pType(), HsmsMessageType.DESELECT_REQ.sType(), sysbytes[0], sysbytes[1], sysbytes[2], sysbytes[3]};
        return this.buildHsmsControlMessage(header, Secs2.empty());
    }

    @Override
    public AbstractHsmsControlMessage buildSeparateRequest(AbstractHsmsSession session) {
        byte[] ss = this.getSessionId2Bytes(session);
        byte[] sysbytes = this.getSystem4Bytes(session);
        byte[] header = new byte[]{ss[0], ss[1], 0, 0, HsmsMessageType.SEPARATE_REQ.pType(), HsmsMessageType.SEPARATE_REQ.sType(), sysbytes[0], sysbytes[1], sysbytes[2], sysbytes[3]};
        return this.buildHsmsControlMessage(header, Secs2.empty());
    }

    @Override
    protected AbstractHsmsControlMessage buildHsmsControlMessage(byte[] header, Secs2 body) {
        return AbstractHsmsGsMessageBuilder.newControlMessageInstance(header, body);
    }

    @Override
    protected AbstractHsmsDataMessage buildHsmsDataMessage(byte[] header, Secs2 body) {
        return AbstractHsmsGsMessageBuilder.newDataMessageInstance(header, body);
    }

    private static AbstractHsmsGsControlMessage newControlMessageInstance(byte[] header, Secs2 body) {
        return new AbstractHsmsGsControlMessage(header, body){
            private static final long serialVersionUID = 7548549319511317849L;
        };
    }

    private static AbstractHsmsGsDataMessage newDataMessageInstance(byte[] header, Secs2 body) {
        return new AbstractHsmsGsDataMessage(header, body){
            private static final long serialVersionUID = 7134587849768341782L;
        };
    }

    public static AbstractHsmsMessage build(byte[] header) {
        return AbstractHsmsGsMessageBuilder.build(header, Secs2.empty());
    }

    public static AbstractHsmsMessage build(byte[] header, Secs2 body) {
        if (HsmsMessageType.get(header[4], header[5]) == HsmsMessageType.DATA) {
            return AbstractHsmsGsMessageBuilder.newDataMessageInstance(header, body);
        }
        return AbstractHsmsGsMessageBuilder.newControlMessageInstance(header, body);
    }

    public static AbstractHsmsMessage fromBytes(byte[] header, List<byte[]> bodies) throws Secs2BytesParseException {
        return AbstractHsmsGsMessageBuilder.build(header, Secs2BytesParsers.parse(bodies));
    }

    public static AbstractHsmsMessage fromMessage(HsmsMessage message) {
        if (message instanceof AbstractHsmsMessage) {
            return (AbstractHsmsMessage)message;
        }
        return AbstractHsmsGsMessageBuilder.build(message.header10Bytes(), message.secs2());
    }
}

