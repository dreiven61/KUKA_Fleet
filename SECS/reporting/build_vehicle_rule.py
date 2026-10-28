"""Separate Reports05 import; existing/new API headers can be filled locally. No network."""
import argparse
import copy
import json
import re
import uuid
from build_carrier_rule import HERE, group, headers
from build_reporting_rule import scalar


def build(with_token=False):
    source=json.loads((HERE/'ecs_candidate/SecsTransferReports04_IMPORT_CANDIDATE.json').read_text(encoding='utf-8-sig'))
    text=json.dumps(source,ensure_ascii=False)
    rule=source['ruleChains'][0]
    for old in [rule['id']['id']]+[p['id']['id'] for p in rule['parametersDefine']]:text=text.replace(old,str(uuid.uuid4()))
    doc=json.loads(text)
    rule,meta=doc['ruleChains'][0],doc['metadata'][0]
    graph=meta['graphInfo']
    rule.update(name='SecsTransferReports05_Ready' if with_token else 'SecsTransferReports05',root=False,
        firstRuleNodeId=None,createdTime=0,updatedTime=0)
    nodes={n['name']:n for n in meta['nodes']}
    visuals={v['id']:v for v in graph['nodes']}

    def add(template,name):
        n=copy.deepcopy(nodes[template]);n.pop('id',None);n['name']=name
        v=copy.deepcopy(visuals[n['additionalInfo']['graphNodeId']])
        gid='servostar-vehicle-'+name
        n['additionalInfo']['graphNodeId']=v['id']=gid
        v['label']=name;v['data'].pop('editId',None)
        nodes[name]=n;visuals[gid]=v;meta['nodes'].append(n);graph['nodes'].append(v)
        return n

    gates=[('WaitVehicleAssigned','assigned'),('WaitSourceArrival','arrived-source'),
        ('WaitSourceDeparture','departed-source'),('WaitDestinationArrival','arrived-dest'),('WaitVehicleUnassigned','unassigned')]
    for name,route in gates:
        n=add('WaitPickupStarted',name)
        n['configuration'].update(maxRetryTimes=120,retryPeriod=5)
        n['configuration']['externalSystemApi'].update(apiName='Fleet_Transfer_'+route,
            apiUrl='http://192.168.0.45:10870/interfaces/api/mes-transfer/'+route)
    reports=[(604,'ReportVehicleAssigned604','SOURCEPORT'),(601,'ReportSourceArrived601','SOURCEPORT'),
        (605,'ReportSourceDeparted605','SOURCEPORT'),(601,'ReportDestinationArrived601','DESTPORT'),
        (610,'ReportVehicleUnassigned610','DESTPORT')]
    for ceid,name,port in reports:
        n=add('ReportAcquireStarted602',name)
        conf=n['configuration'];conf['eventName']='TransferInitiated'
        conf['requestBody']=group(scalar('U4','DATAID','0'),scalar('U2','CEID',str(ceid)),
            group(group(scalar('U2','RPTID','5'),group(scalar('A','robotId','1'),scalar('A','COMMANDID','${COMMANDID}'),
                group(scalar('A',port,'${'+port+'}')),group(scalar('A','CARRIERID','${CARRIERID}'))))))
        conf['responseBody']=scalar('BINARY','ACKC6','0')
    path=['Start Node','ReceiveTransfer','InitEcsContext','ValidateTransfer','QueryFleet_API_Check','ReportTransferInitiated',
        'WaitVehicleAssigned','ReportVehicleAssigned604','WaitSourceArrival','ReportSourceArrived601',
        'WaitPickupStarted','ReportTransferring211','ReportAcquireStarted602','WaitPickupCompleted',
        'ReportCarrierInstalled301','ReportAcquireCompleted603','WaitSourceDeparture','ReportSourceDeparted605',
        'WaitDestinationArrival','ReportDestinationArrived601','WaitDropStarted','ReportDepositStarted606',
        'WaitTransferCompleted','ReportCarrierRemoved302','ReportDepositCompleted607',
        'WaitVehicleUnassigned','ReportVehicleUnassigned610','ReportTransferCompleted207']
    meta['connections'],graph['edges']=[],[]
    for i,name in enumerate(path):
        n=nodes[name];v=visuals[n['additionalInfo']['graphNodeId']]
        v.update(x=405,y=63+i*150);v['data']['detailPanelInfo']=copy.deepcopy(n['configuration'])
        if i:
            label=['Inbound request','Success','Success','True'][i-1] if i<=4 else 'Success'
            prev=nodes[path[i-1]]
            meta['connections'].append(dict(fromIndex=meta['nodes'].index(prev),toIndex=meta['nodes'].index(n),type=label))
            start,end=prev['additionalInfo']['graphNodeId'],n['additionalInfo']['graphNodeId']
            graph['edges'].append(dict(id='servostar-vehicle-edge-'+str(i),source=start,target=end,sourceId=start,targetId=end,
                start=dict(x=0,y=17),end=dict(x=0,y=-17),shape='customEdge',endArrow=True,color='#dfdfdf',type='edge',
                label=label,animate=False,data=dict(detailPanelInfo=dict(type=label))))
    meta['firstNodeIndex']=meta['nodes'].index(nodes['Start Node'])
    graph['graphData']['name']=rule['name'];graph['graphData']['parametersDefine']=copy.deepcopy(rule['parametersDefine'])
    rule['graphInfo']=copy.deepcopy(graph)
    token='PASTE_EXISTING_FLEET_TOKEN'
    if with_token:
        saved=HERE.parent/'native_transfer/manual_api/SecsTransferTest (4).json'
        candidates={str(h.get('value','')).strip() for h in headers(json.loads(saved.read_text(encoding='utf-8-sig')))}
        candidates={t for t in candidates if re.fullmatch('[0-9a-fA-F]{64}',t)}
        if len(candidates)!=1:raise ValueError('Expected one existing token; no output written')
        token=next(iter(candidates))
    all_headers=list(headers(doc));assert len(all_headers)==30
    for h in all_headers:h['value']=token
    assert len(meta['nodes'])==28 and len(meta['connections'])==27
    for name in ('WaitVehicleAssigned','WaitSourceArrival','WaitSourceDeparture','WaitDestinationArrival','WaitVehicleUnassigned'):
        assert path.index(name)+1==path.index(names_report(name))
    urls=[n['configuration']['externalSystemApi']['apiUrl'] for n in meta['nodes'] if n['type'].endswith('TbGrpcApiCallNode')]
    assert len(urls)==10 and sum(u.endswith('/submit') for u in urls)==1
    assert nodes['QueryFleet_API_Check']['configuration']['maxRetryTimes']==0
    # Existing transport, validation, request bodies and response mappings are preserved.
    for old in source['metadata'][0]['nodes']:
        expected=copy.deepcopy(old['configuration'])
        for h in headers(expected):h['value']=token
        assert nodes[old['name']]['configuration']==expected
    encoded=json.dumps(doc,ensure_ascii=False,indent=2)
    if not with_token:assert not re.search(r'(?i)\b[0-9a-f]{64}\b',encoded)
    out=HERE/'ecs_candidate'/('SecsTransferReports05_Ready_LOCAL_WITH_TOKEN.json' if with_token else 'SecsTransferReports05_IMPORT_CANDIDATE.json')
    out.write_text(encoded,encoding='utf-8')
    print('PASS: Reports05 28 nodes / 27 edges, 10 authenticated API nodes, one submit; no upload.')
    print('Created: '+str(out))


def names_report(gate):
    return {'WaitVehicleAssigned':'ReportVehicleAssigned604','WaitSourceArrival':'ReportSourceArrived601',
        'WaitSourceDeparture':'ReportSourceDeparted605','WaitDestinationArrival':'ReportDestinationArrived601',
        'WaitVehicleUnassigned':'ReportVehicleUnassigned610'}[gate]


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--with-existing-token',action='store_true')
    build(parser.parse_args().with_existing_token)
