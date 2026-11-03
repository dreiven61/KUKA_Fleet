"""Read-only ACS startup pattern: issue SV25,58,23 without awaiting replies."""
import argparse,json,pathlib,statistics,time
from reconnect_probe import Peer,L,U2
from live_reconnect_test import plain

def main():
    p=argparse.ArgumentParser();p.add_argument('--output',required=True);p.add_argument('--rounds',type=int,default=100);o=p.parse_args()
    peer=Peer(o.output);latency=[];results={}
    try:
        peer.connect()
        for _ in range(o.rounds):
            requests={}
            for svid in (25,58,23):
                start=time.monotonic();sid=peer.send(1,3,L(U2(svid)),wait=True);requests[sid]=(svid,start)
            while requests:
                message=peer.receive();sid=message['system']
                if sid not in requests:peer.handle(message);continue
                svid,start=requests.pop(sid)
                if (message['stream'],message['function'])!=(1,4):raise AssertionError('S1F4 required')
                value=plain(message['body'])
                if len(value)!=1 or len(value[0])!={25:3,58:10,23:0}[svid]:raise AssertionError('Unexpected idle snapshot')
                latency.append(round((time.monotonic()-start)*1000,3))
        results=dict(passed=True,pattern=[25,58,23],rounds=o.rounds,replies=len(latency),p50Ms=statistics.median(latency),p95Ms=sorted(latency)[int(.95*(len(latency)-1))],maxMs=max(latency))
        print(json.dumps(results),flush=True)
    finally:
        peer.close();pathlib.Path(o.output).with_suffix('.results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')

if __name__=='__main__':main()
