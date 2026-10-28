/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Communicator;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1Message;
import com.kuka.ecs.common.transport.secs.secs1.AbstractSecs1MessageBlock;
import com.kuka.ecs.common.transport.secs.secs1.Secs1Message;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBuilder;
import com.kuka.ecs.common.transport.secs.secs1.Secs1TooBigSendMessageException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BuildException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesPackBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParseException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesParsers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class AbstractSecs1MessageBuilder
implements Secs1MessageBuilder {
    private final AbstractSecs1Communicator comm;
    private final AtomicInteger autoNumber = new AtomicInteger(0);

    public AbstractSecs1MessageBuilder(AbstractSecs1Communicator communicator) {
        this.comm = communicator;
    }

    protected byte[] getAutoNumber2Bytes() {
        int n = this.autoNumber.incrementAndGet();
        return new byte[]{(byte)(n >> 8), (byte)n};
    }

    protected byte[] getDeviceId2Bytes() {
        int n = this.comm.deviceId();
        return new byte[]{(byte)(n >> 8 & 0x7F), (byte)n};
    }

    protected byte[] getSystem4Bytes() {
        byte[] aa = this.getAutoNumber2Bytes();
        if (this.comm.isEquip()) {
            byte[] ss = this.getDeviceId2Bytes();
            return new byte[]{ss[0], ss[1], aa[0], aa[1]};
        }
        return new byte[]{0, 0, aa[0], aa[1]};
    }

    @Override
    public AbstractSecs1Message build(int strm, int func, boolean wbit) throws Secs1TooBigSendMessageException {
        return this.build(strm, func, wbit, Secs2.empty());
    }

    @Override
    public AbstractSecs1Message build(int strm, int func, boolean wbit, Secs2 body) throws Secs1TooBigSendMessageException {
        byte[] dd = this.getDeviceId2Bytes();
        byte[] ssss = this.getSystem4Bytes();
        byte[] header = new byte[]{dd[0], dd[1], (byte)(strm & 0x7F), (byte)func, 0, 0, ssss[0], ssss[1], ssss[2], ssss[3]};
        if (this.comm.isEquip()) {
            header[0] = (byte)(header[0] | 0xFFFFFF80);
        }
        if (wbit) {
            header[2] = (byte)(header[2] | 0xFFFFFF80);
        }
        return AbstractSecs1MessageBuilder.build(header, body);
    }

    @Override
    public AbstractSecs1Message build(SecsMessage primaryMsg, int strm, int func, boolean wbit) throws Secs1TooBigSendMessageException {
        return this.build(primaryMsg, strm, func, wbit, Secs2.empty());
    }

    @Override
    public AbstractSecs1Message build(SecsMessage primaryMsg, int strm, int func, boolean wbit, Secs2 body) throws Secs1TooBigSendMessageException {
        byte[] dd = this.getDeviceId2Bytes();
        byte[] ppbb = primaryMsg.header10Bytes();
        byte[] header = new byte[]{dd[0], dd[1], (byte)(strm & 0x7F), (byte)func, 0, 0, ppbb[6], ppbb[7], ppbb[8], ppbb[9]};
        if (this.comm.isEquip()) {
            header[0] = (byte)(header[0] | 0xFFFFFF80);
        }
        if (wbit) {
            header[2] = (byte)(header[2] | 0xFFFFFF80);
        }
        return AbstractSecs1MessageBuilder.build(header, body);
    }

    public static AbstractSecs1Message build(byte[] header) throws Secs1TooBigSendMessageException {
        return AbstractSecs1MessageBuilder.build(header, Secs2.empty());
    }

    public static AbstractSecs1Message build(byte[] header, Secs2 body) throws Secs1TooBigSendMessageException {
        final ArrayList<AbstractSecs1MessageBlock> blocks = new ArrayList<AbstractSecs1MessageBlock>();
        AbstractSecs1Message o = new AbstractSecs1Message(header, body){
            private static final long serialVersionUID = 4267142316046169877L;

            @Override
            public List<AbstractSecs1MessageBlock> toAbstractBlocks() {
                return Collections.unmodifiableList(blocks);
            }
        };
        try {
            List<byte[]> ll = Secs2BytesPackBuilder.build(244, body).getBytes();
            if (ll.size() > 32766) {
                throw new Secs1TooBigSendMessageException(o);
            }
            int blockNum = 1;
            int m = ll.size() - 1;
            for (int i = 0; i < m; ++i) {
                blocks.add(AbstractSecs1MessageBuilder.buildBlock(header, ll.get(i), false, blockNum));
                ++blockNum;
            }
            blocks.add(AbstractSecs1MessageBuilder.buildBlock(header, ll.get(m), true, blockNum));
            return o;
        }
        catch (Secs2BuildException e) {
            throw new Secs1TooBigSendMessageException(o, (Throwable)e);
        }
    }

    private static AbstractSecs1MessageBlock buildBlock(byte[] header, byte[] body, boolean ebit, int blockNumber) {
        int pos;
        int len = header.length + body.length;
        int sum = 0;
        byte[] bs = new byte[len + 3];
        bs[0] = (byte)len;
        bs[1] = header[0];
        bs[2] = header[1];
        bs[3] = header[2];
        bs[4] = header[3];
        bs[5] = (byte)(blockNumber >> 8);
        if (ebit) {
            bs[5] = (byte)(bs[5] | 0xFFFFFF80);
        }
        bs[6] = (byte)blockNumber;
        bs[7] = header[6];
        bs[8] = header[7];
        bs[9] = header[8];
        bs[10] = header[9];
        for (pos = 1; pos < 11; ++pos) {
            sum += bs[pos] & 0xFF;
        }
        int m = body.length;
        for (int i = 0; i < m; ++i) {
            byte b;
            bs[pos] = b = body[i];
            sum += b & 0xFF;
            ++pos;
        }
        bs[pos] = (byte)(sum >> 8);
        bs[pos + 1] = (byte)sum;
        return new AbstractSecs1MessageBlock(bs){
            private static final long serialVersionUID = -3592481525438106452L;
        };
    }

    public static AbstractSecs1Message fromBlocks(List<? extends AbstractSecs1MessageBlock> blocks) throws Secs2BytesParseException {
        final ArrayList<? extends AbstractSecs1MessageBlock> refs = new ArrayList<AbstractSecs1MessageBlock>(blocks);
        byte[] header = Arrays.copyOfRange(blocks.get(blocks.size() - 1).getBytes(), 1, 11);
        List<byte[]> bss = blocks.stream().map(AbstractSecs1MessageBlock::getBytes).map(bs -> Arrays.copyOfRange(bs, 11, ((byte[])bs).length - 2)).collect(Collectors.toList());
        Secs2 body = Secs2BytesParsers.parse(bss);
        return new AbstractSecs1Message(header, body){
            private static final long serialVersionUID = -338757353807161473L;

            @Override
            public List<AbstractSecs1MessageBlock> toAbstractBlocks() {
                return Collections.unmodifiableList(refs);
            }
        };
    }

    public static AbstractSecs1Message fromMessage(Secs1Message msg) throws Secs1TooBigSendMessageException {
        if (msg instanceof AbstractSecs1Message) {
            return (AbstractSecs1Message)msg;
        }
        return AbstractSecs1MessageBuilder.build(msg.header10Bytes(), msg.secs2());
    }
}

