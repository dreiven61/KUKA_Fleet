/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.context.ApplicationEvent
 *  org.springframework.context.ApplicationEventPublisher
 *  org.thingsboard.server.cluster.TbClusterService
 *  org.thingsboard.server.common.data.Device
 *  org.thingsboard.server.common.data.DeviceTransportType
 *  org.thingsboard.server.common.data.device.DeviceExceptionWarningTypeEnum
 *  org.thingsboard.server.common.data.device.connector.secs.SecsDeviceConnector
 *  org.thingsboard.server.common.data.id.EntityId
 *  org.thingsboard.server.common.data.id.TenantId
 *  org.thingsboard.server.common.data.state.ConnectionStateChangedEvent
 *  org.thingsboard.server.common.msg.TbMsg
 *  org.thingsboard.server.common.msg.TbMsgDataType
 *  org.thingsboard.server.common.msg.TbMsgMetaData
 *  org.thingsboard.server.common.msg.session.SessionMsgType
 *  org.thingsboard.server.dao.device.DeviceExceptionWarningService
 */
package com.kuka.ecs.common.transport.secs.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuka.ecs.common.transport.secs.SecsCommunicator;
import com.kuka.ecs.common.transport.secs.SecsException;
import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.gem.CMDA;
import com.kuka.ecs.common.transport.secs.gem.COMMACK;
import com.kuka.ecs.common.transport.secs.gem.Clock;
import com.kuka.ecs.common.transport.secs.gem.ClockType;
import com.kuka.ecs.common.transport.secs.gem.DRACK;
import com.kuka.ecs.common.transport.secs.gem.ERACK;
import com.kuka.ecs.common.transport.secs.gem.LRACK;
import com.kuka.ecs.common.transport.secs.gem.ONLACK;
import com.kuka.ecs.common.transport.secs.gem.TIACK;
import com.kuka.ecs.common.transport.secs.hsms.HsmsConnectionMode;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicator;
import com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicatorConfig;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.device.DeviceExceptionWarningTypeEnum;
import org.thingsboard.server.common.data.device.connector.secs.SecsDeviceConnector;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.state.ConnectionStateChangedEvent;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.session.SessionMsgType;
import org.thingsboard.server.dao.device.DeviceExceptionWarningService;

public class SecsClient {
    private static final Logger log = LoggerFactory.getLogger(SecsClient.class);
    private SecsCommunicator equip;
    private TbClusterService clusterService;
    private final HsmsSsCommunicatorConfig equipConf;
    private Device device;
    private DeviceExceptionWarningService warningService;

    public SecsClient(Device device, ApplicationEventPublisher applicationEventPublisher, TbClusterService clusterService, DeviceExceptionWarningService warningService) {
        this.clusterService = clusterService;
        this.device = device;
        this.warningService = warningService;
        SecsDeviceConnector connector = SecsDeviceConnector.parseFromJsonNode((JsonNode)device.getDeviceData().getConnector());
        this.equipConf = new HsmsSsCommunicatorConfig();
        this.equipConf.socketAddress(new InetSocketAddress(connector.getTransport().getHost(), connector.getTransport().getPort()));
        this.equipConf.connectionMode(HsmsConnectionMode.valueOf(connector.getTransport().getConnectionMode()));
        this.equipConf.sessionId(connector.getTransport().getSessionId());
        this.equipConf.timeout().t1(connector.getTransport().getTimeout().getT1() / 1000);
        this.equipConf.timeout().t2(connector.getTransport().getTimeout().getT2() / 1000);
        this.equipConf.timeout().t3(connector.getTransport().getTimeout().getT3() / 1000);
        this.equipConf.timeout().t4(connector.getTransport().getTimeout().getT4() / 1000);
        this.equipConf.timeout().t5(connector.getTransport().getTimeout().getT5() / 1000);
        this.equipConf.timeout().t6(connector.getTransport().getTimeout().getT6() / 1000);
        this.equipConf.timeout().t7(connector.getTransport().getTimeout().getT7() / 1000);
        this.equipConf.timeout().t8(connector.getTransport().getTimeout().getT8() / 1000);
        this.equipConf.isEquip(true);
        this.equipConf.gem().mdln("MDLN-A");
        this.equipConf.gem().softrev("000001");
        this.equipConf.gem().clockType(ClockType.A16);
        this.equip = HsmsSsCommunicator.newInstance(this.equipConf);
        this.addListeners(applicationEventPublisher);
    }

