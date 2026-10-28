/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Ascii;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Binary;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Boolean;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParser;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Float4;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Float8;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Int1;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Int2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Int4;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Int8;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Jis8;
import com.kuka.ecs.common.transport.secs.secs2.Secs2List;
import com.kuka.ecs.common.transport.secs.secs2.Secs2RawBytes;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Uint1;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Uint2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Uint4;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Uint8;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Unicode;
import com.kuka.ecs.common.transport.secs.secs2.Secs2UnsupportedDataFormatException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractSecs2BytesParser
implements Secs2BytesParser {
    @Override
    public Secs2 parse(List<byte[]> bss) throws Secs2BytesParseException {
        BytesPack pack = new BytesPack(bss);
        if (pack.hasRemaining()) {
            Secs2 ss = AbstractSecs2BytesParser.stpParse(pack);
            if (pack.hasRemaining()) {
                throw new Secs2BytesParseException("not reach end bytes");
            }
            return ss;
        }
        return new Secs2RawBytes();
    }

    private static Secs2 stpParse(BytesPack pack) throws Secs2BytesParseException {
        byte b = pack.get();
        Secs2Item s2i = Secs2Item.get(b);
        int lengthBits = b & 3;
        int size = 0;
        if (lengthBits == 3) {
            size = pack.get() << 16 & 0xFF0000;
            size |= pack.get() << 8 & 0xFF00;
            size |= pack.get() & 0xFF;
        } else if (lengthBits == 2) {
            size = pack.get() << 8 & 0xFF00;
            size |= pack.get() & 0xFF;
        } else if (lengthBits == 1) {
            size = pack.get() & 0xFF;
        }
        if (s2i == Secs2Item.LIST) {
            ArrayList<Secs2> ll = new ArrayList<Secs2>();
            for (int i = 0; i < size; ++i) {
                ll.add(AbstractSecs2BytesParser.stpParse(pack));
            }
            return new Secs2List(ll);
        }
        byte[] bs = pack.get(size);
        switch (s2i) {
            case ASCII: {
                return new Secs2Ascii(bs);
            }
            case BINARY: {
                return new Secs2Binary(bs);
            }
            case BOOLEAN: {
                return new Secs2Boolean(bs);
            }
            case INT1: {
                return new Secs2Int1(bs);
            }
            case INT2: {
                return new Secs2Int2(bs);
            }
            case INT4: {
                return new Secs2Int4(bs);
            }
            case INT8: {
                return new Secs2Int8(bs);
            }
            case UINT1: {
                return new Secs2Uint1(bs);
            }
            case UINT2: {
                return new Secs2Uint2(bs);
            }
            case UINT4: {
                return new Secs2Uint4(bs);
            }
            case UINT8: {
                return new Secs2Uint8(bs);
            }
            case FLOAT4: {
                return new Secs2Float4(bs);
            }
            case FLOAT8: {
                return new Secs2Float8(bs);
            }
            case JIS8: {
                return new Secs2Jis8(bs);
            }
            case UNICODE: {
                return new Secs2Unicode(bs);
            }
        }
        throw new Secs2UnsupportedDataFormatException();
    }

    private static class BytesPack {
        private final List<byte[]> bss;
        private final int mPack;
        private int mBytes;
        private int iPack;
        private int iBytes;

        private BytesPack(List<byte[]> bss) {
            this.bss = bss.stream().filter(bs -> ((byte[])bs).length > 0).collect(Collectors.toList());
            this.mPack = this.bss.size() - 1;
            this.mBytes = this.mPack < 0 ? -1 : this.bss.get(0).length - 1;
            this.iPack = 0;
            this.iBytes = 0;
        }

        public boolean hasRemaining() {
            if (this.iPack < this.mPack) {
                return true;
            }
            return this.iPack == this.mPack && this.iBytes <= this.mBytes;
        }

        public byte get() throws Secs2BytesParseException {
            while (this.iPack <= this.mPack) {
                if (this.iBytes > this.mBytes) {
                    ++this.iPack;
                    if (this.iPack > this.mPack) continue;
                    this.iBytes = 0;
                    this.mBytes = this.bss.get(this.iPack).length - 1;
                    continue;
                }
                byte b = this.bss.get(this.iPack)[this.iBytes];
                ++this.iBytes;
                return b;
            }
            throw new Secs2BytesParseException("reach end bytes");
        }

        public byte[] get(int size) throws Secs2BytesParseException {
            byte[] bs = new byte[size];
            for (int i = 0; i < size; ++i) {
                bs[i] = this.get();
            }
            return bs;
        }
    }
}

