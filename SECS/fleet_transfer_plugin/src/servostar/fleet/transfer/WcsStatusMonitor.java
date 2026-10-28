package servostar.fleet.transfer;

import com.kuka.wcs.grpc.mission.*;
import io.grpc.*;
import io.grpc.stub.*;
import java.util.concurrent.*;
import java.util.Map;
import java.util.logging.Logger;

/** Independent read-only broadcast subscription; no WCS ack, dispatch or robot action. */
public final class WcsStatusMonitor implements AutoCloseable {
    private final TransferJournal journal;
    private final String host;
    private final int port;
    private ManagedChannel channel;
    private final ScheduledExecutorService reconnect=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"servostar-status-reconnect");t.setDaemon(true);return t;
    });
    private volatile boolean closed;
    private long generation;
    private boolean transportReady;
    private volatile java.util.function.Consumer<Map<String,Object>> observer;
    public void setObserver(java.util.function.Consumer<Map<String,Object>> observer){this.observer=observer;}
    private ClientCallStreamObserver<EmptyParameterRequest> active;
    private static final Logger LOG=Logger.getLogger(WcsStatusMonitor.class.getName());
    public WcsStatusMonitor(TransferJournal journal) {this(journal,"kwcs-engine",7998);}
    WcsStatusMonitor(TransferJournal journal,String host,int port) {this.journal=journal;this.host=host;this.port=port;}
    public synchronized void start() {
        if(Boolean.getBoolean("servostar.transfer.offlineTest")) return;
        channel=ManagedChannelBuilder.forTarget("dns:///"+host+":"+port).usePlaintext().build();
        enqueue(()->subscribe());
    }
    private synchronized void subscribe() {
        if(closed)return;
        final long attempt=++generation;
        transportReady=false;
        WcsMissionServiceGrpc.newStub(channel).subscribeMissionStatus(EmptyParameterRequest.getDefaultInstance(),
            new ClientResponseObserver<EmptyParameterRequest,WcsMissionStatusResponse>() {
                public void beforeStart(ClientCallStreamObserver<EmptyParameterRequest> stream) {
                    active=stream;
                }
                public void onNext(WcsMissionStatusResponse e) {
                    enqueue(()->{
                        synchronized(WcsStatusMonitor.this) {
                            if(closed || attempt!=generation)return;
                            try {
                                Map<String,Object> fact=event(e);journal.observe(fact);
                                java.util.function.Consumer<Map<String,Object>> sink=observer;if(sink!=null)sink.accept(fact);
                            }
                            catch(RuntimeException invalid) { lost(attempt,"invalid status event"); }
                        }
                    });
                }
                public void onError(Throwable error) {enqueue(()->lost(attempt,Status.fromThrowable(error).getCode().name()));}
                public void onCompleted() {enqueue(()->lost(attempt,"stream completed"));}
            });
        watchTransport(attempt);
    }
    /** A server-streaming request is immediately half-closed by the stub; its
     * outbound onReady callback is not a subscription acknowledgement. Track
     * the HTTP/2 transport instead. This permits submission, but never proves
     * pickup/completion: those still require correlated inbound WCS events. */
    private synchronized void watchTransport(long attempt) {
        if(closed || attempt!=generation)return;
        ConnectivityState state=channel.getState(true);
        if(state==ConnectivityState.READY && !transportReady) {
            transportReady=true;
            journal.streamReady();
            LOG.info("WCS status transport ready; progress/completion still require observed events");
        } else if(transportReady && state!=ConnectivityState.READY) {
            lost(attempt,"transport "+state.name()); return;
        }
        channel.notifyWhenStateChanged(state,()->enqueue(()->watchTransport(attempt)));
    }
    private synchronized void lost(long attempt,String reason) {
        if(closed || attempt!=generation)return;
        ++generation; transportReady=false;
        journal.streamLost();
        if(active!=null) {active.cancel("Reconnecting status subscription",null);active=null;}
        LOG.warning("WCS status subscription lost ("+reason+"); incomplete commands blocked; reconnect in 5 seconds");
        if(!reconnect.isShutdown()) reconnect.schedule(()->subscribe(),5,TimeUnit.SECONDS);
    }
    private void enqueue(Runnable task) {
        if(closed)return;
        try {reconnect.execute(task);} catch(RejectedExecutionException stopped) {
            if(!closed)throw stopped;
        }
    }
    static Map<String,Object> event(WcsMissionStatusResponse e) {
        return TransferService.map("commandId",e.getTaskCode(),"responseId",e.getResponseId(),
            "robotId",e.getRobotId(),"carrierId",e.getContainerCode(),"instanceId",e.getInstanceId(),
            "success",e.getSuccess(),"resultCode",e.getResultCode(),"status",e.getMissionStatus().name(),
            "actionType",e.getActionType().name(),"sequence",e.hasNodeSequence()?e.getNodeSequence().getValue():-1,
            "currentNode",e.getCurrentNodeCode(),"nextNode",e.getNextNodeCode(),"carrierLocation",e.getContainerLocationCode(),
            "actionTime",e.getActionTime(),"endTime",e.getEndTime(),"manualCompleteFlag",e.getManualCompleteFlag(),
            "warnCode",e.getWarnCode(),"warnInfo",e.getWarnInfo());
    }
    public synchronized void close() {
        closed=true; journal.streamLost(); reconnect.shutdownNow();
        if(channel!=null)channel.shutdownNow();
    }
}
