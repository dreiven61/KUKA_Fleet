/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.sml;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import com.kuka.ecs.common.transport.secs.sml.SmlDataItemParser;
import com.kuka.ecs.common.transport.secs.sml.SmlParseException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractSmlDataItemParser
implements SmlDataItemParser {
    private static String GROUP_BYTE = "BYTE";
    private static final String pregByte = "0[Xx](?<" + GROUP_BYTE + ">[0-9A-Fa-f]{1,2})";
    private static final Pattern ptnByte = Pattern.compile("^" + pregByte + "$");
    protected static final char SPACE = ' ';
    protected static final char ABB = '<';
    protected static final char ABE = '>';
    protected static final char SBB = '[';
    protected static final char SBE = ']';
    protected static final char DQUOT = '\"';
    protected static final char ZERO = '0';
    protected static final char TAB = '\t';
    protected static final char CR = '\r';
    protected static final char LF = '\n';

    @Override
    public Secs2 parse(CharSequence cs) throws SmlParseException {
        String s = Objects.requireNonNull(cs).toString().trim();
        if (s.isEmpty()) {
            return Secs2.empty();
        }
        SeekValueResult r = this.parsing(s, 0);
        if (r.endIndex < s.length()) {
            throw new SmlParseException("SML not end. index: " + r.endIndex);
        }
        return r.value;
    }

    protected SeekValueResult parsing(String s, int fromIndex) throws SmlParseException {
        try {
            SeekCharResult r = this.seekAngleBranketBegin(s, fromIndex);
            SeekStringResult s2ir = this.seekSecs2ItemString(s, r.index + 1);
            SeekStringResult s2sr = this.seekSizeString(s, s2ir.endIndex);
            int nextIndex = s2sr.endIndex;
            String secs2ItemStr = s2ir.str;
            Secs2Item secs2Item = Secs2Item.symbol(secs2ItemStr);
            switch (secs2Item) {
                case LIST: {
                    return this.parseList(s, nextIndex);
                }
                case ASCII: {
                    return this.parseAscii(s, nextIndex);
                }
                case UNDEFINED: {
                    int size = -1;
                    if (!s2sr.str.isEmpty()) {
                        String ss = s2sr.str;
                        ss = ss.substring(1, ss.length() - 1);
                        try {
                            size = Integer.valueOf(ss);
                        }
                        catch (NumberFormatException e) {
                            throw new SmlParseException("size parse failed. index: " + s2ir.endIndex, e);
                        }
                    }
                    return this.parseExtend(s, nextIndex, secs2ItemStr, size);
                }
            }
            return this.parseDefaults(s, nextIndex, secs2Item);
        }
        catch (IndexOutOfBoundsException e) {
            throw new SmlParseException("parse failed. index: " + fromIndex, e);
        }
    }

    private SeekValueResult parseList(String str, int fromIndex) throws SmlParseException {
        ArrayList<Secs2> ll = new ArrayList<Secs2>();
        int i = fromIndex;
        while (true) {
            SeekCharResult r = this.seekNextChar(str, i);
            if (r.c == '>') {
                return new SeekValueResult(Secs2.list(ll), r.index + 1);
            }
            if (r.c != '<') break;
            SeekValueResult v = this.parsing(str, r.index);
            ll.add(v.value);
            i = v.endIndex;
        }
        throw new SmlParseException("List not found '<' or '>'. index: " + fromIndex);
    }

    private SeekValueResult parseAscii(String str, int fromIndex) throws SmlParseException {
        StringBuilder sb = new StringBuilder();
        int i = fromIndex;
        while (true) {
            String s;
            SeekCharResult x;
            SeekCharResult r = this.seekNextChar(str, i);
            if (r.c == '>') {
                return this.seekValueResult(Secs2.ascii(sb), r.index + 1);
            }
            if (r.c == '\"') {
                x = this.seekNextChar(str, r.index + 1, '\"');
                s = str.substring(r.index + 1, x.index);
                sb.append(s);
                i = x.index + 1;
                continue;
            }
            if (r.c != '0') break;
            x = this.seekNextChar(str, r.index + 1, '>', ' ', '\"', '\t', '\r', '\n');
            s = str.substring(r.index, x.index);
            byte[] bs = new byte[]{this.toByte(s)};
            sb.append(new String(bs, StandardCharsets.US_ASCII));
            i = x.index;
        }
        throw new SmlParseException("Ascii not found '\"' or '0' or '>'. index: " + fromIndex);
    }

    private SeekValueResult parseDefaults(String str, int fromIndex, Secs2Item secs2Item) throws SmlParseException {
        SeekCharResult r = this.seekAngleBranketEnd(str, fromIndex);
        String ss = str.substring(fromIndex, r.index).trim();
        String[] values = ss.isEmpty() ? new String[]{} : ss.split("\\s+");
        int endIndex = r.index + 1;
        switch (secs2Item) {
            case BOOLEAN: {
                ArrayList<Boolean> ll = new ArrayList<Boolean>();
                for (String v : values) {
                    if (v.equals("T") || v.equalsIgnoreCase("true")) {
                        ll.add(Boolean.TRUE);
                        continue;
                    }
                    if (v.equals("F") || v.equalsIgnoreCase("false")) {
                        ll.add(Boolean.FALSE);
                        continue;
                    }
                    try {
                        if (this.toByte(v) == 0) {
                            ll.add(Boolean.FALSE);
                            continue;
                        }
                        ll.add(Boolean.TRUE);
                    }
                    catch (SmlParseException e) {
                        throw new SmlParseException("BOOLEAN parse failed", e);
                    }
                }
                return this.seekValueResult(Secs2.bool(ll), endIndex);
            }
            case BINARY: {
                ArrayList<Byte> ll = new ArrayList<Byte>();
                for (String v : values) {
                    ll.add(this.toByte(v));
                }
                return this.seekValueResult(Secs2.binary(ll), endIndex);
            }
            case INT1: {
                ArrayList<BigInteger> ll = new ArrayList<BigInteger>();
                for (String v : values) {
                    ll.add(new BigInteger(v));
                }
                return this.seekValueResult(Secs2.int1(ll), endIndex);
            }
            case INT2: {
                ArrayList<BigInteger> ll = new ArrayList<BigInteger>();
                for (String v : values) {
                    ll.add(new BigInteger(v));
                }
                return this.seekValueResult(Secs2.int2(ll), endIndex);
            }
            case INT4: {
                ArrayList<BigInteger> ll = new ArrayList<BigInteger>();
                for (String v : values) {
                    ll.add(new BigInteger(v));
                }
                return this.seekValueResult(Secs2.int4(ll), endIndex);
            }
            case INT8: {
                ArrayList<BigInteger> ll = new ArrayList<BigInteger>();
                for (String v : values) {
                    ll.add(new BigInteger(v));
                }
                return this.seekValueResult(Secs2.int8(ll), endIndex);
            }
            case UINT1: {
                ArrayList<BigInteger> ll = new ArrayList<BigInteger>();
                for (String v : values) {
                    ll.add(new BigInteger(v).abs());
                }
                return this.seekValueResult(Secs2.uint1(ll), endIndex);
            }
            case UINT2: {
                ArrayList<BigInteger> ll = new ArrayList<BigInteger>();
                for (String v : values) {
                    ll.add(new BigInteger(v).abs());
                }
                return this.seekValueResult(Secs2.uint2(ll), endIndex);
            }
            case UINT4: {
                ArrayList<BigInteger> ll = new ArrayList<BigInteger>();
                for (String v : values) {
                    ll.add(new BigInteger(v).abs());
                }
                return this.seekValueResult(Secs2.uint4(ll), endIndex);
            }
            case UINT8: {
                ArrayList<BigInteger> ll = new ArrayList<BigInteger>();
                for (String v : values) {
                    ll.add(new BigInteger(v).abs());
                }
                return this.seekValueResult(Secs2.uint8(ll), endIndex);
            }
            case FLOAT4: {
                ArrayList<Float> ll = new ArrayList<Float>();
                for (String v : values) {
                    ll.add(Float.valueOf(v));
                }
                return this.seekValueResult(Secs2.float4(ll), endIndex);
            }
            case FLOAT8: {
                ArrayList<Double> ll = new ArrayList<Double>();
                for (String v : values) {
                    ll.add(Double.valueOf(v));
                }
                return this.seekValueResult(Secs2.float8(ll), endIndex);
            }
        }
        throw new SmlParseException("Unsupport Format " + (Object)((Object)secs2Item));
    }

    private Byte toByte(String value) throws SmlParseException {
        Matcher m = ptnByte.matcher(value);
        try {
            if (m.matches()) {
                int i = Integer.parseInt(m.group(GROUP_BYTE), 16);
                return (byte)i;
            }
            int i = Integer.parseInt(value, 10);
            return (byte)i;
        }
        catch (NumberFormatException e) {
            throw new SmlParseException("Binary parse failed \"" + value + "\"", e);
        }
    }

    protected SeekValueResult parseExtend(String str, int fromIndex, String secs2ItemString, int size) throws SmlParseException {
        throw new SmlParseException("UNKNOWN SECS2ITEM type: " + secs2ItemString);
    }

    protected SeekCharResult seekNextChar(String s, int fromIndex) {
        int i = fromIndex;
        char c;
        while ((c = s.charAt(i)) <= ' ') {
            ++i;
        }
        return this.seekCharResult(c, i);
    }

    protected SeekCharResult seekNextChar(String s, int fromIndex, char ... seekChar) {
        int i = fromIndex;
        while (true) {
            char c = s.charAt(i);
            for (char sc : seekChar) {
                if (c != sc) continue;
                return this.seekCharResult(c, i);
            }
            ++i;
        }
    }

    protected SeekCharResult seekAngleBranketBegin(String s, int fromIndex) {
        return this.seekNextChar(s, fromIndex, '<');
    }

    protected SeekCharResult seekAngleBranketEnd(String s, int fromIndex) {
        return this.seekNextChar(s, fromIndex, '>');
    }

    protected SeekStringResult seekSecs2ItemString(String s, int fromIndex) {
        SeekCharResult r = this.seekNextChar(s, fromIndex);
        int beginIndex = r.index;
        r = this.seekNextChar(s, beginIndex + 1, ' ', '<', '>', '[', '\"');
        int endIndex = r.index;
        return this.seekStringResult(s.substring(beginIndex, endIndex), endIndex);
    }

    protected SeekStringResult seekSizeString(String s, int fromIndex) {
        SeekCharResult r = this.seekNextChar(s, fromIndex);
        if (r.c != '[') {
            return this.seekStringResult("", fromIndex);
        }
        int beginIndex = r.index;
        r = this.seekNextChar(s, beginIndex + 1, ']');
        int endIndex = r.index + 1;
        return this.seekStringResult(s.substring(beginIndex, endIndex), endIndex);
    }

    protected final SeekCharResult seekCharResult(char c, int index) {
        return new SeekCharResult(c, index);
    }

    protected final SeekStringResult seekStringResult(String str, int endIndex) {
        return new SeekStringResult(str, endIndex);
    }

    protected final SeekValueResult seekValueResult(Secs2 value, int endIndex) {
        return new SeekValueResult(value, endIndex);
    }

    protected final class SeekValueResult {
        public final Secs2 value;
        public final int endIndex;

        private SeekValueResult(Secs2 value, int endIndex) {
            this.value = value;
            this.endIndex = endIndex;
        }
    }

    protected final class SeekStringResult {
        public final String str;
        public final int endIndex;

        private SeekStringResult(String str, int endIndex) {
            this.str = str;
            this.endIndex = endIndex;
        }
    }

    protected final class SeekCharResult {
        public final char c;
        public final int index;

        private SeekCharResult(char c, int index) {
            this.c = c;
            this.index = index;
        }
    }
}

