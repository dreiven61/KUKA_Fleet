/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Message;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1MessageBlock;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1MessageBuilder;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1TooBigSendMessageException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;
import java.util.List;

public interface Secs1MessageBuilder {
    public AbstractSecs1Message build(int var1, int var2, boolean var3) throws Secs1TooBigSendMessageException;

    public AbstractSecs1Message build(int var1, int var2, boolean var3, Secs2 var4) throws Secs1TooBigSendMessageException;

    public AbstractSecs1Message build(SecsMessage var1, int var2, int var3, boolean var4) throws Secs1TooBigSendMessageException;

    public AbstractSecs1Message build(SecsMessage var1, int var2, int var3, boolean var4, Secs2 var5) throws Secs1TooBigSendMessageException;

    public static AbstractSecs1Message fromBlocks(List<? extends AbstractSecs1MessageBlock> blocks) throws Secs2BytesParseException {
        return AbstractSecs1MessageBuilder.fromBlocks(blocks);
    }

    public static AbstractSecs1Message fromMessage(Secs1Message msg) throws Secs1TooBigSendMessageException {
        return AbstractSecs1MessageBuilder.fromMessage(msg);
    }

    public static AbstractSecs1Message build(byte[] header, Secs2 body) throws Secs1TooBigSendMessageException {
        return AbstractSecs1MessageBuilder.build(header, body);
    }

    public static AbstractSecs1Message build(byte[] header) throws Secs1TooBigSendMessageException {
        return AbstractSecs1MessageBuilder.build(header);
    }
}

