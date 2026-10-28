"""Create a separate ECS import candidate; never upload or edit a live rule.

Authorization values are replaced with a placeholder in every graph copy.
The implemented TransferInitiated serializer branch is deliberately reused for
211/207; it preserves the configured CEID in the offline vendor-library probe.
"""
import copy
import json
import re
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'SECS/native_transfer/manual_api/SecsTransferTest (4).json'
OUT = Path(__file__).resolve().parent / 'ecs_candidate'
TOKEN = 'PASTE_EXISTING_FLEET_TOKEN'


def clone(value):
    return copy.deepcopy(value)


def scalar(kind, name, value):
    return dict(type=kind, value=value, children=[], paramName=name,
                paramType='DYNAMIC', paramLabel=name, typeValid=True, valueValid=True)


def main():
    export = json.loads(SOURCE.read_text(encoding='utf-8-sig'))
    rule, meta = export['ruleChains'][0], export['metadata'][0]
    graph = meta['graphInfo']
    new_id = str(uuid.uuid4())
    rule['id']['id'] = meta['ruleChainId']['id'] = new_id
    rule['name'] = 'SecsTransferReports02'
    rule['root'] = False
    rule['firstRuleNodeId'] = None
    rule['createdTime'] = rule['updatedTime'] = 0
    for param in rule['parametersDefine']:
        param['id']['id'] = str(uuid.uuid4())
        param['ruleChainId']['id'] = new_id
    existing = {p['paramName'] for p in rule['parametersDefine']}
    for name in ('ACKC6', 'CEID', 'RPTID', 'RESULTCODE'):
        if name not in existing:
            param = clone(rule['parametersDefine'][0])
            param.update(paramName=name, paramDesc=name, paramType='DYNAMIC')
            param.pop('valuesDefine', None)
            param['id']['id'] = str(uuid.uuid4())
            param['ruleChainId']['id'] = new_id
            rule['parametersDefine'].append(param)

    originals = {n['name']: n for n in meta['nodes']}
    report = originals['ReportTransferInitiated']
    query = originals['QueryFleet_API_Check']
    ui = {n['id']: n for n in graph['nodes']}
    report_ui = ui[report['additionalInfo']['graphNodeId']]
    query_ui = ui[query['additionalInfo']['graphNodeId']]

    def set_report(node, ceid, location):
        conf = node['configuration']
        # Compatibility choice: actual wire event is the configured U2 CEID.
        conf['eventName'] = 'TransferInitiated'
        conf['maxRetryTimes'] = 1
        conf['responseBody'] = scalar('BINARY', 'ACKC6', '0')
        body = conf['requestBody']
        body['children'][1] = scalar('U2', 'CEID', str(ceid))
        payload = body['children'][2]['children'][0]
        payload['children'][0] = scalar('U2', 'RPTID', '2')
        values = payload['children'][1]['children']
        values[1]['children'][0]['children'][1] = scalar('A', location, '${'+location+'}')
        values[2] = scalar('U2', 'RESULTCODE', '0')

    set_report(report, 208, 'SOURCEPORT')

    def add(template, ui_template, name):
        n, visual = clone(template), clone(ui_template)
        n['name'] = name
        graph_id = 'servostar-report-' + name
        n['additionalInfo']['graphNodeId'] = graph_id
        visual['id'] = graph_id
        visual['data'].pop('editId', None)
        visual['label'] = name
        meta['nodes'].append(n)
        graph['nodes'].append(visual)
        return n

    wait_pickup = add(query, query_ui, 'WaitPickupStarted')
    in_progress = add(report, report_ui, 'ReportTransferring211')
    wait_complete = add(query, query_ui, 'WaitTransferCompleted')
    completed = add(report, report_ui, 'ReportTransferCompleted207')
    for gate, suffix in ((wait_pickup, 'progress'), (wait_complete, 'completed')):
        gate['configuration']['maxRetryTimes'] = 120
        gate['configuration']['retryPeriod'] = 5
        api = gate['configuration']['externalSystemApi']
        api['apiName'] = 'Fleet_Transfer_' + suffix
        api['apiUrl'] = 'http://192.168.0.45:10870/interfaces/api/mes-transfer/' + suffix
    set_report(in_progress, 211, 'SOURCEPORT')
    set_report(completed, 207, 'DESTPORT')

    # One connected chain; no failure edge leads to a successful completion.
    path = [originals['Start Node'], originals['ReceiveTransfer'], originals['InitEcsContext'],
            originals['ValidateTransfer'], query, report, wait_pickup, in_progress, wait_complete, completed]
    labels = ['Inbound request', 'Success', 'Success', 'True'] + ['Success']*5
    meta['connections'] = []
    graph['edges'] = []
    for i, n in enumerate(path):
        visual = next(v for v in graph['nodes'] if v['id'] == n['additionalInfo']['graphNodeId'])
        visual.update(x=405, y=63+i*150)
        visual['data']['detailPanelInfo'] = clone(n['configuration'])
        if i:
            source, target = path[i-1], n
            label = labels[i-1]
            meta['connections'].append(dict(fromIndex=meta['nodes'].index(source), toIndex=meta['nodes'].index(target), type=label))
            edge = dict(id='servostar-edge-'+str(i), source=source['additionalInfo']['graphNodeId'],
                        target=target['additionalInfo']['graphNodeId'], start=dict(x=0,y=17), end=dict(x=0,y=-17),
                        shape='customEdge', endArrow=True, color='#dfdfdf', type='edge', label=label,
                        animate=False, data=dict(detailPanelInfo=dict(type=label)))
            edge['sourceId'], edge['targetId'] = edge['source'], edge['target']
            graph['edges'].append(edge)
    graph['graphData']['name'] = rule['name']
    graph['graphData']['parametersDefine'] = clone(rule['parametersDefine'])
    rule['graphInfo'] = clone(graph)

    def scrub(value):
        if isinstance(value, dict):
            if str(value.get('key', '')).lower() == 'authorization':
                value['value'] = TOKEN
            for child in value.values():
                scrub(child)
        elif isinstance(value, list):
            for child in value:
                scrub(child)
    scrub(export)
    encoded = json.dumps(export, ensure_ascii=False, indent=2)
    assert not re.search(r'(?i)\b[0-9a-f]{64}\b', encoded), 'Credential-like data retained'
    assert len(meta['nodes']) == 10 and len(meta['connections']) == 9
    assert meta['firstNodeIndex'] == meta['nodes'].index(path[0])
    for e in meta['connections']:
        assert 0 <= e['fromIndex'] < 10 and 0 <= e['toIndex'] < 10
    for n in (report, in_progress, completed):
        body=n['configuration']['requestBody']
        assert body['type']=='L' and len(body['children'])==3
        assert n['configuration']['responseBody']['value']=='0'
    for n in (wait_pickup, wait_complete):
        fields=n['configuration']['responseBody']['children']
        assert {f['externalName']:f['value'] for f in fields} == {'success':'true','code':'0'}
        assert len(n['configuration']['requestBody']['children'])==6
    OUT.mkdir(exist_ok=True)
    (OUT/'SecsTransferReports02_IMPORT_CANDIDATE.json').write_text(encoded,encoding='utf-8')
    # Small per-node configs make in-place UI entry possible without replacing the rule.
    (OUT/'reporting_nodes.reference.json').write_text(json.dumps([
        dict(name=n['name'],configuration=n['configuration']) for n in (wait_pickup,in_progress,wait_complete,completed)
    ],ensure_ascii=False,indent=2),encoding='utf-8')
    print('PASS: separate 10-node / 9-edge ECS candidate; six-field requests, gated 211/207, ACK B0, no stored token. No upload.')


if __name__ == '__main__':
    main()
