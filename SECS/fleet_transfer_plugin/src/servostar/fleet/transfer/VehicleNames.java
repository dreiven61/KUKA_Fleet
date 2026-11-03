package servostar.fleet.transfer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

/** Optional site-owned Fleet RobotID <-> SECS VehicleID dictionary.
 * Fleet correlation/journals always retain the real RobotID. */
final class VehicleNames {
    private static final VehicleNames IDENTITY=new VehicleNames(null);
    private final HostState state;
    private volatile Map<String,String> active;
    private volatile String error="";
    private Path path;
    private boolean optional;
    static VehicleNames identity(){return IDENTITY;}
    VehicleNames(HostState state){
        this.state=state;
        active=Collections.unmodifiableMap(new LinkedHashMap<>(state==null?Collections.emptyMap():state.data.vehicleNames));
        validate(active);
    }
    void configure(Path path){configure(path,false);}
    void configure(Path path,boolean optional){this.path=path;this.optional=optional;}
    void requireReady(){if(!error.isEmpty())throw new IllegalStateException("VEHICLE_NAMES_UNAVAILABLE: "+error);}
    private static void validate(Map<String,String> names){
        Set<String> aliases=new HashSet<>();
        for(Map.Entry<String,String> e:names.entrySet()){
            if(!WorkPorts.identifier(e.getKey(),32) || !WorkPorts.identifier(e.getValue(),32))throw new IllegalArgumentException("RobotID/VehicleID must be 1-32 ASCII identifier characters");
            if(!aliases.add(e.getValue()) || !aliases.add("AMR_"+e.getValue()))throw new IllegalArgumentException("Duplicate/ambiguous VehicleID");
        }
    }
    private static void attributes(Element element,String... expected)throws IOException{
        Set<String> allowed=new HashSet<>(Arrays.asList(expected));NamedNodeMap attrs=element.getAttributes();
        for(int i=0;i<attrs.getLength();i++)if(!allowed.contains(attrs.item(i).getNodeName()))throw new IOException("Unknown RobotNames XML attribute");
        for(String name:expected)if(!element.hasAttribute(name))throw new IOException("Missing RobotNames XML attribute: "+name);
    }
    static Map<String,String> read(Path file)throws Exception{
        if(Files.size(file)>1024*1024)throw new IOException("RobotNames XML exceeds 1 MiB");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setXIncludeAware(false);f.setExpandEntityReferences(false);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        DocumentBuilder builder=f.newDocumentBuilder();builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){
            @Override public void error(SAXParseException e)throws SAXException{throw e;}
            @Override public void fatalError(SAXParseException e)throws SAXException{throw e;}
        });
        Document doc;try(InputStream input=Files.newInputStream(file)){doc=builder.parse(input);}
        Element root=doc.getDocumentElement();
        if(!"RobotNames".equals(root.getTagName()))throw new IOException("RobotNames root required");attributes(root,"version");
        if(!"1".equals(root.getAttribute("version")))throw new IOException("Unsupported RobotNames XML version");
        Map<String,String> names=new LinkedHashMap<>();
        NodeList nodes=root.getChildNodes();
        for(int i=0;i<nodes.getLength();i++){
            Node node=nodes.item(i);if(node.getNodeType()==Node.COMMENT_NODE)continue;
            if(node.getNodeType()==Node.TEXT_NODE && node.getTextContent().trim().isEmpty())continue;
            if(!(node instanceof Element) || !"Robot".equals(((Element)node).getTagName()))throw new IOException("Only Robot elements allowed");
            Element robot=(Element)node;attributes(robot,"fleetId","vehicleId");
            if(robot.getElementsByTagName("*").getLength()!=0 || !robot.getTextContent().trim().isEmpty())throw new IOException("Robot mapping must be empty element");
            if(names.put(robot.getAttribute("fleetId"),robot.getAttribute("vehicleId"))!=null)throw new IOException("Duplicate Fleet RobotID");
            if(names.size()>1000)throw new IOException("Too many Robot mappings");
        }
        if(names.isEmpty())throw new IOException("RobotNames XML contains no mappings");validate(names);return names;
    }
    void refresh(boolean communicating){refresh(()->communicating);}
    synchronized void refresh(java.util.function.BooleanSupplier communicating){
        if(state==null)return;
        try{
            boolean absentDefault=path!=null && optional && !Files.exists(path) && active.isEmpty();
            Map<String,String> desired=path==null || absentDefault?Collections.emptyMap():read(path);
            if(!desired.isEmpty()){
                WorkPorts.requireFresh();
                for(String id:WorkPorts.robotIds())if(!desired.containsKey(id))throw new IllegalStateException("Fleet RobotID has no VehicleID mapping: "+id);
                for(String name:desired.values())if(WorkPorts.contains(name) || WorkPorts.contains("AMR_"+name))throw new IllegalStateException("VehicleID conflicts with a Fleet port alias");
            }
            synchronized(state){
                state.requireHealthy();
                if(!active.equals(desired)){
                    // Changing names mid-session or before an old ACK/set-clear
                    // pair finishes could make the same robot look like two.
                    boolean blocked=communicating.getAsBoolean() || !state.data.alarms.isEmpty()
                        || state.data.jobs.values().stream().anyMatch(j->state.unsettled(j))
                        || state.data.reports.stream().anyMatch(d->!"ACKED".equals(d.state))
                        || state.data.cargoOperations.values().stream().anyMatch(op->Arrays.asList("PREPARING","MUTATING","UNCERTAIN").contains(op.phase));
                    if(blocked)throw new IllegalStateException("Name changes await MCS disconnect, settled jobs, alarms and report ACKs");
                    Map<String,String> previous=state.data.vehicleNames;
                    state.data.vehicleNames=new LinkedHashMap<>(desired);
                    try{state.save();}catch(RuntimeException storageFailure){state.data.vehicleNames=previous;throw storageFailure;}
                    active=Collections.unmodifiableMap(new LinkedHashMap<>(desired));
                }
                error="";
            }
        }catch(Exception ex){
            error=ex instanceof IllegalStateException || ex instanceof IllegalArgumentException?ex.getMessage():"RobotNames XML unavailable or invalid: "+ex.getClass().getSimpleName();
        }
    }
    String vehicle(String fleetId){
        if(fleetId==null || fleetId.isEmpty())return "";Map<String,String> names=active;
        if(names.isEmpty())return fleetId;String name=names.get(fleetId);
        if(name==null)throw new IllegalStateException("Fleet RobotID has no VehicleID mapping: "+fleetId);
        if(WorkPorts.contains(name) || WorkPorts.contains("AMR_"+name))throw new IllegalStateException("VehicleID conflicts with a Fleet port alias");return name;
    }
    boolean preservesPortNamespace(WorkPorts.Snapshot next){
        for(String name:active.values())if(next.aliases.containsKey(name) || next.aliases.containsKey("AMR_"+name))return false;return true;
    }
    /** Only known vehicle IDs are converted; ports and map node UUIDs remain ports. */
    String location(String internal){
        if(internal==null || internal.isEmpty())return "";
        if(WorkPorts.contains(internal))return WorkPorts.canonical(internal);
        Map<String,String> names=active;if(names.isEmpty())return internal;
        if(names.containsKey(internal))return vehicle(internal);
        if(internal.startsWith("AMR_") && names.containsKey(internal.substring(4)))return vehicle(internal.substring(4));
        if(!WorkPorts.vehicle(internal).isEmpty())return vehicle(WorkPorts.vehicle(internal));
        return internal;
    }
    String sourceFromSecs(String wire){
        requireReady();Map<String,String> names=active;if(names.isEmpty() || WorkPorts.contains(wire))return wire;
        for(Map.Entry<String,String> e:names.entrySet()){
            if(e.getValue().equals(wire))return e.getKey();
            if(("AMR_"+e.getValue()).equals(wire))return "AMR_"+e.getKey();
        }
        throw new IllegalArgumentException("Unknown MCS VehicleID/source port");
    }
    Map<String,Object> status(){return TransferService.map("configured",path!=null && (!optional || Files.exists(path)),"file",path==null?"":path.toString(),"enabled",!active.isEmpty(),"ready",error.isEmpty(),"error",error,"bindings",active);}
}
