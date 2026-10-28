/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractProperty;
import com.kuka.ecs.common.transport.secs.StringProperty;

public abstract class AbstractStringProperty
extends AbstractProperty<String>
implements StringProperty {
    private static final long serialVersionUID = 4304110559260592300L;

    public AbstractStringProperty(CharSequence initial) {
        super(initial == null ? null : initial.toString());
    }

    @Override
    public void set(CharSequence cs) {
        super.set(cs == null ? null : cs.toString());
    }
}

