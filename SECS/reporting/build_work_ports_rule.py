"""Reports07: ten exported workstation ports. Offline generation only; never uploads."""
import argparse
import copy
import json
import re
import uuid
from pathlib import Path
from build_carrier_rule import HERE, headers

PORTS = [f'PORT_{n}' for n in (84, 86, 89, 90, 93, 94, 95, 96, 97, 98)]


def membership(variable):
    return '(' + ' || '.join(f"'${{{variable}}}' == '{p}'" for p in PORTS) + ')'


def verify_inventory():
    root = HERE.parent.parent
    export = json.loads((root / 'MAP/map_lay01_dist1/map_lay01_dist1_yft.json').read_text(encoding='utf-8-sig'))
    floor = next(f for f in export['floorList'] if f['floorNumber'] == 'dist1')
    work = [n for n in floor['nodeList'] if any(f['functionType'] == 1 and f.get('containerModel') == 'SIM_TRAY_001' for f in n.get('functionList', []))]
    assert sorted(PORTS) == sorted(f"PORT_{n['nodeNumber']}" for n in work)
    for path in [root / 'VirtualMCS/Core/WorkPorts.cs', HERE.parent / 'fleet_transfer_plugin/src/servostar/fleet/transfer/WorkPorts.java']:
        assert set(re.findall(r'"(PORT_\d+)"', path.read_text(encoding='utf-8'))) == set(PORTS), path
    # Completion confirmation uses exact External Codes; do not invent codes for new ports.
    assert {n['nodeNumber']: n.get('foreignCode', '') for n in work} == {
        n: f'SECS_ST_{n}' if n in (89, 97) else '' for n in (84, 86, 89, 90, 93, 94, 95, 96, 97, 98)}


def build(with_token=False):
    verify_inventory()
    doc = json.loads((HERE / 'ecs_candidate/SecsTransferReports06_Auto_IMPORT_CANDIDATE.json').read_text(encoding='utf-8-sig'))
    rule = doc['ruleChains'][0]
    encoded = json.dumps(doc, ensure_ascii=False)
    for old in [rule['id']['id']] + [p['id']['id'] for p in rule['parametersDefine']]:
        encoded = encoded.replace(old, str(uuid.uuid4()))
    doc = json.loads(encoded)
    rule, meta = doc['ruleChains'][0], doc['metadata'][0]
    graph = meta['graphInfo']
    rule.update(name='SecsTransferReports07_AllPorts_Ready' if with_token else 'SecsTransferReports07_AllPorts', createdTime=0, updatedTime=0)
    validation = next(n for n in meta['nodes'] if n['name'] == 'ValidateTransfer')['configuration']['configurationNodes']
    fields = {f['attributeName']: f for f in validation}
    fields['SOURCEPORT']['attributeValue'] = membership('SOURCEPORT')
    fields['DESTPORT']['attributeValue'] = membership('DESTPORT') + " && '${SOURCEPORT}' != '${DESTPORT}'"
    assert fields['REPLACE']['attributeValue'] == "'${REPLACE}' == '0'"
    for node in meta['nodes']:
        visual = next(v for v in graph['nodes'] if v['id'] == node['additionalInfo']['graphNodeId'])
        visual['data']['detailPanelInfo'] = copy.deepcopy(node['configuration'])
    graph['graphData'].update(name=rule['name'], parametersDefine=copy.deepcopy(rule['parametersDefine']))
    rule['graphInfo'] = copy.deepcopy(graph)
    token = 'PASTE_EXISTING_FLEET_TOKEN'
    if with_token:
        existing = json.loads((HERE / 'ecs_candidate/SecsTransferReports06_Auto_Ready_LOCAL_WITH_TOKEN.json').read_text(encoding='utf-8-sig'))
        tokens = {h.get('value', '') for h in headers(existing)}
        assert len(tokens) == 1 and re.fullmatch(r'[0-9a-fA-F]{64}', next(iter(tokens)))
        token = next(iter(tokens))
    for h in headers(doc):
        h['value'] = token
    assert len(meta['nodes']) == 28 and len(meta['connections']) == 27
    calls = [n for n in meta['nodes'] if n['type'].endswith('TbExternalSystemApiNode') or 'externalSystemApi' in n['configuration']]
    assert len(calls) == 10
    assert sum(n['configuration']['externalSystemApi']['apiUrl'].endswith('/submit') for n in calls) == 1
    # Validate both the server configuration and both UI graph copies for all 100 pairs.
    configs = [fields]
    for g in (graph, rule['graphInfo']):
        v = next(v for v in g['nodes'] if v.get('label') == 'ValidateTransfer')
        configs.append({f['attributeName']: f for f in v['data']['detailPanelInfo']['configurationNodes']})
    for source in PORTS + ['PORT_87', 'PORT_88', 'PORT_62', 'PORT_999']:
        for dest in PORTS + ['PORT_87', 'PORT_99']:
            for config in configs:
                expression = config['SOURCEPORT']['attributeValue'] + ' && (' + config['DESTPORT']['attributeValue'] + ')'
                expression = expression.replace('${SOURCEPORT}', source).replace('${DESTPORT}', dest).replace('||', ' or ').replace('&&', ' and ')
                assert eval(expression, {'__builtins__': {}}, {}) == (source in PORTS and dest in PORTS and source != dest)
    encoded = json.dumps(doc, ensure_ascii=False, indent=2)
    if not with_token:
        assert not re.search(r'(?i)\b[0-9a-f]{64}\b', encoded)
    out = HERE / 'ecs_candidate' / ('SecsTransferReports07_AllPorts_Ready_LOCAL_WITH_TOKEN.json' if with_token else 'SecsTransferReports07_AllPorts_IMPORT_CANDIDATE.json')
    out.write_text(encoded, encoding='utf-8')
    print('PASS: ten map work ports match PC/plugin catalogs; 90 valid pairs; charging/transit/same-port blocked; 28 nodes, 27 links, one submit. No upload.')
    print('Created: ' + str(out))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--with-existing-token', action='store_true')
    build(parser.parse_args().with_existing_token)
