/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import java.util.Objects;

public enum Secs2Item {
    UNDEFINED(-1, -1, "UNDEFINED"),
    LIST(0, -1, "L"),
    BINARY(32, 1, "B"),
    BOOLEAN(36, 1, "BOOLEAN"),
    ASCII(64, 1, "A"),
    JIS8(68, 1, "J"),
    UNICODE(72, 2, "UNICODE"),
    INT8(96, 8, "I8"),
    INT1(100, 1, "I1"),
    INT2(104, 2, "I2"),
    INT4(112, 4, "I4"),
    FLOAT8(-128, 8, "F8"),
    FLOAT4(-112, 4, "F4"),
    UINT8(-96, 8, "U8"),
    UINT1(-92, 1, "U1"),
    UINT2(-88, 2, "U2"),
    UINT4(-80, 4, "U4");

    private final byte code;
    private final int size;
    private final String symbol;

    private Secs2Item(byte itemCode, int itemSize, String itemSymbol) {
        this.code = itemCode;
        this.size = itemSize;
        this.symbol = itemSymbol;
    }

    protected byte code() {
        return this.code;
    }

    protected int size() {
        return this.size;
    }

    protected String symbol() {
        return this.symbol;
    }

    public static Secs2Item get(byte itemCode) {
        byte b = (byte)(itemCode & 0xFC);
        for (Secs2Item item : Secs2Item.values()) {
            if (b != item.code) continue;
            return item;
        }
        return UNDEFINED;
    }

    public static Secs2Item symbol(CharSequence symbol) {
        Objects.requireNonNull(symbol);
        String s = symbol.toString();
        for (Secs2Item item : Secs2Item.values()) {
            if (!s.equalsIgnoreCase(item.symbol)) continue;
            return item;
        }
        return UNDEFINED;
    }
}

