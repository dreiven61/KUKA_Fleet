/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.data.common;

public enum SecsErsEvent {
    TRANSFER_INITIATED("TransferInitiated", "\u642c\u8fd0\u5df2\u521d\u59cb\u5316"),
    VEHICLE_ASSIGNED("VehicleAssigned", "\u5df2\u6307\u6d3eAMR"),
    VEHICLE_ARRIVED("VehicleArrived", "AMR\u5df2\u5230\u8fbe"),
    TRANSFERRING("Transferring", "\u642c\u8fd0\u4e2d"),
    VEHICLE_ACQUIRE_STARTED("VehicleAcquireStarted", "AMR\u5df2\u5f00\u59cb\u53d6\u6599"),
    CARRIER_INSTALLED("CarrierInstalled", "AMR\u5df2\u53d6\u5230\u4e00\u7269\u6599"),
    VEHICLE_ACQUIRE_COMPLETED("VehicleAcquireCompleted", "AMR\u5df2\u53d6\u5230\u6240\u6709\u7269\u6599"),
    VEHICLE_DEPARTED("VehicleDeparted", "AMR\u5df2\u79bb\u5f00"),
    VEHICLE_DEPOSIT_STARTED("VehicleDepositStarted", "AMR\u5df2\u5f00\u59cb\u5378\u6599"),
    CARRIER_REMOVED("CarrierRemoved", "AMR\u5df2\u5378\u8f7d\u4e00\u7269\u6599"),
    VEHICLE_DEPOSIT_COMPLETED("VehicleDeposit-Completed", "AMR\u5df2\u5378\u8f7d\u6240\u6709\u7269\u6599"),
    VEHICLE_UNASSIGNED("VehicleUnassigned", "\u5df2\u91ca\u653eAMR"),
    TRANSFER_COMPLETED("TransferCompleted", "AMR\u642c\u8fd0\u5b8c\u6210"),
    ALARM_SET("AlarmSet", "\u62a5\u8b66\u53d1\u9001"),
    ALARM_CLEARED("AlarmCleared", "\u62a5\u8b66\u5df2\u6e05\u9664");

    String name;
    String desc;

    private SecsErsEvent(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public static SecsErsEvent nameOf(String name) {
        for (SecsErsEvent resource : SecsErsEvent.values()) {
            if (!resource.getName().equalsIgnoreCase(name)) continue;
            return resource;
        }
        return null;
    }

    public String getName() {
        return this.name;
    }

    public String getDesc() {
        return this.desc;
    }
}

