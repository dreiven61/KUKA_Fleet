/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.sml;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.sml.SmlMessageParser;
import com.kuka.ecs.common.transport.secs.sml.SmlParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;

public interface SmlMessage {
    public int getStream();

    public int getFunction();

    public boolean wbit();

    public Secs2 secs2();

    public static SmlMessage from(CharSequence cs) throws SmlParseException {
        return SmlMessageParser.getInstance().parse(cs);
    }

    public static SmlMessage from(Reader reader) throws SmlParseException, IOException {
        return SmlMessageParser.getInstance().parse(reader);
    }

    public static SmlMessage from(Path path) throws SmlParseException, IOException {
        return SmlMessageParser.getInstance().parse(path);
    }
}

