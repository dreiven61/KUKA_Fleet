/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsgs;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageBuilder;
import com.kuka.ecs.common.transport.secs.hsmsgs.AbstractHsmsGsMessageBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;
import java.util.List;

public interface HsmsGsMessageBuilder
extends HsmsMessageBuilder {
    public static AbstractHsmsMessage build(byte[] header) {
        return AbstractHsmsGsMessageBuilder.build(header);
    }

    public static AbstractHsmsMessage build(byte[] header, Secs2 body) {
        return AbstractHsmsGsMessageBuilder.build(header, body);
    }

    public static AbstractHsmsMessage fromMessage(HsmsMessage message) {
        return AbstractHsmsGsMessageBuilder.fromMessage(message);
    }

    public static AbstractHsmsMessage fromBytes(byte[] header, List<byte[]> bodies) throws Secs2BytesParseException {
        return AbstractHsmsGsMessageBuilder.fromBytes(header, bodies);
    }
}

