/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.AbstractSecs2Builder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Ascii;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Binary;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Boolean;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Builder;
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

public final class Secs2Builders {
    private Secs2Builders() {
    }

    public static Secs2Builder getInstance() {
        return SingletonHolder.inst;
    }

    public static Secs2RawBytes empty() {
        return Secs2Builders.getInstance().empty();
    }

    public static Secs2RawBytes raw(byte[] bs) {
        return Secs2Builders.getInstance().raw(bs);
    }

    public static Secs2List list() {
        return Secs2Builders.getInstance().list();
    }

    public static Secs2List list(Secs2 ... values) {
        return Secs2Builders.getInstance().list(values);
    }

    public static Secs2List list(List<? extends Secs2> values) {
        return Secs2Builders.getInstance().list(values);
    }

    public static Secs2Ascii ascii(CharSequence ascii) {
        return Secs2Builders.getInstance().ascii(ascii);
    }

    public static Secs2Binary binary() {
        return Secs2Builders.getInstance().binary();
    }

    public static Secs2Binary binary(byte ... bs) {
        return Secs2Builders.getInstance().binary(bs);
    }

    public static Secs2Binary binary(List<Byte> bs) {
        return Secs2Builders.getInstance().binary(bs);
    }

    public static Secs2Boolean bool() {
        return Secs2Builders.getInstance().bool();
    }

    public static Secs2Boolean bool(boolean ... bools) {
        return Secs2Builders.getInstance().bool(bools);
    }

    public static Secs2Boolean bool(List<Boolean> bools) {
        return Secs2Builders.getInstance().bool(bools);
    }

    public static Secs2Int1 int1() {
        return Secs2Builders.getInstance().int1();
    }

    public static Secs2Int1 int1(int ... values) {
        return Secs2Builders.getInstance().int1(values);
    }

    public static Secs2Int1 int1(long ... values) {
        return Secs2Builders.getInstance().int1(values);
    }

    public static Secs2Int1 int1(BigInteger ... values) {
        return Secs2Builders.getInstance().int1(values);
    }

    public static Secs2Int1 int1(List<? extends Number> values) {
        return Secs2Builders.getInstance().int1(values);
    }

    public static Secs2Int2 int2() {
        return Secs2Builders.getInstance().int2();
    }

    public static Secs2Int2 int2(int ... values) {
        return Secs2Builders.getInstance().int2(values);
    }

    public static Secs2Int2 int2(long ... values) {
        return Secs2Builders.getInstance().int2(values);
    }

    public static Secs2Int2 int2(BigInteger ... values) {
        return Secs2Builders.getInstance().int2(values);
    }

    public static Secs2Int2 int2(List<? extends Number> values) {
        return Secs2Builders.getInstance().int2(values);
    }

    public static Secs2Int4 int4() {
        return Secs2Builders.getInstance().int4();
    }

    public static Secs2Int4 int4(int ... values) {
        return Secs2Builders.getInstance().int4(values);
    }

    public static Secs2Int4 int4(long ... values) {
        return Secs2Builders.getInstance().int4(values);
    }

    public static Secs2Int4 int4(BigInteger ... values) {
        return Secs2Builders.getInstance().int4(values);
    }

    public static Secs2Int4 int4(List<? extends Number> values) {
        return Secs2Builders.getInstance().int4(values);
    }

    public static Secs2Int8 int8() {
        return Secs2Builders.getInstance().int8();
    }

    public static Secs2Int8 int8(int ... values) {
        return Secs2Builders.getInstance().int8(values);
    }

    public static Secs2Int8 int8(long ... values) {
        return Secs2Builders.getInstance().int8(values);
    }

    public static Secs2Int8 int8(BigInteger ... values) {
        return Secs2Builders.getInstance().int8(values);
    }

    public static Secs2Int8 int8(List<? extends Number> values) {
        return Secs2Builders.getInstance().int8(values);
    }

    public static Secs2Uint1 uint1() {
        return Secs2Builders.getInstance().uint1();
    }

    public static Secs2Uint1 uint1(int ... values) {
        return Secs2Builders.getInstance().uint1(values);
    }

    public static Secs2Uint1 uint1(long ... values) {
        return Secs2Builders.getInstance().uint1(values);
    }

    public static Secs2Uint1 uint1(BigInteger ... values) {
        return Secs2Builders.getInstance().uint1(values);
    }

    public static Secs2Uint1 uint1(List<? extends Number> values) {
        return Secs2Builders.getInstance().uint1(values);
    }

    public static Secs2Uint2 uint2() {
        return Secs2Builders.getInstance().uint2();
    }

    public static Secs2Uint2 uint2(int ... values) {
        return Secs2Builders.getInstance().uint2(values);
    }

    public static Secs2Uint2 uint2(long ... values) {
        return Secs2Builders.getInstance().uint2(values);
    }

    public static Secs2Uint2 uint2(BigInteger ... values) {
        return Secs2Builders.getInstance().uint2(values);
    }

    public static Secs2Uint2 uint2(List<? extends Number> values) {
        return Secs2Builders.getInstance().uint2(values);
    }

    public static Secs2Uint4 uint4() {
        return Secs2Builders.getInstance().uint4();
    }

    public static Secs2Uint4 uint4(int ... values) {
        return Secs2Builders.getInstance().uint4(values);
    }

    public static Secs2Uint4 uint4(long ... values) {
        return Secs2Builders.getInstance().uint4(values);
    }

    public static Secs2Uint4 uint4(BigInteger ... values) {
        return Secs2Builders.getInstance().uint4(values);
    }

    public static Secs2Uint4 uint4(List<? extends Number> values) {
        return Secs2Builders.getInstance().uint4(values);
    }

    public static Secs2Uint8 uint8() {
        return Secs2Builders.getInstance().uint8();
    }

    public static Secs2Uint8 uint8(int ... values) {
        return Secs2Builders.getInstance().uint8(values);
    }

    public static Secs2Uint8 uint8(long ... values) {
        return Secs2Builders.getInstance().uint8(values);
    }

    public static Secs2Uint8 uint8(BigInteger ... values) {
        return Secs2Builders.getInstance().uint8(values);
    }

    public static Secs2Uint8 uint8(List<? extends Number> values) {
        return Secs2Builders.getInstance().uint8(values);
    }

    public static Secs2Float4 float4() {
        return Secs2Builders.getInstance().float4();
    }

    public static Secs2Float4 float4(float ... values) {
        return Secs2Builders.getInstance().float4(values);
    }

    public static Secs2Float4 float4(List<? extends Number> values) {
        return Secs2Builders.getInstance().float4(values);
    }

    public static Secs2Float8 float8() {
        return Secs2Builders.getInstance().float8();
    }

    public static Secs2Float8 float8(double ... values) {
        return Secs2Builders.getInstance().float8(values);
    }

    public static Secs2Float8 float8(List<? extends Number> values) {
        return Secs2Builders.getInstance().float8(values);
    }

    private static class SingletonHolder {
        private static final Secs2Builder inst = new AbstractSecs2Builder(){};

        private SingletonHolder() {
        }
    }
}

