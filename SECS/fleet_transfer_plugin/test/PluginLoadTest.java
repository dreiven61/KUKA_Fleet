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
                                if(name.startsWith("io.grpc.") || name.startsWith("com.kuka.wcs.grpc.") || name.startsWith("com.google.protobuf.") || name.startsWith("com.kuka.ecs.common.transport.secs."))
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
            PluginState state=manager.startPlugin(id);
            if (state!=PluginState.STARTED) throw new AssertionError("Start failed: "+manager.getPluginStartingError(id));
            long count=mapping.getHandlerMethods().keySet().stream().filter(m->m.toString().contains("mes-transfer")).count();
            if(count!=11) throw new AssertionError("Expected eleven submit/query/stage mappings, found "+count);
            long nativeCount=mapping.getHandlerMethods().keySet().stream().filter(m->m.toString().contains("mes-secs")).count();
            if(nativeCount!=2)throw new AssertionError("Expected two native SECS administrative mappings, found "+nativeCount);
            System.out.println("PASS: SBP/PF4J loaded packaged JAR, started child context, registered eleven routes. WCS connection disabled in test.");
            manager.stopPlugin(id);
            if(mapping.getHandlerMethods().keySet().stream().anyMatch(m->m.toString().contains("mes-transfer"))) throw new AssertionError("Routes remained after stop");
            System.out.println("PASS: plugin stop removed its routes.");
            manager.unloadPlugin(id);
        } finally { host.close(); }
    }
}
