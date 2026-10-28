/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageBuilder;
import com.kuka.ecs.common.transport.secs.hsmsss.AbstractHsmsSsMessageBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;
import java.util.List;

public interface HsmsSsMessageBuilder
extends HsmsMessageBuilder {
    public static AbstractHsmsMessage build(byte[] header) {
        return AbstractHsmsSsMessageBuilder.build(header);
    }

    public static AbstractHsmsMessage build(byte[] header, Secs2 body) {
        return AbstractHsmsSsMessageBuilder.build(header, body);
    }

    public static AbstractHsmsMessage fromBytes(byte[] header, List<byte[]> bodies) throws Secs2BytesParseException {
        return AbstractHsmsSsMessageBuilder.fromBytes(header, bodies);
    }

    public static AbstractHsmsMessage fromMessage(HsmsMessage message) {
        return AbstractHsmsSsMessageBuilder.fromMessage(message);
    }
}

