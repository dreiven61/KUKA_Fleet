/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.AbstractSecsCommunicator;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.gem.ACKC10;
import com.kuka.ecs.common.transport.secs.gem.ACKC5;
import com.kuka.ecs.common.transport.secs.gem.ACKC6;
import com.kuka.ecs.common.transport.secs.gem.ACKC7;
import com.kuka.ecs.common.transport.secs.gem.AbstractGemConfig;
import com.kuka.ecs.common.transport.secs.gem.CEED;
import com.kuka.ecs.common.transport.secs.gem.CMDA;
import com.kuka.ecs.common.transport.secs.gem.COMMACK;
import com.kuka.ecs.common.transport.secs.gem.Clock;
import com.kuka.ecs.common.transport.secs.gem.DRACK;
import com.kuka.ecs.common.transport.secs.gem.DynamicCollectionEvent;
import com.kuka.ecs.common.transport.secs.gem.DynamicEventReportConfig;
import com.kuka.ecs.common.transport.secs.gem.DynamicReport;
import com.kuka.ecs.common.transport.secs.gem.ERACK;
import com.kuka.ecs.common.transport.secs.gem.GRANT;
import com.kuka.ecs.common.transport.secs.gem.GRANT6;
import com.kuka.ecs.common.transport.secs.gem.Gem;
import com.kuka.ecs.common.transport.secs.gem.LRACK;
import com.kuka.ecs.common.transport.secs.gem.OFLACK;
import com.kuka.ecs.common.transport.secs.gem.ONLACK;
import com.kuka.ecs.common.transport.secs.gem.TIACK;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import com.kuka.ecs.common.transport.secs.secs2.Secs2List;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractGem
implements Gem {
    private final AbstractSecsCommunicator comm;
    private final AbstractGemConfig config;
    private final AtomicLong autoDataId = new AtomicLong(0L);
    private final AtomicLong autoReportId = new AtomicLong(0L);

    public AbstractGem(AbstractSecsCommunicator communicator, AbstractGemConfig config) {
        this.comm = communicator;
        this.config = config;
    }

    protected Secs2 createSecs2Number(Secs2Item item, long ... values) {
        switch (item) {
            case INT1: {
                return Secs2.int1(values);
            }
            case INT2: {
                return Secs2.int2(values);
            }
            case INT4: {
                return Secs2.int4(values);
            }
            case INT8: {
                return Secs2.int8(values);
            }
            case UINT1: {
                return Secs2.uint1(values);
            }
            case UINT2: {
                return Secs2.uint2(values);
            }
            case UINT4: {
                return Secs2.uint4(values);
            }
            case UINT8: {
                return Secs2.uint8(values);
            }
        }
        throw new IllegalArgumentException("Not support " + item.toString());
    }

    @Override
    public Secs2 autoDataId() {
        return this.dataId(this.autoDataId.incrementAndGet());
    }

    @Override
    public Secs2 dataId(long id) {
        return this.createSecs2Number(this.config.dataIdSecs2Item().get(), id);
    }

    protected Secs2 autoReportId() {
        return this.reportId(this.autoReportId.incrementAndGet());
    }

    protected Secs2 reportId(long id) {
        return this.createSecs2Number(this.config.reportIdSecs2Item().get(), id);
    }

    protected Secs2 vId(long id) {
        return this.createSecs2Number(this.config.vIdSecs2Item().get(), id);
    }

    protected Secs2 collectionEventId(long id) {
        return this.createSecs2Number(this.config.collectionEventIdSecs2Item().get(), id);
    }

    @Override
    public DynamicEventReportConfig newDynamicEventReportConfig() {
        return DynamicEventReportConfig.newInstance(this);
    }

    @Override
    public Optional<SecsMessage> s1f1() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.comm.send(1, 1, true);
    }

    @Override
    public Optional<SecsMessage> s1f2(SecsMessage primaryMsg) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        Secs2List ss = this.comm.isEquip() ? Secs2.list(Secs2.ascii(this.config.mdln().get()), Secs2.ascii(this.config.softrev().get())) : Secs2.list();
        return this.comm.send(primaryMsg, 1, 2, false, ss);
    }

    @Override
    public Optional<SecsMessage> s1f4(SecsMessage secsMessage, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.comm.send(secsMessage, 1, 4, false, secs2);
    }

    @Override
    public COMMACK s1f13() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        Secs2List ss = this.comm.isEquip() ? Secs2.list(Secs2.ascii(this.config.mdln().get()), Secs2.ascii(this.config.softrev().get())) : Secs2.list();
        Secs2 r = this.comm.send(1, 13, true, ss).filter(msg -> msg.getStream() == 1).filter(msg -> msg.getFunction() == 14).map(msg -> msg.secs2()).orElseThrow(() -> new Secs2Exception("S1F14 parse failed"));
        return COMMACK.get(r.get(0));
    }

    @Override
    public Optional<SecsMessage> s1f14(SecsMessage primaryMsg, COMMACK commack) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        Secs2List ss = this.comm.isEquip() ? Secs2.list(Secs2.ascii(this.config.mdln().get()), Secs2.ascii(this.config.softrev().get())) : Secs2.list();
        return this.comm.send(primaryMsg, 1, 14, false, Secs2.list(commack.secs2(), ss));
    }

    @Override
    public OFLACK s1f15() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        Secs2 ss = this.comm.send(1, 15, true).filter(msg -> msg.getStream() == 1).filter(msg -> msg.getFunction() == 16).map(msg -> msg.secs2()).orElseThrow(() -> new Secs2Exception("S1F16 parse failed"));
        return OFLACK.get(ss);
    }

    @Override
    public Optional<SecsMessage> s1f16(SecsMessage primaryMsg) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 1, 16, false, OFLACK.OK.secs2());
    }

    @Override
    public ONLACK s1f17() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        Secs2 ss = this.comm.send(1, 17, true).filter(msg -> msg.getStream() == 1).filter(msg -> msg.getFunction() == 18).map(msg -> msg.secs2()).orElseThrow(() -> new Secs2Exception("S1F18 parse failed"));
        return ONLACK.get(ss);
    }

    @Override
    public Optional<SecsMessage> s1f18(SecsMessage primaryMsg, ONLACK onlack) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 1, 18, false, onlack.secs2());
    }

    private Secs2 getClockSecs2(Clock c) {
        switch (this.config.clockType().get()) {
            case A12: {
                return c.toAscii12();
            }
            case A16: {
                return c.toAscii16();
            }
        }
        return Secs2.empty();
    }

    @Override
    public Clock s2f17() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        Secs2 ss = this.comm.send(2, 17, true).filter(msg -> msg.getStream() == 2).filter(msg -> msg.getFunction() == 18).map(msg -> msg.secs2()).orElseThrow(() -> new Secs2Exception("s2f18 parse failed"));
        return Clock.from(ss);
    }

    @Override
    public Optional<SecsMessage> s2f18(SecsMessage primaryMsg, Clock c) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return this.s2f18(primaryMsg, this.getClockSecs2(c));
    }

    @Override
    public Optional<SecsMessage> s2f18Now(SecsMessage primaryMsg) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return this.s2f18(primaryMsg, Clock.now());
    }

    private Optional<SecsMessage> s2f18(SecsMessage primaryMsg, Secs2 ss) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 2, 18, false, ss);
    }

    @Override
    public Optional<SecsMessage> s2f22(SecsMessage primaryMsg, CMDA cmda) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 2, 22, false, cmda.secs2());
    }

    @Override
    public Optional<SecsMessage> s2f28(SecsMessage primaryMsg, CMDA cmda) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 2, 28, false, cmda.secs2());
    }

    @Override
    public TIACK s2f31(Clock c) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        Secs2 ss = this.comm.send(2, 31, true, this.getClockSecs2(c)).filter(msg -> msg.getStream() == 2).filter(msg -> msg.getFunction() == 32).map(msg -> msg.secs2()).orElseThrow(() -> new Secs2Exception("s2f32 parse failed"));
        return TIACK.get(ss);
    }

    @Override
    public TIACK s2f31Now() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return this.s2f31(Clock.now());
    }

    @Override
    public Optional<SecsMessage> s2f32(SecsMessage primaryMsg, TIACK tiack) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 2, 32, false, tiack.secs2());
    }

    @Override
    public DRACK s2f33DeleteAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return this.s2f33Inner(Collections.emptyList());
    }

    @Override
    public DRACK s2f33Define(DynamicEventReportConfig config) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return config.s2f33Define();
    }

    protected DRACK s2f33Inner(List<? extends Secs2> reports) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        Secs2List ss = Secs2.list(this.autoDataId(), Secs2.list(reports));
        Secs2 r = this.comm.send(2, 33, true, ss).filter(msg -> msg.getStream() == 2).filter(msg -> msg.getFunction() == 34).map(msg -> msg.secs2()).orElseThrow(() -> new Secs2Exception("s2f34 parse failed"));
        return DRACK.get(r);
    }

    @Override
    public Optional<SecsMessage> s2f34(SecsMessage primaryMsg, DRACK drack) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 2, 34, false, drack.secs2());
    }

    @Override
    public LRACK s2f35(DynamicEventReportConfig config) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return config.s2f35();
    }

    protected LRACK s2f35Inner(List<? extends Secs2> links) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        Secs2List ss = Secs2.list(this.autoDataId(), Secs2.list(links));
        Secs2 r = this.comm.send(2, 35, true, ss).filter(msg -> msg.getStream() == 2).filter(msg -> msg.getFunction() == 36).map(msg -> msg.secs2()).orElseThrow(() -> new Secs2Exception("s2f36 parse failed"));
        return LRACK.get(r);
    }

    @Override
    public Optional<SecsMessage> s2f36(SecsMessage primaryMsg, LRACK lrack) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 2, 36, false, lrack.secs2());
    }

    @Override
    public ERACK s2f37DisableAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return this.s2f37Inner(CEED.DISABLE, Collections.emptyList());
    }

    @Override
    public ERACK s2f37EnableAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return this.s2f37Inner(CEED.ENABLE, Collections.emptyList());
    }

    @Override
    public ERACK s2f37Enable(DynamicEventReportConfig config) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return config.s2f37Enable();
    }

    protected ERACK s2f37Inner(CEED ceed, List<? extends Secs2> ces) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        Secs2List ss = Secs2.list(ceed.secs2(), Secs2.list(ces));
        Secs2 r = this.comm.send(2, 37, true, ss).filter(msg -> msg.getStream() == 2).filter(msg -> msg.getFunction() == 38).map(msg -> msg.secs2()).orElseThrow(() -> new Secs2Exception("s2f38 parse failed"));
        return ERACK.get(r);
    }

    @Override
    public Optional<SecsMessage> s2f38(SecsMessage primaryMsg, ERACK erack) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 2, 38, false, erack.secs2());
    }

    @Override
    public Optional<SecsMessage> s2f40(SecsMessage primaryMsg, GRANT grant) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 2, 40, false, grant.secs2());
    }

    @Override
    public Optional<SecsMessage> s3f16(SecsMessage primaryMsg, GRANT grant) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 3, 16, false, grant.secs2());
    }

    @Override
    public Optional<SecsMessage> s5f2(SecsMessage primaryMsg, ACKC5 ackc5) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 5, 2, false, ackc5.secs2());
    }

    @Override
    public Optional<SecsMessage> s5f4(SecsMessage primaryMsg, ACKC5 ackc5) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 5, 4, false, ackc5.secs2());
    }

    @Override
    public Optional<SecsMessage> s6f2(SecsMessage primaryMsg, ACKC6 ackc6) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s6fx(primaryMsg, 2, ackc6);
    }

    @Override
    public Optional<SecsMessage> s6f4(SecsMessage primaryMsg, ACKC6 ackc6) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s6fx(primaryMsg, 4, ackc6);
    }

    @Override
    public Optional<SecsMessage> s6f6(SecsMessage primaryMsg, GRANT6 grant6) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 6, 6, false, grant6.secs2());
    }

    @Override
    public Optional<SecsMessage> s6f10(SecsMessage primaryMsg, ACKC6 ackc6) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s6fx(primaryMsg, 10, ackc6);
    }

    @Override
    public Optional<SecsMessage> s6f12(SecsMessage primaryMsg, ACKC6 ackc6) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s6fx(primaryMsg, 12, ackc6);
    }

    @Override
    public Optional<SecsMessage> s6f14(SecsMessage primaryMsg, ACKC6 ackc6) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s6fx(primaryMsg, 14, ackc6);
    }

    @Override
    public Optional<SecsMessage> s6f15(DynamicCollectionEvent ce) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.comm.send(6, 15, true, ce.collectionEventId());
    }

    @Override
    public Optional<SecsMessage> s6f17(DynamicCollectionEvent ce) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.comm.send(6, 17, true, ce.collectionEventId());
    }

    @Override
    public Optional<SecsMessage> s6f19(DynamicReport report) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.comm.send(6, 19, true, report.reportId());
    }

    @Override
    public Optional<SecsMessage> s6f21(DynamicReport report) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.comm.send(6, 21, true, report.reportId());
    }

    @Override
    public Optional<SecsMessage> s6f26(SecsMessage primaryMsg, ACKC6 ackc6) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s6fx(primaryMsg, 26, ackc6);
    }

    private Optional<SecsMessage> s6fx(SecsMessage primaryMsg, int func, ACKC6 ackc6) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 6, func, false, ackc6.secs2());
    }

    @Override
    public Optional<SecsMessage> s7f4(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 4, ackc7);
    }

    @Override
    public Optional<SecsMessage> s7f12(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 12, ackc7);
    }

    @Override
    public Optional<SecsMessage> s7f14(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 14, ackc7);
    }

    @Override
    public Optional<SecsMessage> s7f16(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 16, ackc7);
    }

    @Override
    public Optional<SecsMessage> s7f18(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 18, ackc7);
    }

    @Override
    public Optional<SecsMessage> s7f24(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 24, ackc7);
    }

    @Override
    public Optional<SecsMessage> s7f32(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 32, ackc7);
    }

    @Override
    public Optional<SecsMessage> s7f38(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 38, ackc7);
    }

    @Override
    public Optional<SecsMessage> s7f40(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 40, ackc7);
    }

    @Override
    public Optional<SecsMessage> s7f42(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 42, ackc7);
    }

    @Override
    public Optional<SecsMessage> s7f44(SecsMessage primaryMsg, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s7fx(primaryMsg, 44, ackc7);
    }

    private Optional<SecsMessage> s7fx(SecsMessage primaryMsg, int func, ACKC7 ackc7) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 7, func, false, ackc7.secs2());
    }

    @Override
    public Optional<SecsMessage> s9f1(SecsMessage refMsg) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s9fx(refMsg, 1);
    }

    @Override
    public Optional<SecsMessage> s9f3(SecsMessage refMsg) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s9fx(refMsg, 3);
    }

    @Override
    public Optional<SecsMessage> s9f5(SecsMessage refMsg) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s9fx(refMsg, 5);
    }

    @Override
    public Optional<SecsMessage> s9f7(SecsMessage refMsg) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s9fx(refMsg, 7);
    }

    @Override
    public Optional<SecsMessage> s9f9(SecsMessage refMsg) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s9fx(refMsg, 9);
    }

    @Override
    public Optional<SecsMessage> s9f11(SecsMessage refMsg) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s9fx(refMsg, 11);
    }

    private Optional<SecsMessage> s9fx(SecsMessage refMsg, int func) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.comm.send(9, func, false, Secs2.binary(refMsg.header10Bytes()));
    }

    @Override
    public Optional<SecsMessage> s10f2(SecsMessage primaryMsg, ACKC10 ackc10) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s10fx(primaryMsg, 2, ackc10);
    }

    @Override
    public Optional<SecsMessage> s10f4(SecsMessage primaryMsg, ACKC10 ackc10) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s10fx(primaryMsg, 4, ackc10);
    }

    @Override
    public Optional<SecsMessage> s10f6(SecsMessage primaryMsg, ACKC10 ackc10) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s10fx(primaryMsg, 6, ackc10);
    }

    @Override
    public Optional<SecsMessage> s10f10(SecsMessage primaryMsg, ACKC10 ackc10) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.s10fx(primaryMsg, 10, ackc10);
    }

    private Optional<SecsMessage> s10fx(SecsMessage primaryMsg, int func, ACKC10 ackc10) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(10, func, false, ackc10.secs2());
    }

    @Override
    public Optional<SecsMessage> s13f12(SecsMessage primaryMsg, GRANT grant) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 13, 12, false, grant.secs2());
    }

    @Override
    public Optional<SecsMessage> s2f50(SecsMessage primaryMsg, CMDA cmda) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        if (!primaryMsg.wbit()) {
            return Optional.empty();
        }
        return this.comm.send(primaryMsg, 2, 50, false, cmda.secs2());
    }
}

