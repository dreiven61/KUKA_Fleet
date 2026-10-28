/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.AbstractSecsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.Property;
import com.kuka.ecs.common.transport.secs.ReadOnlyProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.TimeProperty;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import java.util.Objects;

public abstract class AbstractHsmsCommunicatorConfig
extends AbstractSecsCommunicatorConfig {
    private static final long serialVersionUID = 3767856791428188426L;
    private final Property<HsmsConnectionMode> connectionMode = Property.newInstance(HsmsConnectionMode.PASSIVE);
    private final TimeProperty linktest = TimeProperty.newInstance(-1.0f);
    private final TimeProperty rebindIfPassive = TimeProperty.newInstance(10.0f);

    public void connectionMode(HsmsConnectionMode mode) {
        this.connectionMode.set(Objects.requireNonNull(mode));
    }

    public ReadOnlyProperty<HsmsConnectionMode> connectionMode() {
        return this.connectionMode;
    }

    public void notLinktest() {
        this.linktest.set(-1.0f);
    }

    public void linktest(float v) {
        if (v < 0.0f) {
            throw new LinktestIllegalArgumentException(v);
        }
        this.linktest.set(v);
    }

    public ReadOnlyTimeProperty linktest() {
        return this.linktest;
    }

    public void notRebindIfPassive() {
        this.rebindIfPassive.set(-1.0f);
    }

    public void rebindIfPassive(float v) {
        if (v < 0.0f) {
            throw new RebindIfPassiveIllegalArgumentException(v);
        }
        this.rebindIfPassive.set(v);
    }

    public ReadOnlyTimeProperty rebindIfPassive() {
        return this.rebindIfPassive;
    }

    private static class RebindIfPassiveIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = -7951027764805326813L;

        public RebindIfPassiveIllegalArgumentException(float value) {
            super("rebindIfPassive value is >= 0.0F, value=" + value);
        }
    }

    private static class LinktestIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = 2792082412684754490L;

        public LinktestIllegalArgumentException(float value) {
            super("linktest value is >= 0.0F, value=" + value);
        }
    }
}

