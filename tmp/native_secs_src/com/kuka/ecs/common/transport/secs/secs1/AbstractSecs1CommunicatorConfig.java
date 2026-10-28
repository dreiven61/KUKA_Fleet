/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.AbstractSecsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.BooleanProperty;
import com.kuka.ecs.common.transport.secs.NumberProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyBooleanProperty;
import com.kuka.ecs.common.transport.secs.ReadOnlyNumberProperty;

public abstract class AbstractSecs1CommunicatorConfig
extends AbstractSecsCommunicatorConfig {
    private static final long serialVersionUID = 4897063498275749609L;
    private final NumberProperty deviceId = NumberProperty.newInstance(10);
    private final BooleanProperty isMaster = BooleanProperty.newInstance(true);
    private final NumberProperty retry = NumberProperty.newInstance(3);
    private final BooleanProperty isCheckMessageBlockDeviceId = BooleanProperty.newInstance(true);

    public void deviceId(int id) {
        if (id < 0 || id > Short.MAX_VALUE) {
            throw new DeviceIdIllegalArgumentException(id);
        }
        this.deviceId.set(id);
    }

    public ReadOnlyNumberProperty deviceId() {
        return this.deviceId;
    }

    public void isMaster(boolean isMaster) {
        this.isMaster.set(isMaster);
    }

    public ReadOnlyBooleanProperty isMaster() {
        return this.isMaster;
    }

    public void retry(int retryCount) {
        if (retryCount < 0) {
            throw new RetryCountIllegalArgumentException(retryCount);
        }
        this.retry.set(retryCount);
    }

    public ReadOnlyNumberProperty retry() {
        return this.retry;
    }

    public void isCheckMessageBlockDeviceId(boolean doCheck) {
        this.isCheckMessageBlockDeviceId.set(doCheck);
    }

    public ReadOnlyBooleanProperty isCheckMessageBlockDeviceId() {
        return this.isCheckMessageBlockDeviceId;
    }

    private static final class RetryCountIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = 2911974034453457076L;

        public RetryCountIllegalArgumentException(int retryCount) {
            super("retry is >= 0, count=" + retryCount);
        }
    }

    private static final class DeviceIdIllegalArgumentException
    extends IllegalArgumentException {
        private static final long serialVersionUID = 6679136614355645926L;

        public DeviceIdIllegalArgumentException(int id) {
            super("Device-ID is in 0 - 32767, id=" + id);
        }
    }
}

