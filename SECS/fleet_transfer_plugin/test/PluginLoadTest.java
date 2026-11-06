package servostar.fleet.transfer;

import java.nio.file.*;
import org.laxture.sbp.SpringBootPluginManager;
import org.laxture.sbp.internal.PluginRequestMappingHandlerMapping;
import org.pf4j.PluginState;
import org.springframework.context.support.GenericApplicationContext;

/** Runs without our src/classes on the application classpath: exercises actual JAR loading. */
public final class PluginLoadTest {
    public static void main(String[] args) throws Exception {
        GenericApplicationContext host=new GenericApplicationContext();
        PluginRequestMappingHandlerMapping mapping=new PluginRequestMappingHandlerMapping();
        host.getBeanFactory().registerSingleton("requestMappingHandlerMapping",mapping);
        host.refresh();
        mapping.setApplicationContext(host); mapping.afterPropertiesSet();
        SpringBootPluginManager manager=new SpringBootPluginManager(Paths.get(args[0]).toAbsolutePath().getParent()) {
            @Override protected org.pf4j.PluginLoader createPluginLoader() {
                return new org.pf4j.PluginLoader() {
                    public boolean isApplicable(Path path) { return true; }
                    public ClassLoader loadPlugin(Path path,org.pf4j.PluginDescriptor descriptor) {
                        ClassLoader isolatedParent=new ClassLoader(PluginLoadTest.class.getClassLoader()) {
                            @Override public java.util.Enumeration<java.net.URL> getResources(String name) throws java.io.IOException {
                                if(name.startsWith("META-INF/services/io.grpc.")) return java.util.Collections.emptyEnumeration();
                                return super.getResources(name);
                            }
                            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                                if(name.startsWith("io.grpc.") || name.startsWith("com.kuka.rcs.") || name.startsWith("com.kuka.wcs.grpc.") || name.startsWith("com.google.protobuf.") || name.startsWith("com.kuka.ecs.common.transport.secs."))
                                    throw new ClassNotFoundException(name);
                                return super.loadClass(name,resolve);
                            }
                        };
                        org.laxture.sbp.internal.SpringBootPluginClassLoader loader=
                            new org.laxture.sbp.internal.SpringBootPluginClassLoader(managerReference(),descriptor,isolatedParent);
                        loader.setPluginFirstClasses(java.util.Collections.emptyList());
                        loader.setPluginOnlyResources(java.util.Collections.emptyList());
                        loader.addFile(path.toFile()); return loader;
                    }
                };
            }
            private org.pf4j.PluginManager managerReference() {return this;}
        };
        manager.setApplicationContext(host);
        manager.setProfiles(new String[0]);
        manager.setAutoStartPlugin(false);
        manager.presetProperties("spring.config.name","servostar-transfer");
        manager.presetProperties("spring.main.banner-mode","off");
        try {
            // The real host keeps grpc classes inside STANDARD-API's plugin loader.
            // Load that actual dependency before resolving our plugin.
            String dependency=manager.loadPlugin(Paths.get(args[1]).toAbsolutePath());
            if(!"STANDARD-API".equals(dependency)) throw new AssertionError("Unexpected dependency");
            // Fixture: production STANDARD-API is already STARTED. Do not run
            // its application against a live Fleet from this offline test.
            manager.getPlugin(dependency).setPluginState(PluginState.STARTED);
            String id=manager.loadPlugin(Paths.get(args[0]).toAbsolutePath());
            if(manager.getPlugin(id).getDescriptor().getDependencies().stream().noneMatch(d->"STANDARD-API".equals(d.getPluginId())))
                throw new AssertionError("Missing STANDARD-API dependency");
            ClassLoader pluginLoader=manager.getPluginClassLoader(id);
            java.lang.reflect.Field advertisedVersion=pluginLoader.loadClass("servostar.fleet.transfer.NativeHostService").getDeclaredField("VERSION");
            advertisedVersion.setAccessible(true);
            if(!manager.getPlugin(id).getDescriptor().getVersion().equals(advertisedVersion.get(null)))
                throw new AssertionError("SECS/status version differs from packaged Plugin-Version");
            if(pluginLoader.loadClass("io.grpc.Channel").getClassLoader()!=pluginLoader)
                throw new AssertionError("gRPC runtime missing from the isolated custom plugin");
            Class<?> builderType=pluginLoader.loadClass("io.grpc.ManagedChannelBuilder");
            Object builder=builderType.getMethod("forAddress",String.class,int.class).invoke(null,"127.0.0.1",1);
            builder=builderType.getMethod("usePlaintext").invoke(builder);
            Object channel=builderType.getMethod("build").invoke(builder);
            pluginLoader.loadClass("io.grpc.ManagedChannel").getMethod("shutdownNow").invoke(channel);
            Class.forName("com.kuka.wcs.grpc.mission.WcsMissionServiceGrpc",true,pluginLoader);
            if(pluginLoader.loadClass("com.kuka.ecs.common.transport.secs.hsmsss.HsmsSsCommunicator").getClassLoader()!=pluginLoader)
                throw new AssertionError("SECS transport missing from isolated plugin");
            System.out.println("PASS: isolated gRPC runtime, transport provider and WCS stub loaded; no RPC sent.");
            Class<?> statusType=pluginLoader.loadClass("servostar.fleet.transfer.SelectedStatusService");
            Class<?> stateType=pluginLoader.loadClass("servostar.fleet.transfer.HostState");
            java.lang.reflect.Constructor<?> stateCtor=stateType.getDeclaredConstructor(Path.class);stateCtor.setAccessible(true);
            java.lang.reflect.Constructor<?> statusCtor=statusType.getDeclaredConstructor(pluginLoader.loadClass("servostar.fleet.transfer.TransferService$FleetClient"),stateType,String.class);statusCtor.setAccessible(true);
            Object selected=statusCtor.newInstance(null,stateCtor.newInstance(new Object[]{null}),"");
            java.lang.reflect.Method vehicleValues=statusType.getDeclaredMethod("vehicleValues",java.util.Map.class);vehicleValues.setAccessible(true);
            java.util.Map<String,Object> robot=new java.util.HashMap<>();robot.put("status",3);robot.put("occupyStatus",0);
            vehicleValues.invoke(selected,java.util.Collections.singletonMap("1",robot));
            System.out.println("PASS: packaged vehicle snapshot executes with private RCS protobuf classes unavailable.");
            Path historical=Paths.get(args[0]).toAbsolutePath().getParent().getParent().resolve("test/fixtures/return_6894_journal.jsonl");
            Path journalCopy=Files.createTempDirectory("isolated-cold-start-").resolve("journal.jsonl");Files.copy(historical,journalCopy);
            Class<?> journalType=pluginLoader.loadClass("servostar.fleet.transfer.TransferJournal");
            Object restoredJournal=journalType.getConstructor(Path.class).newInstance(journalCopy);
            if(!Boolean.FALSE.equals(journalType.getMethod("available").invoke(restoredJournal)))throw new AssertionError("Catalog-less historical journal accepted transport");
            if(!java.util.Arrays.equals(Files.readAllBytes(historical),Files.readAllBytes(journalCopy)))throw new AssertionError("Cold startup changed historical journal");
            System.out.println("PASS: actual packaged historical journal constructs before Fleet catalog without changing history or allowing transport.");
            Class<?> catalogType=pluginLoader.loadClass("servostar.fleet.transfer.WorkPorts");
            java.lang.reflect.Method catalogStatus=catalogType.getDeclaredMethod("status");catalogStatus.setAccessible(true);catalogStatus.invoke(null);
            Class<?> mapReaderType=pluginLoader.loadClass("servostar.fleet.transfer.FleetMapReader");
            java.lang.reflect.Method decodeNode=mapReaderType.getDeclaredMethod("node",byte[].class);decodeNode.setAccessible(true);
            byte[] nodeBytes=com.kuka.rcs.bd.grpc.map.NodeGto.newBuilder().setNodeUuid("isolated-node").setNodeLabel("71").build().toByteArray();
            java.util.Map<?,?> decodedNode=(java.util.Map<?,?>)decodeNode.invoke(null,(Object)nodeBytes);
            if(!"isolated-node".equals(decodedNode.get("nodeCode")))throw new AssertionError("Packaged metadata decoder failed");
            java.lang.reflect.Constructor<?> mapCtor=mapReaderType.getDeclaredConstructor(String.class);mapCtor.setAccessible(true);
            java.lang.reflect.Method readMaps=mapReaderType.getDeclaredMethod("read");readMaps.setAccessible(true);
            try{readMaps.invoke(mapCtor.newInstance("127.0.0.1:1"));throw new AssertionError("Unavailable metadata unexpectedly succeeded");}
            catch(java.lang.reflect.InvocationTargetException ex){
                if(!ex.getCause().getClass().getName().equals("io.grpc.StatusRuntimeException"))throw ex;
            }
            System.out.println("PASS: isolated catalog initialization, metadata protobuf decoding and unavailable-map RPC execute without linkage errors.");
            Class<?> namesType=pluginLoader.loadClass("servostar.fleet.transfer.VehicleNames");
            java.lang.reflect.Method readNames=namesType.getDeclaredMethod("read",Path.class);readNames.setAccessible(true);
            Path namesExample=Paths.get(args[0]).toAbsolutePath().getParent().getParent().resolve("../native_host/servostar-robot-names.example.xml").normalize();
            if(!"AGV001".equals(((java.util.Map<?,?>)readNames.invoke(null,namesExample)).get("1")))throw new AssertionError("Packaged XML mapping parser failed");
            System.out.println("PASS: actual packaged XML VehicleID parser executes in isolated plugin loader; no Fleet connection.");
            PluginState state=manager.startPlugin(id);
            if (state!=PluginState.STARTED) throw new AssertionError("Start failed: "+manager.getPluginStartingError(id));
            long count=mapping.getHandlerMethods().keySet().stream().filter(m->m.toString().contains("mes-transfer")).count();
            if(count!=11) throw new AssertionError("Expected eleven submit/query/stage mappings, found "+count);
            long nativeCount=mapping.getHandlerMethods().keySet().stream().filter(m->m.toString().contains("mes-secs")).count();
            if(nativeCount!=4)throw new AssertionError("Expected four native SECS administrative mappings, found "+nativeCount);
            System.out.println("PASS: SBP/PF4J loaded packaged JAR, started child context, registered eleven transfer and four native administrative routes. WCS connection disabled in test.");
            manager.stopPlugin(id);
            if(mapping.getHandlerMethods().keySet().stream().anyMatch(m->m.toString().contains("mes-transfer"))) throw new AssertionError("Routes remained after stop");
            if(mapping.getHandlerMethods().keySet().stream().anyMatch(m->m.toString().contains("mes-secs"))) throw new AssertionError("Native administrative routes remained after stop");
            System.out.println("PASS: plugin stop removed its routes.");
            manager.unloadPlugin(id);
        } finally { host.close(); }
    }
}
