/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.TimeProperty;
import java.io.Serializable;

public class SecsTimeout
implements Serializable {
    private static final long serialVersionUID = -3240180901934372535L;
    private final TimeProperty t1 = TimeProperty.newInstance(1.0f);
    private final TimeProperty t2 = TimeProperty.newInstance(15.0f);
    private final TimeProperty t3 = TimeProperty.newInstance(45.0f);
    private final TimeProperty t4 = TimeProperty.newInstance(45.0f);
    private final TimeProperty t5 = TimeProperty.newInstance(10.0f);
    private final TimeProperty t6 = TimeProperty.newInstance(5.0f);
    private final TimeProperty t7 = TimeProperty.newInstance(10.0f);
    private final TimeProperty t8 = TimeProperty.newInstance(6.0f);

    protected SecsTimeout() {
    }

    public void t1(float v) {
        if (v <= 0.0f) {
            throw new T1TimeoutIllegalArgumentException(v);
        }
        this.t1.set(v);
    }

    public ReadOnlyTimeProperty t1() {
        return this.t1;
    }

    public void t2(float v) {
        if (v <= 0.0f) {
            throw new T2TimeoutIllegalArgumentException(v);
        }
        this.t2.set(v);
    }

    public ReadOnlyTimeProperty t2() {
        return this.t2;
    }

    public void t3(float v) {
        if (v <= 0.0f) {
            throw new T3TimeoutIllegalArgumentException(v);
        }
        this.t3.set(v);
    }

    public ReadOnlyTimeProperty t3() {
        return this.t3;
    }

    public void t4(float v) {
        if (v <= 0.0f) {
            throw new T4TimeoutIllegalArgumentException(v);
        }
        this.t4.set(v);
    }

    public ReadOnlyTimeProperty t4() {
        return this.t4;
    }

    public void t5(float v) {
        if (v <= 0.0f) {
            throw new T5TimeoutIllegalArgumentException(v);
        }
        this.t5.set(v);
    }

    public ReadOnlyTimeProperty t5() {
        return this.t5;
    }

    public void t6(float v) {
        if (v <= 0.0f) {
            throw new T6TimeoutIllegalArgumentException(v);
        }
        this.t6.set(v);
    }

    public ReadOnlyTimeProperty t6() {
        return this.t6;
    }

    public void t7(float v) {
        if (v <= 0.0f) {
            throw new T7TimeoutIllegalArgumentException(v);
        }
        this.t7.set(v);
    }

    public ReadOnlyTimeProperty t7() {
        return this.t7;
    }

    public void t8(float v) {
        if (v <= 0.0f) {
            throw new T8TimeoutIllegalArgumentException(v);
        }
        this.t8.set(v);
    }

    public ReadOnlyTimeProperty t8() {
        return this.t8;
    }

    private static class T8TimeoutIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = 633542395990124660L;

        public T8TimeoutIllegalArgumentException(float value) {
            super("T8-timeout is >0, value=" + value);
        }
    }

    private static class T7TimeoutIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = -350165616843221603L;

        public T7TimeoutIllegalArgumentException(float value) {
            super("T7-timeout is >0, value=" + value);
        }
    }

    private static class T6TimeoutIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = 7122891591407469696L;

        public T6TimeoutIllegalArgumentException(float value) {
            super("T6-timeout is >0, value=" + value);
        }
    }

    private static class T5TimeoutIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = -2021716924907606047L;

        public T5TimeoutIllegalArgumentException(float value) {
            super("T5-timeout is >0, value=" + value);
        }
    }

    private static class T4TimeoutIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = -203078702388901633L;

        public T4TimeoutIllegalArgumentException(float value) {
            super("T4-timeout is >0, value=" + value);
        }
    }

    private static class T3TimeoutIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = 3839307398543951387L;

        public T3TimeoutIllegalArgumentException(float value) {
            super("T3-timeout is >0, value=" + value);
        }
    }

    private static class T2TimeoutIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = 3486222709254763543L;

        public T2TimeoutIllegalArgumentException(float value) {
            super("T2-timeout is >0, value=" + value);
        }
    }

    private static class T1TimeoutIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = -2581208452653867273L;

        public T1TimeoutIllegalArgumentException(float value) {
            super("T1-timeout is >0, value=" + value);
        }
    }
}

