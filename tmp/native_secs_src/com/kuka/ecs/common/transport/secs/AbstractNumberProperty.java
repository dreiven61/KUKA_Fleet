/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractProperty;
import com.kuka.ecs.common.transport.secs.NumberProperty;
import java.util.Objects;

public abstract class AbstractNumberProperty
extends AbstractProperty<Number>
implements NumberProperty {
    private static final long serialVersionUID = 7735781891914639639L;

    public AbstractNumberProperty(Number initial) {
        super(Objects.requireNonNull(initial));
    }

    public AbstractNumberProperty(int initial) {
        super(initial);
    }

    public AbstractNumberProperty(long initial) {
        super(initial);
    }

    public AbstractNumberProperty(float initial) {
        super(Float.valueOf(initial));
    }

    public AbstractNumberProperty(double initial) {
        super(initial);
    }

    @Override
    public void set(Number v) {
        super.set(Objects.requireNonNull(v));
    }

    @Override
    public void set(int v) {
        this.set((Number)v);
    }

    @Override
    public void set(long v) {
        this.set((Number)v);
    }

    @Override
    public void set(float v) {
        this.set(Float.valueOf(v));
    }

    @Override
    public void set(double v) {
        this.set((Number)v);
    }

    @Override
    public int intValue() {
        return ((Number)this.get()).intValue();
    }

    @Override
    public long longValue() {
        return ((Number)this.get()).longValue();
    }

    @Override
    public float floatValue() {
        return ((Number)this.get()).floatValue();
    }

    @Override
    public double doubleValue() {
        return ((Number)this.get()).doubleValue();
    }
}

