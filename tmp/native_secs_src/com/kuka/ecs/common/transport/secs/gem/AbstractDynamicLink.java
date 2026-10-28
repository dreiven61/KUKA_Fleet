/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.gem.DynamicCollectionEvent;
import com.kuka.ecs.common.transport.secs.gem.DynamicLink;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractDynamicLink
implements DynamicLink,
Serializable {
    private static final long serialVersionUID = 2780263993430315803L;
    private final DynamicCollectionEvent collectionEvent;
    private final List<Secs2> reportIds;

    public AbstractDynamicLink(DynamicCollectionEvent collectionEvent, List<? extends Secs2> reportIds) {
        this.collectionEvent = collectionEvent;
        this.reportIds = new ArrayList<Secs2>(reportIds);
    }

    @Override
    public Secs2 toS2F35Link() {
        return Secs2.list(this.collectionEvent.collectionEventId(), Secs2.list(this.reportIds));
    }

    @Override
    public DynamicCollectionEvent collectionEvent() {
        return this.collectionEvent;
    }

    @Override
    public Secs2 collectionEventId() {
        return this.collectionEvent.collectionEventId();
    }

    @Override
    public List<Secs2> reportIds() {
        return Collections.unmodifiableList(this.reportIds);
    }

    public int hashCode() {
        return this.collectionEvent.hashCode();
    }

    public boolean equals(Object o) {
        if (o != null && o instanceof AbstractDynamicLink) {
            return ((AbstractDynamicLink)o).collectionEvent.equals(this.collectionEvent);
        }
        return false;
    }
}

