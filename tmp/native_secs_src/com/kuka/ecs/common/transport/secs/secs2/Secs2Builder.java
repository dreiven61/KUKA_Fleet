/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Ascii;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Binary;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Boolean;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Builders;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Float4;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Float8;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Int1;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Int2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Int4;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Int8;
import com.kuka.ecs.common.transport.secs.secs2.Secs2List;
import com.kuka.ecs.common.transport.secs.secs2.Secs2RawBytes;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Uint1;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Uint2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Uint4;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Uint8;
import java.math.BigInteger;
import java.util.List;

public interface Secs2Builder {
    public Secs2RawBytes empty();

    public Secs2RawBytes raw(byte[] var1);

    public Secs2List list();

    public Secs2List list(Secs2 ... var1);

    public Secs2List list(List<? extends Secs2> var1);

    public Secs2Ascii ascii(CharSequence var1);

    public Secs2Binary binary();

    public Secs2Binary binary(byte ... var1);

    public Secs2Binary binary(List<Byte> var1);

    public Secs2Boolean bool();

    public Secs2Boolean bool(boolean ... var1);

    public Secs2Boolean bool(List<Boolean> var1);

    public Secs2Int1 int1();

    public Secs2Int1 int1(int ... var1);

    public Secs2Int1 int1(long ... var1);

    public Secs2Int1 int1(BigInteger ... var1);

    public Secs2Int1 int1(List<? extends Number> var1);

    public Secs2Int2 int2();

    public Secs2Int2 int2(int ... var1);

    public Secs2Int2 int2(long ... var1);

    public Secs2Int2 int2(BigInteger ... var1);

    public Secs2Int2 int2(List<? extends Number> var1);

    public Secs2Int4 int4();

    public Secs2Int4 int4(int ... var1);

    public Secs2Int4 int4(long ... var1);

    public Secs2Int4 int4(BigInteger ... var1);

    public Secs2Int4 int4(List<? extends Number> var1);

    public Secs2Int8 int8();

    public Secs2Int8 int8(int ... var1);

    public Secs2Int8 int8(long ... var1);

    public Secs2Int8 int8(BigInteger ... var1);

    public Secs2Int8 int8(List<? extends Number> var1);

    public Secs2Uint1 uint1();

    public Secs2Uint1 uint1(int ... var1);

    public Secs2Uint1 uint1(long ... var1);

    public Secs2Uint1 uint1(BigInteger ... var1);

    public Secs2Uint1 uint1(List<? extends Number> var1);

    public Secs2Uint2 uint2();

    public Secs2Uint2 uint2(int ... var1);

    public Secs2Uint2 uint2(long ... var1);

    public Secs2Uint2 uint2(BigInteger ... var1);

    public Secs2Uint2 uint2(List<? extends Number> var1);

    public Secs2Uint4 uint4();

    public Secs2Uint4 uint4(int ... var1);

    public Secs2Uint4 uint4(long ... var1);

    public Secs2Uint4 uint4(BigInteger ... var1);

    public Secs2Uint4 uint4(List<? extends Number> var1);

    public Secs2Uint8 uint8();

    public Secs2Uint8 uint8(int ... var1);

    public Secs2Uint8 uint8(long ... var1);

    public Secs2Uint8 uint8(BigInteger ... var1);

    public Secs2Uint8 uint8(List<? extends Number> var1);

    public Secs2Float4 float4();

    public Secs2Float4 float4(float ... var1);

    public Secs2Float4 float4(List<? extends Number> var1);

    public Secs2Float8 float8();

    public Secs2Float8 float8(double ... var1);

    public Secs2Float8 float8(List<? extends Number> var1);

    public static Secs2Builder getInstance() {
        return Secs2Builders.getInstance();
    }
}