    private void addListeners(ApplicationEventPublisher applicationEventPublisher) {
        this.equip.addSecsMessageReceiveListener(msg -> {
            try {
                this.equipReceive(this.device, this.equip, msg);
            }
            catch (InterruptedException e) {
                log.error("deviceId: {},  deviceName: {}, socketAddress: {}, connectionMode: {}, sessionId: {}, isActive: {}, SecsMessageReceiveListener occurred error", new Object[]{this.device.getId(), this.device.getName(), this.equipConf.socketAddress(), this.equipConf.connectionMode(), this.equipConf.sessionId(), this.isActive(), e});
            }
        });
        this.equip.addSecsCommunicatableStateChangeListener(communicatable -> {
            boolean isOnline = false;
            if (communicatable) {
                try {
                    ONLACK onlack;
                    this.equip.gem().s1f1();
                    COMMACK commack = this.equip.gem().s1f13();
                    if (commack == COMMACK.OK && ((onlack = this.equip.gem().s1f17()) == ONLACK.OK || onlack == ONLACK.AlreadyOnline)) {
                        isOnline = true;
                    }
                }
                catch (SecsException | Secs2Exception | InterruptedException e) {
                    log.error("deviceId: {},  deviceName: {}, socketAddress: {}, connectionMode: {}, sessionId: {}, isActive: {}, isOnline:{}, Online failed", new Object[]{this.device.getId(), this.device.getName(), this.equipConf.socketAddress(), this.equipConf.connectionMode(), this.equipConf.sessionId(), this.isActive(), isOnline, e});
                }
            }
            log.info("deviceId: {},  deviceName: {}, socketAddress: {}, connectionMode: {}, sessionId: {}, isOnline:{}, SecsCommunicatableStateChangeListener communicatable: {}", new Object[]{this.device.getId(), this.device.getName(), this.equipConf.socketAddress(), this.equipConf.connectionMode(), this.equipConf.sessionId(), isOnline, communicatable});
            applicationEventPublisher.publishEvent((ApplicationEvent)new ConnectionStateChangedEvent((Object)this, this.device.getId(), Boolean.valueOf(isOnline)));
        });
        this.equip.addSecsLogListener(secsLog -> {
            if (secsLog != null && secsLog.value() != null) {
                if (secsLog.optionalValueString().get().contains("remote computer refused the network connection")) {
                    this.warningService.saveCurrentDayWarningByType(this.device.getId().toString(), null, DeviceExceptionWarningTypeEnum.HEARTBEAT_EXCEPTION);
                }
                if (secsLog.optionalValueString().get().contains("com.kuka.ecs.common.transport.secs.hsms.HsmsDetectTerminateException")) {
                    this.warningService.saveByCondition(this.device.getId().toString(), null, null, null, DeviceExceptionWarningTypeEnum.CONNECTION_DISCONNECTED);
                }
            }
        });
    }

    public boolean sendSecs2ToHost(int stream, int function, boolean wbit, Secs2 secs2) {
        try {
            this.equip.send(stream, function, wbit, secs2);
        }
        catch (SecsException | InterruptedException e) {
            log.error("deviceId: {},  deviceName: {}, socketAddress: {}, connectionMode: {}, sessionId: {}, isActive: {}, sendSecs2 occurred error", new Object[]{this.device.getId(), this.device.getName(), this.equipConf.socketAddress(), this.equipConf.connectionMode(), this.equipConf.sessionId(), this.isActive(), e});
            return false;
        }
        return true;
    }

