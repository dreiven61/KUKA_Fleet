/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.gem.AbstractDynamicLink;
import com.kuka.ecs.common.transport.secs.gem.DynamicCollectionEvent;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import java.util.List;
import java.util.stream.Collectors;

public interface DynamicLink {
    public static DynamicLink newInstance(DynamicCollectionEvent collectionEvent, List<? extends Secs2> reportIds) {
        return new AbstractDynamicLink(collectionEvent, (List)reportIds){
            private static final long serialVersionUID = -6582421299432050134L;
        };
    }

    public Secs2 toS2F35Link();

    public DynamicCollectionEvent collectionEvent();

    public Secs2 collectionEventId();

    public List<Secs2> reportIds();

    public static DynamicLink fromS2F35Link(Secs2 secs2) throws Secs2Exception {
        DynamicCollectionEvent ce = DynamicCollectionEvent.newInstance(null, secs2.get(0));
        List rptids = secs2.get(1).stream().collect(Collectors.toList());
        return DynamicLink.newInstance(ce, rptids);
    }
}

