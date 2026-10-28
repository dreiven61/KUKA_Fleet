/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.sml;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.sml.AbstractSmlDataItemParser;
import com.kuka.ecs.common.transport.secs.sml.SmlParseException;

public interface SmlDataItemParser {
    public Secs2 parse(CharSequence var1) throws SmlParseException;

    public static SmlDataItemParser newInstance() {
        return new AbstractSmlDataItemParser(){};
    }
}