    public boolean replySecs2ToHost(SecsMessage primaryMessage, int stream, int function, Secs2 secs2) {
        try {
            switch (stream) {
                case 1: {
                    switch (function) {
                        case 4: {
                            this.equip.gem().s1f4(primaryMessage, secs2);
                        }
                    }
                    break;
                }
                case 2: {
                    switch (function) {
                        case 50: {
                            this.equip.gem().s2f50(primaryMessage, CMDA.OK);
                        }
                    }
                }
            }
        }
        catch (SecsException | InterruptedException ignore) {
            log.error("InterruptedException", (Throwable)ignore);
        }
        return true;
    }

    public void tryConnect() {
        try {
            log.info("deviceId: {},  deviceName: {}, socketAddress: {}, connectionMode: {}, sessionId: {}, isActive: {}, tryConnect host", new Object[]{this.device.getId(), this.device.getName(), this.equipConf.socketAddress(), this.equipConf.connectionMode(), this.equipConf.sessionId(), this.isActive()});
            this.equip.open();
        }
        catch (IOException e) {
            log.error("deviceId: {},  deviceName: {}, socketAddress: {}, connectionMode: {}, sessionId: {}, isActive: {}, secs client connect error", new Object[]{this.device.getId(), this.device.getName(), this.equipConf.socketAddress(), this.equipConf.connectionMode(), this.equipConf.sessionId(), this.isActive(), e});
            try {
                this.equip.close();
            }
            catch (IOException ex) {
                log.error("deviceId: {},  deviceName: {}, socketAddress: {}, connectionMode: {}, sessionId: {}, isActive: {}, secs client close failed", new Object[]{this.device.getId(), this.device.getName(), this.equipConf.socketAddress(), this.equipConf.connectionMode(), this.equipConf.sessionId(), this.isActive(), e});
            }
        }
    }

    public void reConnect() {
        try {
            this.equip.openAndWaitUntilCommunicatable();
        }
        catch (IOException | InterruptedException e) {
            log.error("deviceId: {},  deviceName: {}, socketAddress: {}, connectionMode: {}, sessionId: {}, isActive: {}, reConnect failed", new Object[]{this.device.getId(), this.device.getName(), this.equipConf.socketAddress(), this.equipConf.connectionMode(), this.equipConf.sessionId(), this.isActive(), e});
        }
    }

    public boolean destroy() {
        try {
            log.info("deviceId: {},  deviceName: {}, socketAddress: {}, connectionMode: {}, sessionId: {}, isActive: {}, disconnect host", new Object[]{this.device.getId(), this.device.getName(), this.equipConf.socketAddress(), this.equipConf.connectionMode(), this.equipConf.sessionId(), this.isActive()});
            this.equip.close();
        }
        catch (IOException e) {
            log.error("deviceId: {},  deviceName: {}, socketAddress: {}, connectionMode: {}, sessionId: {}, isActive: {}, disconnect host failed", new Object[]{this.device.getId(), this.device.getName(), this.equipConf.socketAddress(), this.equipConf.connectionMode(), this.equipConf.sessionId(), this.isActive(), e});
            return false;
        }
        return true;
    }

    private boolean isActive() {
        return this.equip != null && this.equip.isCommunicatable();
    }

