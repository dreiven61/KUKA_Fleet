/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.AbstractSecs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BuildException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesPackBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Secs2Ascii
extends AbstractSecs2 {
    private static final Logger log = LoggerFactory.getLogger(Secs2Ascii.class);
    private static final long serialVersionUID = -1511335317630097868L;
    private static Secs2Item secs2Item = Secs2Item.ASCII;
    private static Charset charset = StandardCharsets.US_ASCII;
    private String ascii;
    private byte[] bytes;
    private static final byte BACKSLASH = 92;
    private static final byte[] BS = new byte[]{92, 98};
    private static final byte[] HT = new byte[]{92, 116};
    private static final byte[] LF = new byte[]{92, 110};
    private static final byte[] FF = new byte[]{92, 102};
    private static final byte[] CR = new byte[]{92, 114};

    public Secs2Ascii(CharSequence cs) {
        this.ascii = Objects.requireNonNull(cs).toString();
        this.bytes = null;
    }

    public Secs2Ascii(byte[] bs) {
        Objects.requireNonNull(bs);
        this.ascii = null;
        this.bytes = Arrays.copyOf(bs, bs.length);
    }

    @Override
    public int size() {
        return this.ascii().length();
    }

    @Override
    protected void putBytesPack(Secs2BytesPackBuilder builder) throws Secs2BuildException {
        this.putHeadAndBodyBytesToBytesPack(builder, this.bytes());
    }

    private synchronized String ascii() {
        if (this.ascii == null) {
            this.ascii = new String(this.bytes, charset);
        }
        return this.ascii;
    }

    private synchronized byte[] bytes() {
        if (this.bytes == null) {
            this.bytes = this.ascii.getBytes(charset);
        }
        return this.bytes;
    }

    @Override
    public String getAscii() throws Secs2Exception {
        return this.ascii();
    }

    @Override
    public Secs2Item secs2Item() {
        return secs2Item;
    }

    @Override
    protected String toJsonValue() {
        return "\"" + Secs2Ascii.escapeJsonString(this.ascii()) + "\"";
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static final String escapeJsonString(CharSequence cs) {
        try (ByteArrayOutputStream strm = new ByteArrayOutputStream();){
            byte[] bb;
            for (byte b : bb = cs.toString().getBytes(StandardCharsets.US_ASCII)) {
                if (b == 8) {
                    strm.write(BS);
                    continue;
                }
                if (b == 9) {
                    strm.write(HT);
                    continue;
                }
                if (b == 10) {
                    strm.write(LF);
                    continue;
                }
                if (b == 12) {
                    strm.write(FF);
                    continue;
                }
                if (b == 13) {
                    strm.write(CR);
                    continue;
                }
                if (b == 34 || b == 47 || b == 92) {
                    strm.write(92);
                    strm.write(b);
                    continue;
                }
                strm.write(b);
            }
            String string = new String(strm.toByteArray(), StandardCharsets.US_ASCII);
            return string;
        }
        catch (IOException notHappen) {
            log.error(notHappen.getMessage());
            return "";
        }
    }

    @Override
    protected String toStringValue() {
        return "\"" + this.ascii() + "\"";
    }
}

