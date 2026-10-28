package servostar.fleet.reporting;

import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class IbsemReportsTest {
    private static final String CMD="REFERENCE_ONLY_001", CARGO="SIM_CARGO_001";
    private static int checks;
    private static void check(boolean pass, String name) {
        checks++; if (!pass) throw new AssertionError(name);
    }
    private static void reject(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid body was accepted");
    }
    private static int number(Secs2 body, int... path) throws Exception {
        return body.get(path).getInt(0);
    }
    public static void main(String[] args) throws Exception {
        Path out=Paths.get(args[0]); Files.createDirectories(out);
        Secs2 progress=IbsemReports.transfer(1,211,CMD,CARGO,"PORT_89","PORT_97","PORT_89",0);
        Secs2 completed=IbsemReports.transfer(2,207,CMD,CARGO,"PORT_89","PORT_97","PORT_97",0);
        check(number(progress,1)==211 && number(completed,1)==207,"Event IDs");
        check(completed.get(0).secs2Item()==Secs2Item.UINT4,"DATAID is U4");
        check(completed.get(1).secs2Item()==Secs2Item.UINT2,"CEID is U2");
        check(completed.get(2,0,0).secs2Item()==Secs2Item.UINT2,"RPTID is U2");
        check(completed.get(2,0,1,0).secs2Item()==Secs2Item.ASCII,"CommandID is ASCII");
        check(completed.get(2,0,1,2).secs2Item()==Secs2Item.UINT2,"ResultCode is U2");
        check(completed.size()==3 && completed.get(2).size()==1,"S6F11 outer L counts");
        check(number(completed,2,0,0)==2 && completed.get(2,0,1).size()==3,"RPTID2 shape");
        check(completed.getAscii(2,0,1,0).equals(CMD),"Command correlation");
        check(completed.get(2,0,1,1).size()==1,"TransferCompleteInfo L1");
        check(completed.get(2,0,1,1,0).size()==2,"TransferCompleteInfo entry L2");
        check(completed.get(2,0,1,1,0,0).size()==3,"Carrier/source/destination L3");
        check(completed.getAscii(2,0,1,1,0,0,2).equals("PORT_97"),"Destination field");
        check(completed.getAscii(2,0,1,1,0,1).equals("PORT_97"),"Observed final location");
        check(progress.getAscii(2,0,1,1,0,1).equals("PORT_89"),"Pickup start does not imply installed");
        check(number(completed,2,0,1,2)==0,"Verified OK result field");
        for(int ceid:new int[]{601,602,603,604,605,606,607,610}) {
            Secs2 body=IbsemReports.vehicle(1,ceid,"1",CMD,"PORT_89",CARGO);
            check(number(body,2,0,0)==5 && body.get(2,0,1).size()==4,"RPTID5 "+ceid);
        }
        for(int ceid:new int[]{301,302}) {
            Secs2 body=IbsemReports.carrier(1,ceid,"1",CARGO,"1",CMD);
            check(number(body,2,0,0)==3 && body.get(2,0,1).size()==4,"RPTID3 "+ceid);
        }
        for(int result:new int[]{5,6,7,8}) {
            Secs2 body=IbsemReports.transfer(1,207,CMD,CARGO,"PORT_89","PORT_97","1",result);
            check(number(body,2,0,1,2)==result,"Explicit failure result "+result);
        }
        check((IbsemReports.alarm(true,6,900001,"TEST_ONLY_WARNING").get(0).getByte(0)&255)==0x86,"Alarm set bit");
        check((IbsemReports.alarm(false,6,900001,"TEST_ONLY_WARNING").get(0).getByte(0)&255)==6,"Alarm clear bit");
        check(number(IbsemReports.unitAlarm(1,true,"1",900001,"TEST_ONLY_WARNING"),1)==702,"Unit alarm set");
        check(number(IbsemReports.unitAlarm(1,false,"1",900001,"TEST_ONLY_WARNING"),1)==701,"Unit alarm clear");
        check(IbsemReports.acknowledge().size()==1 && IbsemReports.acknowledge().getByte(0)==0,"Binary ACK");
        reject(()->IbsemReports.transfer(1,207,CMD,CARGO,"PORT_89","PORT_97","PORT_97",99));
        reject(()->IbsemReports.transfer(1,211,CMD,CARGO,"PORT_89","PORT_97","PORT_89",5));
        reject(()->IbsemReports.transfer(1,207,CMD,CARGO,"PORT_89","PORT_97","",0));
        reject(()->IbsemReports.transfer(-1,207,CMD,CARGO,"PORT_89","PORT_97","PORT_97",0));
        reject(()->IbsemReports.transfer(0x100000000L,207,CMD,CARGO,"PORT_89","PORT_97","PORT_97",0));
        reject(()->IbsemReports.transfer(1,207,"한글",CARGO,"PORT_89","PORT_97","PORT_97",0));
        reject(()->IbsemReports.alarm(true,128,1,"INVALID"));
        reject(()->IbsemReports.alarm(true,6,1,"INVALID/ALARM"));
        reject(()->IbsemReports.alarm(true,6,-1,"INVALID"));
        Files.write(out.resolve("progress_211.typed.json"),progress.toJson().getBytes(StandardCharsets.UTF_8));
        Files.write(out.resolve("completed_207.typed.json"),completed.toJson().getBytes(StandardCharsets.UTF_8));
        System.out.println("PASS: "+checks+" body checks using extracted Fleet Secs2 library. No network calls or live changes.");
    }
}
