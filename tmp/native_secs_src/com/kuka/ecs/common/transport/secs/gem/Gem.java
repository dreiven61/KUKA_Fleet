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
import com.kuka.ecs.common.transport.secs.gem.AbstractGem;
import com.kuka.ecs.common.transport.secs.gem.AbstractGemConfig;
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
import com.kuka.ecs.common.transport.secs.gem.LRACK;
import com.kuka.ecs.common.transport.secs.gem.OFLACK;
import com.kuka.ecs.common.transport.secs.gem.ONLACK;
import com.kuka.ecs.common.transport.secs.gem.TIACK;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import java.util.Optional;

public interface Gem {
    public static Gem newInstance(AbstractSecsCommunicator communicator, AbstractGemConfig config) {
        return new AbstractGem(communicator, config){};
    }

    public Secs2 autoDataId();

    public Secs2 dataId(long var1);

    public DynamicEventReportConfig newDynamicEventReportConfig();

    public Optional<SecsMessage> s1f1() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s1f2(SecsMessage var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public COMMACK s1f13() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<SecsMessage> s1f4(SecsMessage var1, Secs2 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s1f14(SecsMessage var1, COMMACK var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public OFLACK s1f15() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<SecsMessage> s1f16(SecsMessage var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public ONLACK s1f17() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<SecsMessage> s1f18(SecsMessage var1, ONLACK var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Clock s2f17() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<SecsMessage> s2f18(SecsMessage var1, Clock var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<SecsMessage> s2f18Now(SecsMessage var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<SecsMessage> s2f22(SecsMessage var1, CMDA var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s2f28(SecsMessage var1, CMDA var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public TIACK s2f31(Clock var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public TIACK s2f31Now() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<SecsMessage> s2f32(SecsMessage var1, TIACK var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public DRACK s2f33DeleteAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public DRACK s2f33Define(DynamicEventReportConfig var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<SecsMessage> s2f34(SecsMessage var1, DRACK var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public LRACK s2f35(DynamicEventReportConfig var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<SecsMessage> s2f36(SecsMessage var1, LRACK var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public ERACK s2f37DisableAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public ERACK s2f37EnableAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public ERACK s2f37Enable(DynamicEventReportConfig var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<SecsMessage> s2f38(SecsMessage var1, ERACK var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s2f40(SecsMessage var1, GRANT var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s3f16(SecsMessage var1, GRANT var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s5f2(SecsMessage var1, ACKC5 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s5f4(SecsMessage var1, ACKC5 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f2(SecsMessage var1, ACKC6 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f4(SecsMessage var1, ACKC6 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f6(SecsMessage var1, GRANT6 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f10(SecsMessage var1, ACKC6 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f12(SecsMessage var1, ACKC6 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f14(SecsMessage var1, ACKC6 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f15(DynamicCollectionEvent var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f17(DynamicCollectionEvent var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f19(DynamicReport var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f21(DynamicReport var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s6f26(SecsMessage var1, ACKC6 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f4(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f12(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f14(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f16(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f18(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f24(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f32(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f38(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f40(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f42(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s7f44(SecsMessage var1, ACKC7 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s9f1(SecsMessage var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s9f3(SecsMessage var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s9f5(SecsMessage var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s9f7(SecsMessage var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s9f9(SecsMessage var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s9f11(SecsMessage var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s10f2(SecsMessage var1, ACKC10 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s10f4(SecsMessage var1, ACKC10 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s10f6(SecsMessage var1, ACKC10 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s10f10(SecsMessage var1, ACKC10 var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s13f12(SecsMessage var1, GRANT var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<SecsMessage> s2f50(SecsMessage var1, CMDA var2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;
}

