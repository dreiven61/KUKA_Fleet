using KukaMcs.Core;
using Secs4Net;
using static Secs4Net.Item;

static class ControlTests
{
    public static async Task Run(McsClient client, SecsGem equipment, JobStore jobs, CancellationToken token, Action<bool,string> check)
    {
        TransferRequest Request(string id) => new(id,"SIM_CARGO_001","PORT_89","PORT_98");
        SecsMessage Event(uint ceid, TransferRequest r) => new(6,11,true){SecsItem=L(U4(300),U2((ushort)ceid),L(L(U2(2),L(A(r.CommandId),L(L(L(A(r.CarrierId),A(r.SourcePort),A(r.DestPort)),A(r.SourcePort))),U2(0)))))};
        foreach(string command in new[]{"CANCEL","ABORT","PAUSE","RESUME"})
        {
            using var m=Protocol.Control(command,command is "CANCEL" or "ABORT"?"CONTROL_ID":null);
            check(m.S==2 && m.F==41 && m.ReplyExpected && m.SecsItem![0].GetString()==command,"S2F41 "+command);
            check(m.SecsItem![1].Count==(command is "CANCEL" or "ABORT"?1:0),"System/command scope "+command);
        }
        foreach(var (cmd,start,end,phase) in new[]{("CANCEL",206u,204u,JobPhase.Canceled),("ABORT",203u,201u,JobPhase.Aborted)})
        {
            var r=Request("CONTROL_"+cmd);jobs.Reserve(r);jobs.Acknowledge(r.CommandId,4);
            await client.SendControlAsync(cmd,r.CommandId);
            check(jobs.Snapshot().Single(j=>j.Request.CommandId==r.CommandId).IsPending,"S2F42 not terminal "+cmd);
            foreach(uint ceid in new[]{start,end}){using var m=Event(ceid,r);using var ack=await equipment.SendAsync(m,token);check(Protocol.Ack(ack,6,12)==0,"Control event ACK "+ceid);}
            check(jobs.Snapshot().Single(j=>j.Request.CommandId==r.CommandId).Phase==phase,"Observed terminal "+cmd);
            using var late=Event(207,r);jobs.Report(Protocol.ParseReport(late)!);
            check(jobs.Snapshot().Single(j=>j.Request.CommandId==r.CommandId).Phase==phase,"Late207 cannot overwrite "+cmd);
        }
        var paused=Request("PAUSE_RESUME");jobs.Reserve(paused);jobs.Acknowledge(paused.CommandId,4);
        jobs.Report(new(211,paused.CommandId,paused.CarrierId,paused.SourcePort,paused.DestPort,paused.SourcePort,0));
        await client.SendControlAsync("PAUSE");
        using(var m=Event(209,paused))using(var ack=await equipment.SendAsync(m,token))check(Protocol.Ack(ack,6,12)==0,"Paused ACK");
        check(jobs.Snapshot().Single(j=>j.Request.CommandId==paused.CommandId).Phase==JobPhase.Paused,"209 pauses job");
        await client.SendControlAsync("RESUME");
        using(var m=Event(210,paused))using(var ack=await equipment.SendAsync(m,token))check(Protocol.Ack(ack,6,12)==0,"Resume ACK");
        check(jobs.Snapshot().Single(j=>j.Request.CommandId==paused.CommandId).Phase==JobPhase.Transferring,"210 resumes job");
        foreach(uint ceid in new[]{202u,205u}){
            string cmd=ceid==202?"ABORT":"CANCEL";jobs.BeginControl(paused.CommandId,cmd);jobs.ControlAcknowledge(paused.CommandId,cmd,4);
            using var m=Event(ceid,paused);using var ack=await equipment.SendAsync(m,token);
            check(jobs.Snapshot().Single(j=>j.Request.CommandId==paused.CommandId).Phase==JobPhase.Transferring,"Failed control preserves job "+ceid);
        }
        jobs.Release(paused.CommandId);
        foreach(uint ceid in new[]{701u,702u}){
            using var m=new SecsMessage(6,11,true){SecsItem=L(U4(0),U2((ushort)ceid),L(L(U2(6),L(A("3"),U4(900001),A("FLEET_PATH_NOT_FOUND")))))};
            using var ack=await equipment.SendAsync(m,token);check(Protocol.Ack(ack,6,12)==0,"Unit alarm ACK "+ceid);
            check(Protocol.ParseUnitAlarm(m)?.AlarmId==900001,"ALID U4");
        }
        foreach(uint ceid in new[]{103u,105u,107u}){
            using var m=new SecsMessage(6,11,true){SecsItem=L(U4(0),U2((ushort)ceid),L())};using var ack=await equipment.SendAsync(m,token);check(Protocol.Ack(ack,6,12)==0,"TSC ACK "+ceid);
        }
        var duplicate=Request("DUPLICATE_REPLY");jobs.Reserve(duplicate);jobs.Acknowledge(duplicate.CommandId,5);
        check(jobs.Snapshot().Single(j=>j.Request.CommandId==duplicate.CommandId).Phase==JobPhase.Uncertain,"Duplicate reply does not declare existing job rejected");jobs.Release(duplicate.CommandId);
        var duplicateControl=Request("DUPLICATE_CONTROL");jobs.Reserve(duplicateControl);jobs.Acknowledge(duplicateControl.CommandId,4);
        jobs.BeginControl(duplicateControl.CommandId,"CANCEL");jobs.ControlAcknowledge(duplicateControl.CommandId,"CANCEL",5);
        check(jobs.Snapshot().Single(j=>j.Request.CommandId==duplicateControl.CommandId).Phase==JobPhase.Uncertain,"Duplicate control requires status review");jobs.Release(duplicateControl.CommandId);
    }
}
