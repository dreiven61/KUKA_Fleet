/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsmsss;

import com.kuka.ecs.common.transport.secs.hsms.HsmsCommunicator;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import com.kuka.ecs.common.transport.secs.hsmsss.AbstractHsmsSsActiveCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsss.AbstractHsmsSsPassiveCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicatorConfig;
import java.io.IOException;

public interface HsmsSsCommunicator
extends HsmsCommunicator {
    public static HsmsSsCommunicator newInstance(HsmsSsCommunicatorConfig config) {
        HsmsConnectionMode mode = config.connectionMode().get();
        switch (mode) {
            case PASSIVE: {
                return new AbstractHsmsSsPassiveCommunicator(config){};
            }
            case ACTIVE: {
                return new AbstractHsmsSsActiveCommunicator(config){};
            }
        }
        throw new IllegalStateException("undefined connecton-mode: " + (Object)((Object)mode));
    }

    public static HsmsSsCommunicator open(HsmsSsCommunicatorConfig config) throws IOException {
        HsmsSsCommunicator inst = HsmsSsCommunicator.newInstance(config);
        try {
            inst.open();
        }
        catch (IOException e) {
            try {
                inst.close();
            }
            catch (IOException iOException) {
                // empty catch block
            }
            throw e;
        }
        return inst;
    }
}

