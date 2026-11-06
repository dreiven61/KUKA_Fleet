package servostar.fleet.transfer;

import java.util.*;

/** Converts Fleet map insertion/removal observations into durable SECS events. */
final class VehicleInstallationMonitor {
    private final TransferService.FleetClient client;
    private final String token;
    private final HostState state;
    private final VehicleNames names;

    VehicleInstallationMonitor(TransferService.FleetClient client,String token,HostState state,VehicleNames names){
        this.client=client;
        this.token=token;
        this.state=state;
        this.names=names;
    }

    void poll()throws Exception {
        Map<String,Object> reply=client.readStatus("robotQuery",token,Collections.emptyMap());
        if(!NativeHostEngine.ok(reply) || !(reply.get("data") instanceof List))
            throw new IllegalStateException("Fleet vehicle snapshot unavailable");
        List<?> rows=(List<?>)reply.get("data");
        if(rows.size()>=1000)throw new IllegalStateException("Fleet vehicle snapshot possibly truncated");
        Map<String,Boolean> snapshot=new TreeMap<>();
        for(Object item:rows){
            if(!(item instanceof Map))throw new IllegalStateException("Invalid Fleet vehicle row");
            @SuppressWarnings("unchecked") Map<String,Object> row=(Map<String,Object>)item;
            String id=NativeHostEngine.text(row,"robotId");
            if(!WorkPorts.identifier(id,32))throw new IllegalStateException("Invalid Fleet RobotID");
            boolean installed=SelectedStatusService.robotStatus(row)!=SelectedStatusService.DEPARTURE;
            if(snapshot.put(id,installed)!=null)throw new IllegalStateException("Duplicate Fleet RobotID");
        }
        synchronized(state){
            state.requireHealthy();
            names.requireReady();
            Map<String,Boolean> previous=state.data.vehicleInstallations;
            // First startup records a baseline. Earlier transitions cannot be dated or replayed.
            Map<String,Boolean> next=previous==null?new LinkedHashMap<>():new LinkedHashMap<>(previous);
            List<HostState.Delivery> reports=new ArrayList<>();
            for(Map.Entry<String,Boolean> robot:snapshot.entrySet()){
                String fleetId=robot.getKey();
                boolean installed=robot.getValue();
                Boolean before=next.put(fleetId,installed);
                if(previous==null || Objects.equals(before,installed) || before==null && !installed)continue;
                HostState.Delivery event=new HostState.Delivery();
                event.kind="VEHICLE_INSTALLATION";
                event.ceid=installed?608:609;
                event.vehicle=names.vehicle(fleetId); // Preserve the wire ID across resends and restarts.
                event.key="vehicle-installation:"+(state.data.sequence+reports.size());
                reports.add(event);
            }
            // A missing row, communication fault or empty response cannot prove removal.
            if(next.equals(previous))return;
            if(state.data.reports.size()+reports.size()>20000 || state.data.sequence+reports.size()-1>0xffffffffL)
                throw new IllegalStateException("Vehicle installation report capacity exceeded");
            for(HostState.Delivery event:reports)state.add(event);
            state.data.vehicleInstallations=next;
            state.save();
        }
    }
}
