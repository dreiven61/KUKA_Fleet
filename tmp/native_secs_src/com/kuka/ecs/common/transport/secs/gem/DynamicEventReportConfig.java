/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.gem.AbstractDynamicEventReportConfig;
import com.kuka.ecs.common.transport.secs.gem.AbstractGem;
import com.kuka.ecs.common.transport.secs.gem.DRACK;
import com.kuka.ecs.common.transport.secs.gem.DynamicCollectionEvent;
import com.kuka.ecs.common.transport.secs.gem.DynamicEventReportException;
import com.kuka.ecs.common.transport.secs.gem.DynamicLink;
import com.kuka.ecs.common.transport.secs.gem.DynamicReport;
import com.kuka.ecs.common.transport.secs.gem.ERACK;
import com.kuka.ecs.common.transport.secs.gem.LRACK;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import java.util.List;
import java.util.Optional;

public interface DynamicEventReportConfig {
    public static DynamicEventReportConfig newInstance(AbstractGem gem) {
        return new AbstractDynamicEventReportConfig(gem){
            private static final long serialVersionUID = -5375563226536904308L;
        };
    }

    public DynamicReport addDefineReport(long var1, CharSequence var3, List<? extends Number> var4);

    public DynamicReport addDefineReport(long var1, List<? extends Number> var3);

    public DynamicReport addDefineReport(CharSequence var1, List<? extends Number> var2);

    public DynamicReport addDefineReport(List<? extends Number> var1);

    public boolean removeReport(DynamicReport var1);

    public Optional<DynamicReport> getReport(CharSequence var1);

    public Optional<DynamicReport> getReport(Secs2 var1);

    public DynamicLink addLinkById(long var1, List<? extends Number> var3);

    public DynamicLink addLinkById(DynamicCollectionEvent var1, List<? extends Number> var2);

    public DynamicLink addLinkByReport(long var1, List<? extends DynamicReport> var3);

    public DynamicLink addLinkByReport(DynamicCollectionEvent var1, List<? extends DynamicReport> var2);

    public boolean removeLink(DynamicLink var1);

    public DynamicCollectionEvent addEnableCollectionEvent(long var1);

    public DynamicCollectionEvent addEnableCollectionEvent(CharSequence var1, long var2);

    public boolean removeEnableCollectionEvent(DynamicCollectionEvent var1);

    public Optional<DynamicCollectionEvent> getCollectionEvent(CharSequence var1);

    public Optional<DynamicCollectionEvent> getCollectionEvent(Secs2 var1);

    public DRACK s2f33DeleteAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public DRACK s2f33Define() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public LRACK s2f35() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public ERACK s2f37DisableAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public ERACK s2f37EnableAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public ERACK s2f37Enable() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException;

    public Optional<? extends SecsMessage> s6f15(DynamicCollectionEvent var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<? extends SecsMessage> s6f15(CharSequence var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, DynamicEventReportException, InterruptedException;

    public Optional<? extends SecsMessage> s6f17(DynamicCollectionEvent var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<? extends SecsMessage> s6f17(CharSequence var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, DynamicEventReportException, InterruptedException;

    public Optional<? extends SecsMessage> s6f19(DynamicReport var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<? extends SecsMessage> s6f19(CharSequence var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, DynamicEventReportException, InterruptedException;

    public Optional<? extends SecsMessage> s6f21(DynamicReport var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException;

    public Optional<? extends SecsMessage> s6f21(CharSequence var1) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, DynamicEventReportException, InterruptedException;
}

