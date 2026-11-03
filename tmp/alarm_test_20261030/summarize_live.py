import json,re,sys
from pathlib import Path
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent
source=Path.home()/'AppData/Local/ServoStar/KukaVirtualMCS/logs/messages_20261030.log'
parts=re.split(r'(?m)(?=^\[\d\d:\d\d:\d\d\.\d+\])',source.read_text(encoding='utf-8-sig'))
ids=['C3E5B29C7CD441E2A51835BA93038D95','E4AD2CC08E3A42D4BD941F8F447F433A']
starts=[next(i for i,x in enumerate(parts) if 'S2F49 W TRANSFER' in x and ident in x) for ident in ids]
summaries=[]
for n,ident in enumerate(ids):
    segment=parts[starts[n]:starts[n+1] if n+1<len(ids) else len(parts)]
    alarms=[x for x in segment if '\nS5F1 W' in x]
    acks={re.search(r'SB=(0x[0-9A-F]+)',x).group(1):x for x in segment if '\nS5F2 ' in x}
    ack_ok=all(re.search(r'SB=(0x[0-9A-F]+)',x).group(1) in acks
        and re.search(r'<B\s+\[1\]\s+0>',acks[re.search(r'SB=(0x[0-9A-F]+)',x).group(1)]) for x in alarms)
    events=[]
    for x in segment:
        if '\nS6F11 W' in x:
            m=re.search(r'<U4\s+\[1\]\s+\d+>\s*<U2\s+\[1\]\s+(\d+)>',x)
            if m:events.append((int(m.group(1)),x))
    terminal=[x for ceid,x in events if ceid==207 and ident in x]
    unit={str(c):sum(ceid==c for ceid,_ in events) for c in [702,701]}
    event_acks={re.search(r'SB=(0x[0-9A-F]+)',x).group(1):x for x in segment if '\nS6F12 ' in x}
    checked_events=[x for c,x in events if c in [702,701,207]]
    event_ack_ok=all(re.search(r'SB=(0x[0-9A-F]+)',x).group(1) in event_acks
        and re.search(r'<B\s+\[1\]\s+0>',event_acks[re.search(r'SB=(0x[0-9A-F]+)',x).group(1)]) for x in checked_events)
    times=[re.search(r'^\[([^]]+)\]',x).group(1) for x in alarms]
    summary=dict(commandId=ident,workflowId=6901+n,
        setCount=sum(bool(re.search(r'<B\s+\[1\]\s+134>',x)) for x in alarms),
        clearCount=sum(bool(re.search(r'<B\s+\[1\]\s+6>',x)) for x in alarms),
        alids=sorted(set(int(v) for x in alarms for v in re.findall(r'<U4\s+\[1\]\s+(\d+)>',x))),
        alarmTimes=times,alarmAcksZero=ack_ok,unitAndCompletionAcksZero=event_ack_ok,unitEventCounts=unit,
        normalCompletionCount=len(terminal),normalCompletionResultZero=bool(terminal) and all('<U2 [1] 0>' in x for x in terminal),
        transferRequestCount=sum('S2F49 W TRANSFER' in x for x in segment))
    summary['passed']=summary['setCount']==summary['clearCount']==1 and summary['alids']==[1200103] and ack_ok and event_ack_ok \
        and unit==({'702':0,'701':0} if n==0 else {'702':1,'701':1}) \
        and summary['normalCompletionCount']==1 and summary['normalCompletionResultZero'] and summary['transferRequestCount']==1
    summaries.append(summary)
    (root/f'live_1.2.3_workflow_{6901+n}_messages.log').write_text(''.join(segment),encoding='utf-8')
(root/'live_1.2.3_summary.json').write_text(json.dumps(summaries,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summaries,ensure_ascii=False,indent=2))
