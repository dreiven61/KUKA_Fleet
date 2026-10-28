package servostar.fleet.transfer;

import org.laxture.sbp.SpringBootPlugin;
import org.laxture.sbp.spring.boot.SpringBootstrap;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Paths;
import java.io.IOException;

/** Runs in Interface Platform; does not start a separate web server. */
public final class TransferPlugin extends SpringBootPlugin {
    public TransferPlugin(PluginWrapper wrapper) { super(wrapper); }

    @Override protected SpringBootstrap createSpringBootstrap() {
        return new SpringBootstrap(this, Bootstrap.class)
            .addPresetProperty("spring.config.name", "servostar-transfer")
            .addPresetProperty("spring.main.banner-mode", "off");
    }

    @Configuration(proxyBeanMethods = false)
    public static class Bootstrap {
        @Bean public TransferJournal servoStarTransferJournal() throws IOException {
            String path=System.getProperty("servostar.transfer.journal",
                "/opt/nav/robot/bin/Log/interface/servostar-transfer-journal.jsonl");
            return new TransferJournal(Boolean.getBoolean("servostar.transfer.offlineTest")?null:Paths.get(path));
        }
        @Bean(initMethod="start",destroyMethod="close") public WcsStatusMonitor servoStarStatusMonitor(TransferJournal journal) {
            return new WcsStatusMonitor(journal);
        }
        @Bean public FleetHttpClient servoStarFleetHttpClient(){return new FleetHttpClient();}
        @Bean public TransferService servoStarTransferService(FleetHttpClient client,TransferJournal journal){return new TransferService(client,journal);}
        @Bean public TransferController servoStarTransferController(TransferService service) {return new TransferController(service);}
        @Bean(initMethod="start",destroyMethod="close") public NativeHostService servoStarNativeHost(TransferService service,FleetHttpClient client,WcsStatusMonitor monitor){return new NativeHostService(service,client,monitor);}
        @Bean public NativeHostController servoStarNativeHostController(NativeHostService host){return new NativeHostController(host);}
    }
}
