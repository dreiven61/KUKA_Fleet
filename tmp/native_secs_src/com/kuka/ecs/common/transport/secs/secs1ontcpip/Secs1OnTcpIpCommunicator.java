/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1ontcpip;

import com.kuka.ecs.common.transport.secs.secs1.Secs1Communicator;
import com.kuka.ecs.common.transport.secs.secs1ontcpip.AbstractSecs1OnTcpIpCommunicator;
import com.kuka.ecs.common.transport.secs.secs1ontcpip.Secs1OnTcpIpCommunicatorConfig;
import java.io.IOException;

public interface Secs1OnTcpIpCommunicator
extends Secs1Communicator {
    public static Secs1OnTcpIpCommunicator newInstance(Secs1OnTcpIpCommunicatorConfig config) {
        return new AbstractSecs1OnTcpIpCommunicator(config){};
    }

    public static Secs1OnTcpIpCommunicator open(Secs1OnTcpIpCommunicatorConfig config) throws IOException {
        Secs1OnTcpIpCommunicator inst = Secs1OnTcpIpCommunicator.newInstance(config);
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

