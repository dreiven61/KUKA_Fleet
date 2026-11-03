package servostar.fleet.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Intentionally runs without FixtureRunner: simulates a fresh plugin loader. */
public final class ColdStartCatalogTest {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        Path fixture=Paths.get(args[0]).resolve("test/fixtures/return_6894_journal.jsonl");
        byte[] original=Files.readAllBytes(fixture);
        Path journalFile=Files.createTempDirectory("cold-catalog-").resolve("journal.jsonl");Files.write(journalFile,original);
        ObjectMapper json=new ObjectMapper();
        Map<String,Object> first=json.readValue(Files.readAllLines(fixture).get(0),Map.class);
        Map<String,Object> request=(Map<String,Object>)first.get("request");
        check(!WorkPorts.ready(),"Must exercise genuinely uninitialized catalog");
        Path torn=journalFile.resolveSibling("torn.jsonl");Files.write(torn,"{\"kind\":\"RESERVE\"".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try{new TransferJournal(torn);throw new AssertionError("Torn history accepted");}catch(java.io.IOException expected){checks++;}
        TransferJournal journal=new TransferJournal(journalFile);
        check(Arrays.equals(original,Files.readAllBytes(journalFile)),"Reading historical journal never changes records");
        journal.streamReady();
        check(!journal.available(),"New submissions blocked before catalog is restored");
        check(!Boolean.TRUE.equals(journal.stage(request,"completed").get("success")),"Unbound history cannot report false completion");
        WorkPorts.install(FixtureRunner.catalog());
        check(journal.available(),"Journal becomes available after Fleet metadata scan");
        check(Boolean.TRUE.equals(journal.stage(request,"completed").get("success")),"Captured carried completion restored after metadata scan");
        check(Arrays.equals(original,Files.readAllBytes(journalFile)),"Deferred replay never submits or rewrites history");
        try{journal.reserve(request);throw new AssertionError("Duplicate historical command accepted");}catch(IllegalArgumentException expected){checks++;}
        for(String vehicle:Arrays.asList("AGV_WEST_01","999999999999999999999999")){
            Map<String,Object> normal=TransferService.normalize(TransferTest.request("WIDE_"+checks));
            TransferJournal dynamic=new TransferJournal(null);dynamic.streamReady();dynamic.reserve(normal);dynamic.accepted((String)normal.get("COMMANDID"));
            dynamic.observe(TransferService.map("commandId",normal.get("COMMANDID"),"carrierId",normal.get("CARRIERID"),"responseId","first","robotId",vehicle,
                "instanceId","8888","status","MOVE_BEGIN","success",true,"resultCode","0","manualCompleteFlag",0,"sequence",1,
                "nextNode",WorkPorts.node((String)normal.get("SOURCEPORT"))));
            Map<String,Object> result=dynamic.stage(normal,"assigned");
            check(Boolean.TRUE.equals(result.get("success")),"String/wide numeric vehicle can report lifecycle");
            check(vehicle.equals(result.get("robotId")),"Actual Fleet ID preserved without integer parsing");
        }
        System.out.println("PASS: "+checks+" fresh-loader journal-before-catalog startup, deferred replay, captured completion and duplicate checks; no Fleet calls.");
    }
}
