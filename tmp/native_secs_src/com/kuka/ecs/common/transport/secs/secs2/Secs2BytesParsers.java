/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.AbstractSecs2BytesParser;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParser;
import java.util.List;

public final class Secs2BytesParsers {
    private Secs2BytesParsers() {
    }

    public static Secs2BytesParser getInstance() {
        return SingletonHolder.inst;
    }

    public static Secs2 parse(List<byte[]> bss) throws Secs2BytesParseException {
        return Secs2BytesParsers.getInstance().parse(bss);
    }

    private static final class SingletonHolder {
        private static final Secs2BytesParser inst = new AbstractSecs2BytesParser(){};

        private SingletonHolder() {
        }
    }
}

