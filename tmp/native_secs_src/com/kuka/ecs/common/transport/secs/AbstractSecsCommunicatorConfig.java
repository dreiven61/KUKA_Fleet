/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.BooleanProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyBooleanProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyProperty;
import com.kuka.ecs.common.transport.secs.SecsTimeout;
import com.kuka.ecs.common.transport.secs.StringProperty;
import com.kuka.ecs.common.transport.secs.gem.AbstractGemConfig;
import java.io.Serializable;
import java.util.Objects;

public abstract class AbstractSecsCommunicatorConfig
implements Serializable {
    private static final long serialVersionUID = -8456991094606676409L;
    private final SecsTimeout timeout = new SecsTimeout();
    private final BooleanProperty isEquip = BooleanProperty.newInstance(false);
    private final AbstractGemConfig gem = new AbstractGemConfig(){
        private static final long serialVersionUID = -3386783271396322749L;
    };
    private final StringProperty name = StringProperty.newInstance("");
    private final StringProperty logSubjectHeader = StringProperty.newInstance("");

    public void isEquip(boolean f) {
        this.isEquip.set(f);
    }

    public ReadOnlyBooleanProperty isEquip() {
        return this.isEquip;
    }

    public SecsTimeout timeout() {
        return this.timeout;
    }

    public AbstractGemConfig gem() {
        return this.gem;
    }

    public void name(CharSequence name) {
        this.name.set(Objects.requireNonNull(name).toString());
    }

    public ReadOnlyProperty<String> name() {
        return this.name;
    }

    public void logSubjectHeader(CharSequence header) {
        this.logSubjectHeader.set(Objects.requireNonNull(header).toString());
    }

    public ReadOnlyProperty<String> logSubjectHeader() {
        return this.logSubjectHeader;
    }
}

