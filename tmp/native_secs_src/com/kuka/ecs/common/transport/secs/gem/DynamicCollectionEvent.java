/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.gem.AbstractDynamicCollectionEvent;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import java.util.Optional;

public interface DynamicCollectionEvent {
    public static DynamicCollectionEvent newInstance(CharSequence alias, Secs2 collectionEventId) {
        return new AbstractDynamicCollectionEvent(alias, collectionEventId){
            private static final long serialVersionUID = 3280261182801909513L;
        };
    }

    public Optional<String> alias();

    public Secs2 toS2F37CollectionEvent();

    public Secs2 collectionEventId();

    public static DynamicCollectionEvent fromS2F37CollectionEvent(Secs2 secs2) {
        return DynamicCollectionEvent.newInstance(null, secs2);
    }
}

