/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.AbstractProperty;
import com.kuka.ecs.common.transport.secs.Property;
import com.kuka.ecs.common.transport.secs.ReadOnlyProperty;
import com.kuka.ecs.common.transport.secs.StringProperty;
import com.kuka.ecs.common.transport.secs.gem.ClockType;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import java.io.Serializable;
import java.util.Objects;

public abstract class AbstractGemConfig
implements Serializable {
    private static final long serialVersionUID = 1130854092113358850L;
    private final StringProperty mdln = StringProperty.newInstance("      ");
    private final StringProperty softrev = StringProperty.newInstance("      ");
    private final Property<ClockType> clockType = Property.newInstance(ClockType.A16);
    private final Secs2NumberItemProperty dataIdSecs2Item = new Secs2NumberItemProperty(Secs2Item.UINT4);
    private final Secs2NumberItemProperty vIdSecs2Item = new Secs2NumberItemProperty(Secs2Item.UINT4);
    private final Secs2NumberItemProperty reportIdSecs2Item = new Secs2NumberItemProperty(Secs2Item.UINT4);
    private final Secs2NumberItemProperty collectionEventIdSecs2Item = new Secs2NumberItemProperty(Secs2Item.UINT4);

    public void mdln(CharSequence cs) {
        this.mdln.set(Objects.requireNonNull(cs));
    }

    public ReadOnlyProperty<String> mdln() {
        return this.mdln;
    }

    public void softrev(CharSequence cs) {
        this.softrev.set(Objects.requireNonNull(cs));
    }

    public ReadOnlyProperty<String> softrev() {
        return this.softrev;
    }

    public void clockType(ClockType type) {
        this.clockType.set(Objects.requireNonNull(type));
    }

    public ReadOnlyProperty<ClockType> clockType() {
        return this.clockType;
    }

    public void dataIdSecs2Item(Secs2Item item) {
        this.dataIdSecs2Item.set(item);
    }

    public ReadOnlyProperty<Secs2Item> dataIdSecs2Item() {
        return this.dataIdSecs2Item;
    }

    public void vIdSecs2Item(Secs2Item item) {
        this.vIdSecs2Item.set(item);
    }

    public ReadOnlyProperty<Secs2Item> vIdSecs2Item() {
        return this.vIdSecs2Item;
    }

    public void reportIdSecs2Item(Secs2Item item) {
        this.reportIdSecs2Item.set(item);
    }

    public ReadOnlyProperty<Secs2Item> reportIdSecs2Item() {
        return this.reportIdSecs2Item;
    }

    public void collectionEventIdSecs2Item(Secs2Item item) {
        this.collectionEventIdSecs2Item.set(item);
    }

    public ReadOnlyProperty<Secs2Item> collectionEventIdSecs2Item() {
        return this.collectionEventIdSecs2Item;
    }

    protected class Secs2NumberItemProperty
    extends AbstractProperty<Secs2Item> {
        private static final long serialVersionUID = 3803548762155640142L;

        public Secs2NumberItemProperty(Secs2Item initial) {
            super(Objects.requireNonNull(initial));
        }

        @Override
        public void set(Secs2Item v) {
            Objects.requireNonNull(v);
            switch (v) {
                case INT1: 
                case INT2: 
                case INT4: 
                case INT8: 
                case UINT1: 
                case UINT2: 
                case UINT4: 
                case UINT8: {
                    super.set(v);
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Not support " + v.toString());
                }
            }
        }
    }
}

