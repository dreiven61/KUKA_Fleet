package servostar.fleet.transfer;

import java.util.*;

/** Atomic facility catalog populated from Fleet; production has no site IDs. */
final class WorkPorts {
    static final class Snapshot {
        final Map<String,Map<String,Object>> robots,ports,nodes;
        final Map<String,String> aliases;
        final List<String> ids,models;
        final List<Map<String,Object>> maps;
        Snapshot(Map<String,Map<String,Object>> r,Map<String,Map<String,Object>> p,Map<String,Map<String,Object>> n,Map<String,String> a,List<String> m,List<Map<String,Object>> floors){
            robots=Collections.unmodifiableMap(r);ports=Collections.unmodifiableMap(p);nodes=Collections.unmodifiableMap(n);aliases=Collections.unmodifiableMap(a);
            ids=Collections.unmodifiableList(new ArrayList<>(p.keySet()));models=Collections.unmodifiableList(m);maps=Collections.unmodifiableList(floors);
        }
    }
    private static volatile Snapshot current;
    private static final ThreadLocal<Snapshot> pinned=new ThreadLocal<>();
    private static volatile String error="Fleet facility scan has not completed";
    private static volatile long scannedAt;
    interface PublicationGuard { void publish(Snapshot next,Runnable commit); }
    private static volatile PublicationGuard publicationAllowed=(s,commit)->commit.run();
    private static final java.util.concurrent.atomic.AtomicLong scans=new java.util.concurrent.atomic.AtomicLong();
    private static volatile long publishedScan;
    static final List<String> ALL=new AbstractList<String>(){
        public String get(int index){return require().ids.get(index);}
        public int size(){return require().ids.size();}
        @Override public Iterator<String> iterator(){return require().ids.iterator();}
    };
    static boolean identifier(String value,int max){return value!=null && value.matches("[A-Za-z0-9_.:-]{1,"+max+"}");}
    private static String requiredId(Object value,int max){if(value==null || !identifier(String.valueOf(value),max))throw new IllegalArgumentException("Missing/invalid Fleet identifier");return String.valueOf(value);}
    private static String optionalText(Object value){return value==null?"":String.valueOf(value);}
    // Existing accepted jobs keep their last confirmed bindings during a scan
    // outage. New admission and selected inventory require a successful scan.
    private static Snapshot require(){Snapshot s=pinned.get();if(s==null)s=current;if(s==null)throw new IllegalStateException("FLEET_CATALOG_UNAVAILABLE: "+error);return s;}
    static <T> T withCurrent(java.util.concurrent.Callable<T> action)throws Exception{
        if(pinned.get()!=null)return action.call();Snapshot captured=current;
        if(captured==null)return action.call();
        pinned.set(captured);try{return action.call();}finally{pinned.remove();}
    }
    static void requireFresh(){require();if(!error.isEmpty())throw new IllegalStateException("FLEET_CATALOG_UNAVAILABLE: "+error);}
    static boolean ready(){return current!=null && error.isEmpty();}
    static void refresh(TransferService.FleetClient client,String token)throws Exception{
        long sequence=0;
        try{Map<String,Object> scanned;synchronized(client){sequence=scans.incrementAndGet();scanned=client.facilitySnapshot(token);}if(scanned==null){requireFresh();return;}install(scanned,sequence);}
        catch(Exception ex){synchronized(WorkPorts.class){if(sequence>=publishedScan){publishedScan=sequence;error="Fleet facility scan failed: "+ex.getClass().getSimpleName();}}throw ex;}
    }
    static void publicationGuard(PublicationGuard guard){publicationAllowed=guard;}
    static boolean preservesBindings(Snapshot next){
        Snapshot old=current;if(old==null)return true;
        for(Map.Entry<String,Map<String,Object>> p:old.ports.entrySet()){
            Map<String,Object> n=next.ports.get(p.getKey());
            if(n==null || !Objects.equals(p.getValue().get("nodeCode"),n.get("nodeCode")) || !Objects.equals(p.getValue().get("externalCode"),n.get("externalCode")))return false;
        }
        for(Map.Entry<String,String> alias:old.aliases.entrySet())if(!Objects.equals(alias.getValue(),next.aliases.get(alias.getKey())))return false;
        return true;
    }
    static void install(Map<String,Object> scanned){install(scanned,scans.incrementAndGet());}
    @SuppressWarnings("unchecked")
    private static void install(Map<String,Object> scanned,long sequence){
        if(sequence<publishedScan)return;
        Map<String,Map<String,Object>> robots=new TreeMap<>(),ports=new TreeMap<>(),nodes=new TreeMap<>();Map<String,String> aliases=new HashMap<>();Set<String> ambiguous=new HashSet<>();
        for(Map<String,Object> r:(List<Map<String,Object>>)scanned.get("robots")){
            String id=requiredId(r.get("robotId"),32);requiredId(r.get("robotType"),64);
            if(robots.put(id,new LinkedHashMap<>(r))!=null)throw new IllegalArgumentException("Invalid/duplicate Fleet VehicleID");
        }
        List<Map<String,Object>> maps=(List<Map<String,Object>>)scanned.get("maps");
        for(Map<String,Object> floor:maps)for(Map<String,Object> n:(List<Map<String,Object>>)floor.get("nodes")){
            String code=requiredId(n.get("nodeCode"),64);if(nodes.put(code,new LinkedHashMap<>(n))!=null)throw new IllegalArgumentException("Invalid/duplicate Fleet node code");
        }
        for(Map<String,Object> p:(List<Map<String,Object>>)scanned.get("ports")){
            String node=requiredId(p.get("nodeCode"),64),id=requiredId(p.get("portId"),64);
            if(!nodes.containsKey(node) || robots.containsKey(id) || ports.put(id,new LinkedHashMap<>(p))!=null)throw new IllegalArgumentException("Invalid/ambiguous Fleet port");
            for(String alias:Arrays.asList(id,node,optionalText(p.get("externalCode")),optionalText(p.get("legacyAlias")))){
                if(alias.isEmpty())continue;String old=aliases.put(alias,id);if(old!=null && !old.equals(id))ambiguous.add(alias);
            }
        }
        for(String alias:ambiguous){if(ports.containsKey(alias))throw new IllegalArgumentException("Fleet canonical port ID conflicts with another alias");aliases.remove(alias);}
        for(String alias:aliases.keySet())if(robots.containsKey(alias))throw new IllegalArgumentException("Fleet port alias conflicts with VehicleID");
        List<String> models=new ArrayList<>();for(Object model:(List<?>)scanned.get("models")){String id=requiredId(model,64);if(models.contains(id))throw new IllegalArgumentException("Duplicate Fleet model");models.add(id);}
        Snapshot next=new Snapshot(robots,ports,nodes,aliases,models,new ArrayList<>(maps));
        publicationAllowed.publish(next,()->{synchronized(WorkPorts.class){if(sequence<publishedScan)return;current=next;publishedScan=sequence;scannedAt=System.currentTimeMillis();error="";}});
    }
    static String canonical(String port){String id=require().aliases.get(port);if(id==null)throw new IllegalArgumentException("Unknown Fleet work port: "+port);return id;}
    static boolean contains(String port){return port!=null && require().aliases.containsKey(port);}
    static String node(String port){return String.valueOf(require().ports.get(canonical(port)).get("nodeCode"));}
    static String containerNode(String port){Map<String,Object> p=require().ports.get(canonical(port));String ext=optionalText(p.get("externalCode"));return ext.isEmpty()?String.valueOf(p.get("nodeCode")):ext;}
    static String port(String node){Snapshot s=require();String id=s.aliases.get(node);if(id==null)throw new IllegalStateException("Node is not a Fleet work port: "+node);return id;}
    static boolean sameNode(String port,String node){return contains(port) && (node(port).equals(node) || containerNode(port).equals(node));}
    static boolean samePort(String first,String second){return contains(first) && contains(second) && canonical(first).equals(canonical(second));}
    @SuppressWarnings("unchecked")
    static List<String> eligibleRobots(String source,String dest,String model){
        Snapshot s=require();List<String> out=new ArrayList<>();String vehicle=vehicle(source);
        if(!s.models.contains(model))throw new IllegalArgumentException("Unknown Fleet carrier model");
        List<Map<String,Object>> endpoints=new ArrayList<>();if(contains(source))endpoints.add(s.ports.get(canonical(source)));endpoints.add(s.ports.get(canonical(dest)));
        for(Map<String,Object> p:endpoints){List<?> allowed=(List<?>)p.getOrDefault("containerModelCode",Collections.emptyList());if(!allowed.isEmpty() && !allowed.contains(model))throw new IllegalArgumentException("Carrier model not supported at Fleet port");}
        for(Map.Entry<String,Map<String,Object>> r:s.robots.entrySet()){
            if(!vehicle.isEmpty() && !vehicle.equals(r.getKey()))continue;
            String type=String.valueOf(r.getValue().getOrDefault("robotType",""));boolean allowed=!type.isEmpty();
            for(Map<String,Object> p:endpoints){List<?> types=(List<?>)p.getOrDefault("robotTypeCode",Collections.emptyList());if(!types.isEmpty() && !types.contains(type))allowed=false;}
            if(allowed)out.add(r.getKey());
        }
        if(out.isEmpty())throw new IllegalStateException("No compatible Fleet robot for route/model");return out;
    }
    static String vehicle(String source){
        if(source==null)return "";Snapshot s=require();if(s.robots.containsKey(source))return source;
        String raw=source.startsWith("AMR_")?source.substring(4):"";return s.robots.containsKey(raw)?raw:"";
    }
    static boolean source(String source){return contains(source) || !vehicle(source).isEmpty();}
    static List<String> robotIds(){return new ArrayList<>(require().robots.keySet());}
    static List<String> robotModels(){Set<String> models=new TreeSet<>();for(Map<String,Object> r:require().robots.values()){String model=String.valueOf(r.getOrDefault("robotType",""));if(!model.isEmpty())models.add(model);}return new ArrayList<>(models);}
    static List<String> models(){return require().models;}
    static String robotModel(String vehicle){Map<String,Object> r=require().robots.get(vehicle);String model=r==null?"":String.valueOf(r.getOrDefault("robotType",""));if(model.isEmpty())throw new IllegalStateException("Fleet robot model unavailable");return model;}
    static boolean knownNode(String node){return node!=null && require().nodes.containsKey(node);}
    static boolean waitNode(String node){
        Map<String,Object> row=require().nodes.get(node);if(row==null)return false;
        for(Object function:(List<?>)row.getOrDefault("functions",Collections.emptyList()))if(Arrays.asList("1","4","10","18").contains(String.valueOf(function)))return false;
        return !require().ports.values().stream().anyMatch(p->node.equals(p.get("nodeCode")));
    }
    static Map<String,Object> status(){
        Snapshot s=current;return TransferService.map("ready",ready(),"scannedAt",scannedAt,"error",error,
            "robots",s==null?Collections.emptyList():new ArrayList<>(s.robots.values()),"ports",s==null?Collections.emptyList():new ArrayList<>(s.ports.values()),
            "models",s==null?Collections.emptyList():s.models,"maps",s==null?Collections.emptyList():s.maps);
    }
}
