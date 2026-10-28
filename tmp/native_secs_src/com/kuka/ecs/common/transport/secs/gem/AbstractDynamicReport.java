/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.gem.DynamicReport;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class AbstractDynamicReport
implements DynamicReport,
Serializable {
    private static final long serialVersionUID = -1459017309329426409L;
    private final Secs2 reportId;
    private final String alias;
    private final List<Secs2> vids;

    public AbstractDynamicReport(Secs2 reportId, CharSequence alias, List<? extends Secs2> vids) {
        this.reportId = reportId;
        this.alias = alias == null ? null : alias.toString();
        this.vids = new ArrayList<Secs2>(vids);
    }

    @Override
    public Secs2 toS2F33Report() {
        return Secs2.list(this.reportId, Secs2.list(this.vids));
    }

    @Override
    public Secs2 reportId() {
        return this.reportId;
    }

    @Override
    public Optional<String> alias() {
        return this.alias == null ? Optional.empty() : Optional.of(this.alias);
    }

    @Override
    public List<Secs2> vids() {
        return Collections.unmodifiableList(this.vids);
    }

    public int hashCode() {
        return this.reportId.hashCode();
    }

    public boolean equals(Object o) {
        if (o != null && o instanceof AbstractDynamicReport) {
            return ((AbstractDynamicReport)o).reportId.equals(this.reportId);
        }
        return false;
    }
}