    private void equipReceive(Device device, SecsCommunicator comm, SecsMessage msg) throws InterruptedException {
        try {
            if (comm.deviceId() != msg.deviceId()) {
                SecsClient.sendSxF0(comm, msg, 0);
                comm.gem().s9f1(msg);
                return;
            }
            int strm = msg.getStream();
            int func = msg.getFunction();
            block1 : switch (strm) {
                case 1: {
                    switch (func) {
                        case 3: {
                            TbMsgMetaData metaData = new TbMsgMetaData();
                            metaData.putValue("transportType", DeviceTransportType.E82.name());
                            metaData.putValue("secsMessageHeader", Arrays.toString(msg.header10Bytes()));
                            metaData.putValue("secsMessageBody", msg.secs2().toString());
                            metaData.putValue("stream", String.valueOf(msg.getStream()));
                            metaData.putValue("function", String.valueOf(msg.getFunction()));
                            TbMsg tbMsg = TbMsg.newMsg((String)SessionMsgType.INBOUND_REQUEST.name(), (EntityId)device.getId(), (TbMsgMetaData)metaData, (TbMsgDataType)TbMsgDataType.TEXT, (String)"");
                            this.clusterService.pushMsgToRuleEngine(TenantId.defaultId(), (EntityId)device.getId(), tbMsg, null);
                            log.info("\u63a5\u6536\u5230\u7684\u6570\u636e: {}, deviceId: {}, sessionId: {}, msg: {}", new Object[]{device.getId(), comm.sessionId(), msg});
                            break block1;
                        }
                        case 13: {
                            comm.gem().s1f14(msg, COMMACK.OK);
                            break block1;
                        }
                        case 15: {
                            comm.gem().s1f16(msg);
                            break block1;
                        }
                        case 17: {
                            comm.gem().s1f18(msg, ONLACK.OK);
                            break block1;
                        }
                    }
                    SecsClient.sendSxF0(comm, msg, strm);
                    comm.gem().s9f5(msg);
                    break;
                }
                case 2: {
                    switch (func) {
                        case 31: {
                            comm.gem().s2f32(msg, TIACK.OK);
                            break block1;
                        }
                        case 33: {
                            comm.gem().s2f34(msg, DRACK.OK);
                            break block1;
                        }
                        case 35: {
                            comm.gem().s2f36(msg, LRACK.OK);
                            break block1;
                        }
                        case 37: {
                            comm.gem().s2f38(msg, ERACK.OK);
                            break block1;
                        }
                        case 49: {
                            TbMsgMetaData metaData = new TbMsgMetaData();
                            metaData.putValue("transportType", DeviceTransportType.E82.name());
                            metaData.putValue("secsMessageHeader", Arrays.toString(msg.header10Bytes()));
                            metaData.putValue("secsMessageBody", msg.secs2().toString());
                            metaData.putValue("stream", String.valueOf(msg.getStream()));
                            metaData.putValue("function", String.valueOf(msg.getFunction()));
                            TbMsg tbMsg = TbMsg.newMsg((String)SessionMsgType.INBOUND_REQUEST.name(), (EntityId)device.getId(), (TbMsgMetaData)metaData, (TbMsgDataType)TbMsgDataType.TEXT, (String)"");
                            this.clusterService.pushMsgToRuleEngine(TenantId.defaultId(), (EntityId)device.getId(), tbMsg, null);
                            log.info("\u63a5\u6536\u5230\u7684\u6570\u636e: {}, deviceId: {}, sessionId: {}, msg: {}", new Object[]{device.getId(), comm.sessionId(), msg});
                            break block1;
                        }
                    }
                    SecsClient.sendSxF0(comm, msg, strm);
                    comm.gem().s9f5(msg);
                    break;
                }
                case 6: {
                    switch (func) {
                        case 15: {
                            comm.send(msg, 6, 16, false, Secs2.list(comm.gem().autoDataId(), msg.secs2(), Secs2.list(Secs2.list(Secs2.uint4(1), Secs2.list(Clock.now().toAscii16())), Secs2.list(Secs2.uint4(2), Secs2.list(Secs2.int4(1234567890))))));
                            break block1;
                        }
                        case 17: {
                            break block1;
                        }
                        case 19: {
                            comm.send(msg, 6, 20, false, Secs2.list(Clock.now().toAscii16()));
                            break block1;
                        }
                        case 21: {
                            break block1;
                        }
                    }
                    SecsClient.sendSxF0(comm, msg, strm);
                    comm.gem().s9f5(msg);
                    break;
                }
                default: {
                    SecsClient.sendSxF0(comm, msg, 0);
                    comm.gem().s9f3(msg);
                    break;
                }
            }
        }
        catch (SecsException ignore) {
            log.error("InterruptedException", (Throwable)ignore);
        }
    }

    private static void sendSxF0(SecsCommunicator comm, SecsMessage primary, int strm) throws InterruptedException, SecsException {
        if (primary.wbit()) {
            comm.send(primary, strm, 0, false);
        }
    }
}

