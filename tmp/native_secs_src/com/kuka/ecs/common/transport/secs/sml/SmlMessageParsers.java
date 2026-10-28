/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.sml;

import com.kuka.ecs.common.transport.secs.sml.AbstractSmlMessageParser;
import com.kuka.ecs.common.transport.secs.sml.SmlMessage;
import com.kuka.ecs.common.transport.secs.sml.SmlMessageParser;
import com.kuka.ecs.common.transport.secs.sml.SmlParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;

public final class SmlMessageParsers {
    private SmlMessageParsers() {
    }

    public static SmlMessageParser getInstance() {
        return SingletonHolder.inst;
    }

    public static SmlMessage parse(CharSequence cs) throws SmlParseException {
        return SmlMessageParsers.getInstance().parse(cs);
    }

    public static SmlMessage parse(Reader reader) throws SmlParseException, IOException {
        return SmlMessageParsers.getInstance().parse(reader);
    }

    public static SmlMessage parse(Path path) throws SmlParseException, IOException {
        return SmlMessageParsers.getInstance().parse(path);
    }

    private static final class SingletonHolder {
        private static final SmlMessageParser inst = new AbstractSmlMessageParser(){};

        private SingletonHolder() {
        }
    }
}

