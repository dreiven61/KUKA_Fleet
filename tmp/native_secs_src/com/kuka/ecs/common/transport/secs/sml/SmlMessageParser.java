/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.sml;

import com.kuka.ecs.common.transport.secs.sml.SmlMessage;
import com.kuka.ecs.common.transport.secs.sml.SmlMessageParsers;
import com.kuka.ecs.common.transport.secs.sml.SmlParseException;
import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public interface SmlMessageParser {
    public SmlMessage parse(CharSequence var1) throws SmlParseException;

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    default public SmlMessage parse(Reader reader) throws SmlParseException, IOException {
        try (CharArrayWriter w = new CharArrayWriter();){
            while (true) {
                int r;
                if ((r = reader.read()) < 0) {
                    SmlMessage smlMessage = this.parse(w.toString());
                    return smlMessage;
                }
                w.write(r);
                continue;
                break;
            }
        }
    }

    default public SmlMessage parse(Path path) throws SmlParseException, IOException {
        try (BufferedReader r = Files.newBufferedReader(path);){
            SmlMessage smlMessage = this.parse(r);
            return smlMessage;
        }
    }

    public static SmlMessageParser getInstance() {
        return SmlMessageParsers.getInstance();
    }
}

