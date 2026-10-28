/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1ontcpip;

import com.kuka.ecs.common.transport.secs.secs1.Secs1Communicator;
import com.kuka.ecs.common.transport.secs.secs1ontcpip.AbstractSecs1OnTcpIpReceiverCommunicator;
import com.kuka.ecs.common.transport.secs.secs1ontcpip.Secs1OnTcpIpReceiverCommunicatorConfig;
import java.io.IOException;

public interface Secs1OnTcpIpReceiverCommunicator
extends Secs1Communicator {
    public static Secs1OnTcpIpReceiverCommunicator newInstance(Secs1OnTcpIpReceiverCommunicatorConfig config) {
        return new AbstractSecs1OnTcpIpReceiverCommunicator(config){};
    }

    public static Secs1OnTcpIpReceiverCommunicator open(Secs1OnTcpIpReceiverCommunicatorConfig config) throws IOException {
        Secs1OnTcpIpReceiverCommunicator inst = Secs1OnTcpIpReceiverCommunicator.newInstance(config);
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

