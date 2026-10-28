/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.gem.AbstractDynamicReport;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface DynamicReport {
    public static DynamicReport newInstance(Secs2 reportId, CharSequence alias, List<? extends Secs2> vids) {
        return new AbstractDynamicReport(reportId, alias, (List)vids){
            private static final long serialVersionUID = 1L;
        };
    }

    public Secs2 toS2F33Report();

    public Secs2 reportId();

    public Optional<String> alias();

    public List<Secs2> vids();

    public static DynamicReport fromS2F33Report(Secs2 secs2) throws Secs2Exception {
        Secs2 rptid = secs2.get(0);
        List vids = secs2.get(1).stream().collect(Collectors.toList());
        return DynamicReport.newInstance(rptid, null, vids);
    }
}

