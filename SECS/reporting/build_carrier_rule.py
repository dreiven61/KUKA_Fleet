"""Reports04 adds observed carrier transitions; no live requests or uploads."""
import argparse
import copy
import json
import re
import uuid
from pathlib import Path
from build_reporting_rule import scalar

HERE = Path(__file__).resolve().parent


def group(*children):
    value = scalar('L', '', '')
    value['children'] = list(children)
    return value


def headers(x):
    if isinstance(x, dict):
        if str(x.get('key', x.get('name', ''))).lower() == 'authorization':
            yield x
        for v in x.values():
            for found in headers(v): yield found
    elif isinstance(x, list):
        for v in x:
            for found in headers(v): yield found


def build(with_token=False):
    original = json.loads((HERE/'ecs_candidate/SecsTransferReports03_IMPORT_CANDIDATE.json').read_text(encoding='utf-8-sig'))
    text = json.dumps(original, ensure_ascii=False)
    rule = original['ruleChains'][0]
    for old in [rule['id']['id']] + [p['id']['id'] for p in rule['parametersDefine']]:
        text = text.replace(old, str(uuid.uuid4()))
    doc = json.loads(text)
    rule, meta = doc['ruleChains'][0], doc['metadata'][0]
    graph = meta['graphInfo']
    rule.update(name='SecsTransferReports04_Ready' if with_token else 'SecsTransferReports04', root=False,
                firstRuleNodeId=None, createdTime=0, updatedTime=0)
    nodes = {n['name']: n for n in meta['nodes']}
    visuals = {v['id']: v for v in graph['nodes']}
    for ceid, name, location in [(301, 'ReportCarrierInstalled301', '1'),
                                  (302, 'ReportCarrierRemoved302', '${DESTPORT}')]:
        template = nodes['ReportTransferInitiated']
        n = copy.deepcopy(template)
        n.pop('id', None)
        n['name'] = name
        v = copy.deepcopy(visuals[n['additionalInfo']['graphNodeId']])
        gid = 'servostar-carrier-' + name
        n['additionalInfo']['graphNodeId'] = v['id'] = gid
        v['label'] = name
        v['data'].pop('editId', None)
        conf = n['configuration']
        conf['eventName'] = 'TransferInitiated'
        conf['requestBody'] = group(scalar('U4','DATAID','0'),scalar('U2','CEID',str(ceid)),
            group(group(scalar('U2','RPTID','3'),group(scalar('A','robotId','1'),
                scalar('A','CARRIERID','${CARRIERID}'),scalar('A','CARRIERLOC',location),
                scalar('A','COMMANDID','${COMMANDID}')))))
        conf['responseBody'] = scalar('BINARY','ACKC6','0')
        nodes[name] = n
        visuals[gid] = v
        meta['nodes'].append(n)
        graph['nodes'].append(v)
    path = ['Start Node','ReceiveTransfer','InitEcsContext','ValidateTransfer','QueryFleet_API_Check',
        'ReportTransferInitiated','WaitPickupStarted','ReportTransferring211','ReportAcquireStarted602',
        'WaitPickupCompleted','ReportCarrierInstalled301','ReportAcquireCompleted603',
        'WaitDropStarted','ReportDepositStarted606','WaitTransferCompleted','ReportCarrierRemoved302',
        'ReportDepositCompleted607','ReportTransferCompleted207']
    meta['connections'], graph['edges'] = [], []
    for i, name in enumerate(path):
        n = nodes[name]
        v = visuals[n['additionalInfo']['graphNodeId']]
        v.update(x=405,y=63+i*150)
        v['data']['detailPanelInfo'] = copy.deepcopy(n['configuration'])
        if i:
            label = ['Inbound request','Success','Success','True'][i-1] if i <= 4 else 'Success'
            prev = nodes[path[i-1]]
            meta['connections'].append(dict(fromIndex=meta['nodes'].index(prev),toIndex=meta['nodes'].index(n),type=label))
            source, target = prev['additionalInfo']['graphNodeId'], n['additionalInfo']['graphNodeId']
            graph['edges'].append(dict(id='servostar-carrier-edge-'+str(i),source=source,target=target,
                sourceId=source,targetId=target,start=dict(x=0,y=17),end=dict(x=0,y=-17),shape='customEdge',
                endArrow=True,color='#dfdfdf',type='edge',label=label,animate=False,data=dict(detailPanelInfo=dict(type=label))))
    meta['firstNodeIndex'] = meta['nodes'].index(nodes['Start Node'])
    graph['graphData']['name'] = rule['name']
    graph['graphData']['parametersDefine'] = copy.deepcopy(rule['parametersDefine'])
    rule['graphInfo'] = copy.deepcopy(graph)
    token = 'PASTE_EXISTING_FLEET_TOKEN'
    if with_token:
        existing = HERE.parent/'native_transfer/manual_api/SecsTransferTest (4).json'
        values = {str(h.get('value','')).strip() for h in headers(json.loads(existing.read_text(encoding='utf-8-sig')))}
        values = {v for v in values if re.fullmatch('[0-9a-fA-F]{64}',v)}
        if len(values) != 1: raise ValueError('Expected exactly one existing token; no output written')
        token = next(iter(values))
    all_headers = list(headers(doc))
    assert len(all_headers) == 15
    for h in all_headers: h['value'] = token
    # Existing gates, API URLs, request/response mappings and retry policy remain unchanged.
    for old in original['metadata'][0]['nodes']:
        if old['type'].endswith('TbGrpcApiCallNode'):
            expected = copy.deepcopy(old['configuration'])
            for h in headers(expected): h['value'] = token
            assert nodes[old['name']]['configuration'] == expected
    assert len(meta['nodes']) == 18 and len(meta['connections']) == 17
    assert path.index('WaitPickupCompleted')+1 == path.index('ReportCarrierInstalled301')
    assert path.index('WaitTransferCompleted')+1 == path.index('ReportCarrierRemoved302')
    encoded = json.dumps(doc,ensure_ascii=False,indent=2)
    if not with_token: assert not re.search(r'(?i)\b[0-9a-f]{64}\b',encoded)
    suffix = 'SecsTransferReports04_Ready_LOCAL_WITH_TOKEN.json' if with_token else 'SecsTransferReports04_IMPORT_CANDIDATE.json'
    out = HERE/'ecs_candidate'/suffix
    out.write_text(encoded,encoding='utf-8')
    print('PASS: Reports04 18 nodes / 17 edges; 301/302 after observed gates; 5 unchanged API nodes; no upload.')
    print('Created: '+str(out))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--with-existing-token', action='store_true')
    build(parser.parse_args().with_existing_token)
