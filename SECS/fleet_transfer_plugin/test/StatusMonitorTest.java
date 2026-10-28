package servostar.fleet.transfer;

import com.kuka.wcs.grpc.mission.*;
import io.grpc.*;
import io.grpc.stub.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.Map;

/** Loopback-only streaming RPC, with no Fleet or robot calls. */
public final class StatusMonitorTest {
    private static int checks;
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    private static void await(BooleanSupplier condition,String message) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean() && System.nanoTime()<end)Thread.sleep(10);
        check(condition.getAsBoolean(),message);
    }
    public static void main(String[] args) throws Exception {
        // The vendor fat SDK's service file lists only its static:// resolver.
        // Production packaging supplies the DNS service entry explicitly.
        NameResolverRegistry.getDefaultRegistry().register(new io.grpc.internal.DnsNameResolverProvider());
        CountDownLatch subscribed=new CountDownLatch(1);
        AtomicReference<StreamObserver<WcsMissionStatusResponse>> response=new AtomicReference<>();
        AtomicInteger subscriptions=new AtomicInteger();
        Server server=ServerBuilder.forPort(0).addService(new WcsMissionServiceGrpc.WcsMissionServiceImplBase(){
            @Override public void subscribeMissionStatus(EmptyParameterRequest req,StreamObserver<WcsMissionStatusResponse> out){
                response.set(out);subscriptions.incrementAndGet();
                subscribed.countDown(); // Keep the stream open, without sending a mission.
            }
        }).build().start();
        final int serverPort=server.getPort();
        TransferJournal journal=new TransferJournal(null);
        WcsStatusMonitor monitor=new WcsStatusMonitor(journal,"127.0.0.1",serverPort);
        try {
            monitor.start();
            if(!subscribed.await(5,TimeUnit.SECONDS))throw new AssertionError("Subscription request did not reach loopback server");
            await(()->journal.available(),"Idle stream transport must become ready before any mission");
            Map<String,Object> req=TransferTest.request("MONITOR_TEST");
            AtomicInteger sends=new AtomicInteger();
            TransferService service=new TransferService((path,token,body)->{
                if(path.equals("robotQuery")) return TransferService.map("success",true,"code","0");
                if(path.equals("containerQuery") || path.equals("containerQueryAll"))return TransferTest.cargo("SIM_CARGO_001","PORT_97");
                sends.incrementAndGet(); return TransferService.map("success",true,"code","0");
            },journal);
            check(Boolean.TRUE.equals(service.preview(req,"test-token").get("statusMonitorReady")),"Preview exposes readiness");
            check(Boolean.TRUE.equals(service.submit(req,"test-token").get("success")),"Idle subscription permits mock submission");
            check(sends.get()==1,"Exactly one mock submission");
            check("WAITING".equals(journal.state(TransferService.normalize(req),true).get("code")),"Transport readiness never fabricates completion");
            response.get().onError(Status.UNAVAILABLE.withDescription("Test disconnect").asRuntimeException());
            await(()->!journal.available(),"Stream failure must remove readiness");
            check("STATUS_UNCERTAIN".equals(journal.state(TransferService.normalize(req),true).get("code")),"Gap blocks incomplete command");
            org.springframework.http.ResponseEntity<Map<String,Object>> blocked=new TransferController(service).submit("test-token",TransferTest.request("BLOCKED_TEST"));
            check(blocked.getStatusCodeValue()==503,"Pre-submit block is 503, not ambiguous 502");
            check(Boolean.FALSE.equals(blocked.getBody().get("submitted")),"Blocked response explicitly says not submitted");
            check("STATUS_MONITOR_OR_JOURNAL_UNAVAILABLE".equals(blocked.getBody().get("code")),"Block reports monitor/journal cause");
            check(sends.get()==1,"Disconnected monitor never sends transport");
            await(()->subscriptions.get()>=2 && journal.available(),"Monitor reconnects to idle server");
            check("STATUS_UNCERTAIN".equals(journal.state(TransferService.normalize(req),true).get("code")),"Reconnect does not clear uncertain command");
        } finally {monitor.close();server.shutdownNow();server.awaitTermination(3,TimeUnit.SECONDS);}
        check(!journal.available(),"Close clears readiness");
        TransferJournal disconnected=new TransferJournal(null);
        WcsStatusMonitor offline=new WcsStatusMonitor(disconnected,"127.0.0.1",serverPort);
        try {offline.start();Thread.sleep(500);check(!disconnected.available(),"Unavailable endpoint cannot become ready");}
        finally {offline.close();}
        TransferService uncertain=new TransferService((path,token,body)->{
            if(path.equals("robotQuery"))return TransferService.map("success",true,"code","0");
            if(path.equals("containerQuery") || path.equals("containerQueryAll"))return TransferTest.cargo("SIM_CARGO_001","PORT_97");
            throw new java.io.IOException("Mock response lost after attempted POST");
        });
        check(new TransferController(uncertain).submit("test-token",TransferTest.request("UNCERTAIN_TEST")).getStatusCodeValue()==502,
            "A response lost after submission remains an uncertain 502");
        System.out.println("PASS: "+checks+" status monitor checks. Loopback RPC and mock transport only; no Fleet calls.");
    }
}
