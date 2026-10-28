/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.gem.DynamicCollectionEvent;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.io.Serializable;
import java.util.Optional;

public abstract class AbstractDynamicCollectionEvent
implements DynamicCollectionEvent,
Serializable {
    private static final long serialVersionUID = -1354409024900428639L;
    private final String alias;
    private final Secs2 collectionEventId;

    public AbstractDynamicCollectionEvent(CharSequence alias, Secs2 collectionEventId) {
        this.alias = alias == null ? null : alias.toString();
        this.collectionEventId = collectionEventId;
    }

    @Override
    public Optional<String> alias() {
        return this.alias == null ? Optional.empty() : Optional.of(this.alias);
    }

    @Override
    public Secs2 toS2F37CollectionEvent() {
        return this.collectionEventId;
    }

    @Override
    public Secs2 collectionEventId() {
        return this.collectionEventId;
    }

    public int hashCode() {
        return this.collectionEventId.hashCode();
    }

    public boolean equals(Object o) {
        if (o != null && o instanceof AbstractDynamicCollectionEvent) {
            return ((AbstractDynamicCollectionEvent)o).collectionEventId.equals(this.collectionEventId);
        }
        return false;
    }
}

