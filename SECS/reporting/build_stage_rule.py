"""Build a separate Reports03 import candidate. No network or stored credentials."""
import copy
import json
import re
import uuid
from pathlib import Path
from build_reporting_rule import scalar

HERE = Path(__file__).resolve().parent


def main():
    doc = json.loads((HERE / 'ecs_candidate/SecsTransferReports02_SAVED_REDACTED.json').read_text(encoding='utf-8-sig'))
    rule, meta = doc['ruleChains'][0], doc['metadata'][0]
    graph = meta['graphInfo']
    identity = str(uuid.uuid4())
    rule['id']['id'] = meta['ruleChainId']['id'] = identity
    rule.update(name='SecsTransferReports03', root=False, firstRuleNodeId=None, createdTime=0, updatedTime=0)
    for p in rule['parametersDefine']:
        p['id']['id'] = str(uuid.uuid4())
        p['ruleChainId']['id'] = identity
    nodes = {n['name']: n for n in meta['nodes']}
    visuals = {v['id']: v for v in graph['nodes']}

    def add(template, name):
        n = copy.deepcopy(nodes[template])
        v = copy.deepcopy(visuals[n['additionalInfo']['graphNodeId']])
        n.pop('id', None)
        n['name'] = name
        gid = 'servostar-stage-' + name
        n['additionalInfo']['graphNodeId'] = v['id'] = gid
        v['label'] = name
        v['data'].pop('editId', None)
        nodes[name] = n
        visuals[gid] = v
        meta['nodes'].append(n)
        graph['nodes'].append(v)
        return n

    for name, route in [('WaitPickupCompleted', 'pickup-completed'), ('WaitDropStarted', 'drop-started')]:
        n = add('WaitPickupStarted', name)
        conf = n['configuration']
        conf.update(maxRetryTimes=120, retryPeriod=5)
        conf['externalSystemApi'].update(apiName='Fleet_Transfer_' + route,
            apiUrl='http://192.168.0.45:10870/interfaces/api/mes-transfer/' + route)

    def group(*children):
        result = scalar('L', '', '')
        result['children'] = list(children)
        return result

    for ceid, name, port in [(602, 'ReportAcquireStarted602', 'SOURCEPORT'),
                            (603, 'ReportAcquireCompleted603', 'SOURCEPORT'),
                            (606, 'ReportDepositStarted606', 'DESTPORT'),
                            (607, 'ReportDepositCompleted607', 'DESTPORT')]:
        n = add('ReportTransferInitiated', name)
        conf = n['configuration']
        # Vendor enum branch quirk: preserve configured CEID/RPTID, verified by probe.
        conf['eventName'] = 'TransferInitiated'
        conf['requestBody'] = group(scalar('U4', 'DATAID', '0'), scalar('U2', 'CEID', str(ceid)),
            group(group(scalar('U2', 'RPTID', '5'), group(scalar('A', 'robotId', '1'),
                scalar('A', 'COMMANDID', '${COMMANDID}'), group(scalar('A', port, '${'+port+'}')),
                group(scalar('A', 'CARRIERID', '${CARRIERID}'))))))
        conf['responseBody'] = scalar('BINARY', 'ACKC6', '0')

    names = ['Start Node', 'ReceiveTransfer', 'InitEcsContext', 'ValidateTransfer', 'QueryFleet_API_Check',
        'ReportTransferInitiated', 'WaitPickupStarted', 'ReportTransferring211', 'ReportAcquireStarted602',
        'WaitPickupCompleted', 'ReportAcquireCompleted603', 'WaitDropStarted', 'ReportDepositStarted606',
        'WaitTransferCompleted', 'ReportDepositCompleted607', 'ReportTransferCompleted207']
    meta['connections'], graph['edges'] = [], []
    for i, name in enumerate(names):
        n = nodes[name]
        v = visuals[n['additionalInfo']['graphNodeId']]
        v.update(x=405, y=63+i*150)
        v['data']['detailPanelInfo'] = copy.deepcopy(n['configuration'])
        if i:
            label = ['Inbound request', 'Success', 'Success', 'True'][i-1] if i <= 4 else 'Success'
            previous = nodes[names[i-1]]
            meta['connections'].append(dict(fromIndex=meta['nodes'].index(previous), toIndex=meta['nodes'].index(n), type=label))
            source, target = previous['additionalInfo']['graphNodeId'], n['additionalInfo']['graphNodeId']
            graph['edges'].append(dict(id=f'servostar-stage-edge-{i}', source=source, target=target,
                sourceId=source, targetId=target, start=dict(x=0,y=17), end=dict(x=0,y=-17),
                shape='customEdge', endArrow=True, color='#dfdfdf', type='edge', label=label,
                animate=False, data=dict(detailPanelInfo=dict(type=label))))
    meta['firstNodeIndex'] = meta['nodes'].index(nodes['Start Node'])
    graph['graphData']['name'] = rule['name']
    graph['graphData']['parametersDefine'] = copy.deepcopy(rule['parametersDefine'])
    rule['graphInfo'] = copy.deepcopy(graph)

    def scrub(x):
        if isinstance(x, dict):
            if str(x.get('key', '')).lower() == 'authorization':
                x['value'] = 'PASTE_EXISTING_FLEET_TOKEN'
            for child in x.values(): scrub(child)
        elif isinstance(x, list):
            for child in x: scrub(child)
    scrub(doc)
    encoded = json.dumps(doc, ensure_ascii=False, indent=2)
    assert not re.search(r'(?i)\b[0-9a-f]{64}\b', encoded)
    assert len(meta['nodes']) == 16 and len(meta['connections']) == 15
    # No second submit node, no control/cancel operation and no failure -> success edge.
    urls = [n['configuration']['externalSystemApi']['apiUrl'] for n in meta['nodes'] if n['type'].endswith('TbGrpcApiCallNode')]
    assert sum(url.endswith('/submit') for url in urls) == 1
    assert all(url.rsplit('/',1)[-1] in ('submit','progress','completed','pickup-completed','drop-started') for url in urls)
    out = HERE / 'ecs_candidate/SecsTransferReports03_IMPORT_CANDIDATE.json'
    out.write_text(encoded, encoding='utf-8')
    print('PASS: Reports03 16 nodes / 15 edges; gated 602/603/606/607, one submit, no credential, no upload.')


if __name__ == '__main__': main()
