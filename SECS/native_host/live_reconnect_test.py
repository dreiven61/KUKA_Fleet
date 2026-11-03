"""SIM01-only integration: fresh S1F3, disconnect, ACK faults, durable restart.

One submission per saved command identity. No transport replay or journal edits.
The restart checkpoint requires the operator to stop/start the installed plugin
and create restart_done; it never restarts the whole Fleet stack.
"""
import argparse,json,pathlib,select,time,urllib.request,uuid,statistics
from reconnect_probe import Peer,L,A,U2,U4

def plain(x):
    if x is None:return None
    if x[0]=='L':return [plain(v) for v in x[1]]
    if x[0]=='A':return x[1]
    return x[1][0] if len(x[1])==1 else x[1]

def transfer(command,source,dest):
    return L(U4(1),A(''),A('TRANSFER'),L(
        L(A('COMMANDINFO'),L(L(A('COMMANDID'),A(command)),L(A('PRIORITY'),U2(1)),L(A('REPLACE'),U2(0)))),
        L(A('TRANSFERINFO'),L(L(A('CARRIERID'),A('SIM_CARGO_001')),L(A('SOURCEPORT'),A(source)),L(A('DESTPORT'),A(dest))))))

def main():
    p=argparse.ArgumentParser();p.add_argument('--execute-sim',action='store_true');p.add_argument('--config',required=True);p.add_argument('--output',required=True)
    o=p.parse_args()
    if not o.execute_sim:p.error('--execute-sim required')
    out=pathlib.Path(o.output);out.mkdir(parents=True,exist_ok=True)
    if (out/'commands.json').exists():raise RuntimeError('Existing attempt retained; review it rather than replay')
    token=json.loads(pathlib.Path(o.config).read_text(encoding='utf-8-sig'))['authorization']
    peer=Peer(out/'wire.jsonl');latencies=[];results={};events=[]
    def record(kind,value):peer.record(kind,value)
    def progress(label,**v):print(json.dumps(dict(stage=label,**v)),flush=True);record('PROGRESS',dict(stage=label,**v))
    def api(path,body={}):
        req=urllib.request.Request('http://192.168.0.45:10870/interfaces/api/'+path,json.dumps(body).encode(),{'Authorization':token,'Content-Type':'application/json'},method='POST')
        with urllib.request.urlopen(req,timeout=8) as r:reply=json.load(r)
        record('API '+path,reply);return reply
    def check(condition,message):
        if not condition:raise AssertionError(message)
    def query(ids=(23,25,58,21)):
        r=peer.query(ids);check((r['stream'],r['function'])==(1,4),'No S1F4: '+str(r))
        latencies.append(r['latencyMs']);v=plain(r['body']);check(len(v)==len(ids) or not ids,'SVID count/order');return dict(zip(ids or (3,17,21,23,25,46,58),v))
    def commands(snapshot):return [r[0][0] for r in snapshot[23]]
    def pump(seconds=.05):
        if peer.socket and select.select([peer.socket],[],[],seconds)[0]:peer.handle(peer.receive())
    def pending(dataid=None):
        rows=api('mes-secs/status')['state']['reports'];return rows if dataid is None else next((r for r in rows if r['dataId']==dataid),None)
    def until(predicate,timeout,label,action=None):
        deadline=time.monotonic()+timeout;nextlog=time.monotonic()+20
        while time.monotonic()<deadline:
            if action:action()
            if predicate():return
            if time.monotonic()>nextlog:progress('waiting',forStage=label);nextlog=time.monotonic()+20
            pump(.1)
        raise TimeoutError(label)
    robots=api('amr/robotQuery')['data'];status=api('mes-secs/status');cargo=api('amr/containerQueryAll')['data']
    check(status['version']=='1.3.0' and status['state']['healthy'] and not status['state']['reports'],'Healthy 1.3.0 and drained outbox required')
    check(len(robots)==3 and all(r.get('buildingCode')=='SIM01' and r.get('robotType')=='KMP1500I' and not r.get('missionCode') for r in robots),'Idle existing SIM01 fleet required')
    check(not any(j['phase'] not in ('COMPLETED','FAILED','ABORTED','CANCELED','REJECTED') for j in status['state']['jobs']),'Unresolved native job')
    selected=next(c for c in cargo if c['containerCode']=='SIM_CARGO_001')
    check(str(selected['isCarry'])=='0' and selected['nodeCode'] in ('lay01-dist1-90','SECS_ST_90'),'Carrier must start PORT_90')
    check(not any(c['nodeCode'] in ('lay01-dist1-97','SECS_ST_97') and str(c['inMapStatus'])=='1' for c in cargo),'Destination occupied')
    identities=dict(outbound=uuid.uuid4().hex.upper(),sameCarrierRejected=uuid.uuid4().hex.upper(),returning=uuid.uuid4().hex.upper())
    (out/'commands.json').write_text(json.dumps(identities,indent=2),encoding='utf-8')
    fault=dict(drop604=None,reject207=None,dropReturn607=None)
    def ack(message):
        value=plain(message['body'])
        if message['stream']!=6:return 0
        dataid,ceid=value[:2];events.append(dict(dataId=dataid,ceid=ceid,body=value))
        if ceid==604 and fault['drop604'] is None:fault['drop604']=dataid;record('FAULT_DROP_ACK_604',dataid);return None
        if ceid==207 and fault['reject207'] is None:fault['reject207']=dataid;record('FAULT_REJECT_ACK_207',dataid);return 1
        if ceid==607 and results.get('restartRecovered') and fault['dropReturn607'] is None:fault['dropReturn607']=dataid;record('FAULT_DROP_ACK_607',dataid);return None
        return 0
    peer.ack_policy=ack
    try:
        peer.connect()
        for n in range(100):
            ids=[(23,),(25,),(58,),(23,25,58,21)][n%4];s=query(ids)
            if 23 in s:check(not s[23],'Idle SV23 must be empty')
            if 25 in s:check(len(s[25])==3,'Three AMRs')
            if 58 in s:check(len(s[58])==10,'Ten managed ports')
        allvalues=query(());check(len(allvalues)==7,'All SVIDs');check(query((58,999,25))[999]==[],'Unknown SVID empty slot')
        progress('idle-continuous-query-pass',count=len(latencies),maxMs=max(latencies))
        cmd=identities['outbound'];request=transfer(cmd,'PORT_90','PORT_97');record('SOLE_SUBMISSION',request)
        reply=peer.primary(2,49,request);check(plain(reply['body'])[0]==4,'TRANSFER not accepted; no replay')
        progress('outbound-accepted',commandId=cmd)
        until(lambda:fault['drop604'] is not None,60,'assigned report',lambda:check(cmd in commands(query()),'Accepted job disappeared'))
        # Lose an ACK and disconnect the MCS while Fleet continues its sole job.
        peer.close();time.sleep(2);peer.connect()
        check(cmd in commands(query()),'Disconnected job missing on reconnect')
        progress('mcs-reconnected',lostAckDataId=fault['drop604'])
        until(lambda:fault['reject207'] is not None,180,'outbound final report',lambda:check(cmd in commands(query()),'Job disappeared before final ACK'))
        did=fault['reject207'];until(lambda:bool(pending(did) and pending(did)['state']=='BLOCKED'),10,'ACK rejection persisted')
        for _ in range(40):check(cmd in commands(query()),'UnACKed terminal command missing')
        duplicate=peer.primary(2,49,request);check(plain(duplicate['body'])[0]==5,'Original COMMANDID must be duplicate')
        rejected=peer.primary(2,49,transfer(identities['sameCarrierRejected'],'PORT_97','PORT_90'));check(plain(rejected['body'])[0]==2,'New ID same carrier must be blocked before ACK')
        rows=api('amr/jobQuery',dict(jobCode=cmd,limit=1000))['data'];exact=[j for j in rows if j['jobCode']==cmd]
        check(len(exact)==1,'Exactly one Fleet transport required')
        check(cmd in commands(query()),'Rejection must not remove pending transfer')
        results['disconnectAndReject']=dict(commandId=cmd,workflowId=exact[0]['workflowId'],reportDataId=did,originalHcack=5,newIdHcack=2)
        # Persisted BLOCKED report deliberately remains blocked across restart.
        peer.close();(out/'restart_ready.json').write_text(json.dumps(dict(commandId=cmd,dataId=did)),encoding='utf-8')
        progress('restart-ready',commandId=cmd,dataId=did)
        deadline=time.monotonic()+600
        while not (out/'restart_done').exists():
            if time.monotonic()>deadline:raise TimeoutError('Plugin restart checkpoint')
            time.sleep(1)
        peer.connect();check(cmd in commands(query()),'Pending command lost across plugin restart')
        check(pending(did)['state']=='BLOCKED','Rejected ACK must remain blocked after restart')
        check(api('mes-secs/retry-report',dict(dataId=did))['success'],'Explicit report retry failed')
        until(lambda:pending(did) is None,20,'Same DATAID report ACK',lambda:query())
        until(lambda:cmd not in commands(query()),20,'ACKed job removed from SV23')
        check(sum(e['dataId']==did for e in events)>=2,'Report not retransmitted with same DATAID')
        results['restartRecovered']=True;progress('restart-and-explicit-retry-pass',dataId=did)
        cmd=identities['returning'];request=transfer(cmd,'PORT_97','PORT_90');record('SOLE_SUBMISSION',request)
        reply=peer.primary(2,49,request);check(plain(reply['body'])[0]==4,'Return not accepted; no replay')
        progress('return-accepted',commandId=cmd)
        until(lambda:fault['dropReturn607'] is not None,180,'Return drop-completed report',lambda:query())
        did=fault['dropReturn607'];progress('ack-lost-connected',dataId=did)
        # Keep the connection and continue querying while the original report
        # waits for production T3=120 s. ACK the same report's next delivery.
        until(lambda:sum(e['dataId']==did for e in events)>=2,155,'Lost ACK retransmission',lambda:query())
        until(lambda:any(e['ceid']==207 and e['body'][2][0][1][0]==cmd for e in events),30,'Return final207',lambda:query())
        until(lambda:not pending() and not query()[23],20,'Outbox and transfer list drained')
        rows=api('amr/jobQuery',dict(jobCode=cmd,limit=1000))['data'];exact=[j for j in rows if j['jobCode']==cmd];check(len(exact)==1,'Return duplicated in Fleet')
        results['lostAck']=dict(dataId=did,deliveries=sum(e['dataId']==did for e in events),workflowId=exact[0]['workflowId'])
        final=api('mes-secs/status');cargo=api('amr/containerQueryAll')['data'];robots=api('amr/robotQuery')['data']
        check(not final['state']['reports'] and not final['state']['activeAlarms'],'Final reports/alarms must be clear')
        check(all(not r.get('missionCode') and r['status']==3 and r['occupyStatus']==0 for r in robots),'Final vehicles not idle')
        selected=next(c for c in cargo if c['containerCode']=='SIM_CARGO_001');check(str(selected['isCarry'])=='0' and selected['nodeCode'] in ('SECS_ST_90','lay01-dist1-90'),'Carrier not restored')
        results['queryLatency']=dict(count=len(latencies),p50Ms=statistics.median(latencies),p95Ms=sorted(latencies)[int(.95*(len(latencies)-1))],maxMs=max(latencies))
        results['pass']=True;progress('PASS',**results)
    except BaseException as ex:
        results.update(pass_=False,error=type(ex).__name__+': '+str(ex));progress('FAILED',**results);raise
    finally:
        peer.close();(out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':main()
