/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.sml;

import com.kuka.ecs.common.transport.secs.sml.AbstractSmlMessage;
import com.kuka.ecs.common.transport.secs.sml.SmlDataItemParser;
import com.kuka.ecs.common.transport.secs.sml.SmlMessage;
import com.kuka.ecs.common.transport.secs.sml.SmlMessageParser;
import com.kuka.ecs.common.transport.secs.sml.SmlParseException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractSmlMessageParser
implements SmlMessageParser {
    private final SmlDataItemParser smlDataItemParser = SmlDataItemParser.newInstance();
    protected static final String GROUP_STREAM = "STREAM";
    protected static final String GROUP_FUNCTION = "FUNCTION";
    protected static final String GROUP_WBIT = "WBIT";
    protected static final String GROUP_SECS2 = "SECS2";
    protected static final String pregMessage = "[Ss](?<STREAM>[0-9]{1,3})[Ff](?<FUNCTION>[0-9]{1,3})\\s*(?<WBIT>[Ww]?)\\s*(?<SECS2>(<.+>)?)";
    protected static final Pattern ptnMessage = Pattern.compile("^[Ss](?<STREAM>[0-9]{1,3})[Ff](?<FUNCTION>[0-9]{1,3})\\s*(?<WBIT>[Ww]?)\\s*(?<SECS2>(<.+>)?)$");

    protected SmlDataItemParser getSmlSecs2Parser() {
        return this.smlDataItemParser;
    }

    @Override
    public SmlMessage parse(CharSequence cs) throws SmlParseException {
        Objects.requireNonNull(cs);
        String s = this.trimPeriod(cs);
        Matcher m = ptnMessage.matcher(s);
        if (!m.matches()) {
            throw new SmlParseException("\"SxFy [W] items.\" parse failed");
        }
        try {
            int strm = Integer.parseInt(m.group(GROUP_STREAM));
            int func = Integer.parseInt(m.group(GROUP_FUNCTION));
            boolean wbit = !m.group(GROUP_WBIT).isEmpty();
            String secs2 = m.group(GROUP_SECS2);
            return new AbstractSmlMessage(strm, func, wbit, this.getSmlSecs2Parser().parse(secs2)){
                private static final long serialVersionUID = 5278030007936351049L;
            };
        }
        catch (NumberFormatException e) {
            throw new SmlParseException("SxFy parse failed", e);
        }
    }

    private String trimPeriod(CharSequence cs) throws SmlParseException {
        String s = cs.toString().replaceAll("\\r\\n|\\r|\\n|\\t", " ").trim();
        if (s.endsWith(".")) {
            return s.substring(0, s.length() - 1).trim();
        }
        throw new SmlParseException("not end \".\"");
    }
}

