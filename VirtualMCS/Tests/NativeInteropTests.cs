using KukaMcs.Core;

static class NativeInteropTests
{
    public static async Task Run(int port)
    {
        var folder=Path.Combine(Path.GetTempPath(),"KukaNativeInterop_"+Guid.NewGuid().ToString("N"));
        var jobs=new JobStore(Path.Combine(folder,"jobs.jsonl"));
        var cargo = new CargoStore(Path.Combine(folder,"cargo.jsonl"));
        await using var client=new McsClient(jobs, cargo);
        CargoSnapshot? inventory = null; client.InventoryReceived += snapshot => inventory = snapshot;
        var logs=new System.Collections.Concurrent.ConcurrentQueue<LogEntry>();
        client.Logged+=logs.Enqueue;
        async Task Wait(Func<bool> condition,string name,int seconds=10){
            var end=DateTime.UtcNow.AddSeconds(seconds);
            while(!condition() && DateTime.UtcNow<end)await Task.Delay(50);
            if(!condition())throw new Exception("Native interop failed: "+name+" / "+string.Join(" | ",logs.TakeLast(5).Select(x=>x.Text)));
        }
        await client.ConnectAsync(new("127.0.0.1",port,3000,true,5));
        await Wait(()=>client.Communicating,"Java passive -> .NET Active S1F13/14");
        var incoming = new CargoRequest("IN_WIRE", "CARRIER_IN", "SIM_CARGO_004", "PORT_84", "SIM_TRAY_001", "SIM_CARGO_004");
        await client.SendCargoAsync(incoming);
        await Wait(()=>cargo.Snapshot().Any(x=>x.Request.RequestId=="IN_WIRE" && x.Outcome=="CONFIRMED"),"New scanned cargo +9002");
        await client.SendCargoAsync(new("QUERY_WIRE", "CARRIER_QUERY"));
        await Wait(()=>inventory?.Items.Any(x=>x.CarrierId=="SIM_CARGO_004" && x.Port=="PORT_84")==true,"9001 multi-cargo snapshot");
        await client.SendCargoAsync(incoming with { RequestId="OUT_WIRE",Command="CARRIER_OUT" });
        await Wait(()=>cargo.Snapshot().Any(x=>x.Request.RequestId=="OUT_WIRE" && x.Outcome=="CONFIRMED"),"Off-map retained cargo +9002");
        await client.SendCargoAsync(new("SCAN_WIRE", "CARRIER_SCAN_TEST", "SIM_CARGO_001", "PORT_89", ScannedId:"WRONG"));
        await Wait(()=>logs.Any(x=>x.Direction=="RX" && x.Ceid==406),"406 scan mismatch +S6F12");
        await client.SendTransferAsync(new("TR_TEST_001","SIM_CARGO_001","PORT_89","PORT_97"));
        await Wait(()=>jobs.Snapshot().Any(x=>x.Phase==JobPhase.Completed),"14 normal reports + final confirmed207",30);
        var reports=logs.Where(x=>x.Direction=="RX" && x.Stream==6 && x.Function==11 && x.Ceid is not (9001 or 9002 or 406)).Select(x=>x.Ceid).ToArray();
        uint?[] expected=[208,604,601,211,602,301,603,605,601,606,302,607,610,207];
        if(!reports.SequenceEqual(expected))throw new Exception("Native interop CEID order: "+string.Join(",",reports));
        if(logs.Count(x=>x.Direction=="TX" && x.Stream==2 && x.Function==49)!=1)throw new Exception("Native interop repeated transfer");
        if(logs.Count(x=>x.Direction=="TX" && x.Stream==6 && x.Function==12)!=19)throw new Exception("Native interop missing inventory/scan/transport ACKs");
        Console.WriteLine("PASS: Java/.NET real HSMS: create, query, out, 406 mismatch, 19 acknowledged reports and one transport.");
    }
}
