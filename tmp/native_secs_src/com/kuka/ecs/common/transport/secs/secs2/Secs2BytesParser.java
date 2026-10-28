/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParsers;
import java.util.List;

public interface Secs2BytesParser {
    public Secs2 parse(List<byte[]> var1) throws Secs2BytesParseException;

    public static Secs2BytesParser getInstance() {
        return Secs2BytesParsers.getInstance();
    }
}

