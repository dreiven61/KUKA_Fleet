/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.SecsSendMessageException;
import com.kuka.ecs.common.transport.secs.SecsWaitReplyMessageException;
import com.kuka.ecs.common.transport.secs.gem.AbstractGem;
import com.kuka.ecs.common.transport.secs.gem.AliasNotFoundDynamicEventReportException;
import com.kuka.ecs.common.transport.secs.gem.CEED;
import com.kuka.ecs.common.transport.secs.gem.DRACK;
import com.kuka.ecs.common.transport.secs.gem.DynamicCollectionEvent;
import com.kuka.ecs.common.transport.secs.gem.DynamicEventReportConfig;
import com.kuka.ecs.common.transport.secs.gem.DynamicEventReportException;
import com.kuka.ecs.common.transport.secs.gem.DynamicLink;
import com.kuka.ecs.common.transport.secs.gem.DynamicReport;
import com.kuka.ecs.common.transport.secs.gem.ERACK;
import com.kuka.ecs.common.transport.secs.gem.LRACK;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public abstract class AbstractDynamicEventReportConfig
implements DynamicEventReportConfig,
Serializable {
    private static final long serialVersionUID = 5357410952900950632L;
    private final AbstractGem gem;
    private final Set<DynamicReport> reports = new CopyOnWriteArraySet<DynamicReport>();
    private final Set<DynamicLink> links = new CopyOnWriteArraySet<DynamicLink>();
    private final Set<DynamicCollectionEvent> events = new CopyOnWriteArraySet<DynamicCollectionEvent>();

    public AbstractDynamicEventReportConfig(AbstractGem gem) {
        this.gem = gem;
    }

    protected DynamicReport createReport(Secs2 reportId, CharSequence alias, List<? extends Number> vids) {
        List vv = vids.stream().map(Number::longValue).map(this.gem::vId).collect(Collectors.toList());
        return DynamicReport.newInstance(reportId, alias, vv);
    }

    protected DynamicCollectionEvent createCollectionEvent(CharSequence alias, long ceid) {
        return DynamicCollectionEvent.newInstance(alias, this.gem.collectionEventId(ceid));
    }

    protected DynamicLink createLink(DynamicCollectionEvent ce, List<? extends Secs2> reports) {
        return DynamicLink.newInstance(ce, reports);
    }

    @Override
    public DynamicReport addDefineReport(long reportId, CharSequence alias, List<? extends Number> vids) {
        return this.addDefineReport(this.gem.reportId(reportId), alias, vids);
    }

    @Override
    public DynamicReport addDefineReport(long reportId, List<? extends Number> vids) {
        return this.addDefineReport(this.gem.reportId(reportId), null, vids);
    }

    @Override
    public DynamicReport addDefineReport(CharSequence alias, List<? extends Number> vids) {
        return this.addDefineReport(this.gem.autoReportId(), alias, vids);
    }

    @Override
    public DynamicReport addDefineReport(List<? extends Number> vids) {
        return this.addDefineReport(this.gem.autoReportId(), null, vids);
    }

    private DynamicReport addDefineReport(Secs2 reportId, CharSequence alias, List<? extends Number> vids) {
        DynamicReport r = this.createReport(reportId, alias, vids);
        this.reports.add(r);
        return r;
    }

    @Override
    public boolean removeReport(DynamicReport report) {
        return this.reports.remove(report);
    }

    @Override
    public Optional<DynamicReport> getReport(CharSequence alias) {
        if (alias == null) {
            return Optional.empty();
        }
        String s = alias.toString();
        return this.reports.stream().filter(r -> r.alias().isPresent()).filter(r -> Objects.equals(r.alias().get(), s)).findFirst();
    }

    @Override
    public Optional<DynamicReport> getReport(Secs2 reportId) {
        if (reportId == null) {
            return Optional.empty();
        }
        return this.reports.stream().filter(r -> Objects.equals(r.reportId(), reportId)).findFirst();
    }

    @Override
    public DynamicLink addLinkById(long ceid, List<? extends Number> reportIds) {
        return this.addLinkById(this.createCollectionEvent(null, ceid), reportIds);
    }

    @Override
    public DynamicLink addLinkById(DynamicCollectionEvent ce, List<? extends Number> reportIds) {
        List ss = reportIds.stream().map(Number::longValue).map(this.gem::reportId).collect(Collectors.toList());
        return this.addLinkBySecs2(ce, ss);
    }

    @Override
    public DynamicLink addLinkByReport(long ceid, List<? extends DynamicReport> reports) {
        return this.addLinkByReport(this.createCollectionEvent(null, ceid), reports);
    }

    @Override
    public DynamicLink addLinkByReport(DynamicCollectionEvent ce, List<? extends DynamicReport> reports) {
        List ss = reports.stream().map(r -> r.reportId()).collect(Collectors.toList());
        return this.addLinkBySecs2(ce, ss);
    }

    private DynamicLink addLinkBySecs2(DynamicCollectionEvent ce, List<? extends Secs2> reports) {
        DynamicLink link = this.createLink(ce, reports);
        this.links.add(link);
        return link;
    }

    @Override
    public boolean removeLink(DynamicLink link) {
        return this.links.remove(link);
    }

    @Override
    public DynamicCollectionEvent addEnableCollectionEvent(long ceid) {
        return this.addEnableCollectionEvent(null, ceid);
    }

    @Override
    public DynamicCollectionEvent addEnableCollectionEvent(CharSequence alias, long ceid) {
        DynamicCollectionEvent ce = this.createCollectionEvent(alias, ceid);
        this.events.add(ce);
        return ce;
    }

    @Override
    public boolean removeEnableCollectionEvent(DynamicCollectionEvent ce) {
        return this.events.remove(ce);
    }

    @Override
    public Optional<DynamicCollectionEvent> getCollectionEvent(CharSequence alias) {
        if (alias == null) {
            return Optional.empty();
        }
        String s = alias.toString();
        return this.events.stream().filter(r -> r.alias().isPresent()).filter(ce -> Objects.equals(ce.alias().get(), s)).findFirst();
    }

    @Override
    public Optional<DynamicCollectionEvent> getCollectionEvent(Secs2 ceid) {
        if (ceid == null) {
            return Optional.empty();
        }
        return this.events.stream().filter(ev -> Objects.equals(ev.collectionEventId(), ceid)).findFirst();
    }

    @Override
    public DRACK s2f33DeleteAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return this.gem.s2f33DeleteAll();
    }

    @Override
    public DRACK s2f33Define() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        List rr = this.reports.stream().map(r -> r.toS2F33Report()).collect(Collectors.toList());
        return this.gem.s2f33Inner(rr);
    }

    @Override
    public LRACK s2f35() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        List ll = this.links.stream().map(l -> l.toS2F35Link()).collect(Collectors.toList());
        return this.gem.s2f35Inner(ll);
    }

    @Override
    public ERACK s2f37DisableAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return this.gem.s2f37DisableAll();
    }

    @Override
    public ERACK s2f37EnableAll() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        return this.gem.s2f37EnableAll();
    }

    @Override
    public ERACK s2f37Enable() throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, Secs2Exception, InterruptedException {
        List ee = this.events.stream().map(e -> e.collectionEventId()).collect(Collectors.toList());
        return this.gem.s2f37Inner(CEED.ENABLE, ee);
    }

    public Optional<SecsMessage> s6f15(DynamicCollectionEvent ce) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.gem.s6f15(ce);
    }

    public Optional<SecsMessage> s6f15(CharSequence alias) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, DynamicEventReportException, InterruptedException {
        DynamicCollectionEvent ce = this.getCollectionEvent(alias).orElseThrow(() -> new AliasNotFoundDynamicEventReportException("\"" + Objects.toString(alias) + "\" not found"));
        return this.s6f15(ce);
    }

    public Optional<SecsMessage> s6f17(DynamicCollectionEvent ce) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.gem.s6f17(ce);
    }

    public Optional<SecsMessage> s6f17(CharSequence alias) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, DynamicEventReportException, InterruptedException {
        DynamicCollectionEvent ce = this.getCollectionEvent(alias).orElseThrow(() -> new AliasNotFoundDynamicEventReportException("\"" + Objects.toString(alias) + "\" not found"));
        return this.s6f17(ce);
    }

    public Optional<SecsMessage> s6f19(DynamicReport report) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.gem.s6f19(report);
    }

    public Optional<SecsMessage> s6f19(CharSequence alias) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, DynamicEventReportException, InterruptedException {
        DynamicReport report = this.getReport(alias).orElseThrow(() -> new AliasNotFoundDynamicEventReportException("\"" + Objects.toString(alias) + "\" not found"));
        return this.s6f19(report);
    }

    public Optional<SecsMessage> s6f21(DynamicReport report) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
        return this.gem.s6f21(report);
    }

    public Optional<SecsMessage> s6f21(CharSequence alias) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, DynamicEventReportException, InterruptedException {
        DynamicReport report = this.getReport(alias).orElseThrow(() -> new AliasNotFoundDynamicEventReportException("\"" + Objects.toString(alias) + "\" not found"));
        return this.s6f21(report);
    }
}

