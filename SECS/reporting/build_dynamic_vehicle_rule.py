"""Reports06: automatic dispatch among existing AMRs 1/2/3, actual vehicle in reports. No network."""
import argparse, copy, json, re, uuid
from build_carrier_rule import HERE, headers

def walk(x):
    if isinstance(x,dict):
        yield x
        for v in x.values():yield from walk(v)
    elif isinstance(x,list):
        for v in x:yield from walk(v)

def build(with_token=False):
    source=json.loads((HERE/'ecs_candidate/SecsTransferReports05_IMPORT_CANDIDATE.json').read_text(encoding='utf-8-sig'))
    text=json.dumps(source,ensure_ascii=False)
    rule=source['ruleChains'][0]
    for old in [rule['id']['id']]+[p['id']['id'] for p in rule['parametersDefine']]:text=text.replace(old,str(uuid.uuid4()))
    doc=json.loads(text);rule,meta=doc['ruleChains'][0],doc['metadata'][0];graph=meta['graphInfo']
    rule.update(name='SecsTransferReports06_Auto_Ready' if with_token else 'SecsTransferReports06_Auto',createdTime=0,updatedTime=0)
    nodes={n['name']:n for n in meta['nodes']}
    # This installed ECS compares every mapped response field, then serializes it with Gson.
    # Numeric vehicleId gives unquoted metadata 1/2/3; the STRING robotId API field remains for diagnostics.
    assigned=nodes['WaitVehicleAssigned']['configuration']['responseBody']['children']
    assigned.append(dict(name='robotId',externalName='vehicleId',type='INTEGER',valueType='DYNAMIC',
        desc='Actual WCS assigned AMR ID',children=[],value='1,2,3',id=str(uuid.uuid4())))
    changes=0
    for n in meta['nodes']:
        if not n['type'].endswith('TbEventReportSendNode'):continue
        for field in walk(n['configuration']['requestBody']):
            if field.get('paramName')=='robotId':field['value']='${robotId}';changes+=1
        if n['name']=='ReportCarrierInstalled301':
            fields=[f for f in walk(n['configuration']['requestBody']) if f.get('paramName')=='CARRIERLOC']
            assert len(fields)==1;fields[0].update(value='${robotId}');changes+=1
    assert changes==12  # 9 RPTID5 + 2 RPTID3 vehicle fields and 301 location
    for n in meta['nodes']:
        v=next(v for v in graph['nodes'] if v['id']==n['additionalInfo']['graphNodeId'])
        v['data']['detailPanelInfo']=copy.deepcopy(n['configuration'])
    graph['graphData'].update(name=rule['name'],parametersDefine=copy.deepcopy(rule['parametersDefine']))
    rule['graphInfo']=copy.deepcopy(graph)
    token='PASTE_EXISTING_FLEET_TOKEN'
    if with_token:
        old=json.loads((HERE.parent/'native_transfer/manual_api/SecsTransferTest (4).json').read_text(encoding='utf-8-sig'))
        tokens={str(h.get('value','')).strip() for h in headers(old)}
        tokens={v for v in tokens if re.fullmatch('[0-9a-fA-F]{64}',v)}
        if len(tokens)!=1:raise ValueError('Expected one existing token')
        token=next(iter(tokens))
    hs=list(headers(doc));assert len(hs)==30
    for h in hs:h['value']=token
    assert len(meta['nodes'])==28 and len(meta['connections'])==27
    for n in meta['nodes']:
        if n['type'].endswith('TbEventReportSendNode'):
            assert not any(f.get('paramName')=='robotId' and f.get('value')=='1' for f in walk(n['configuration']['requestBody']))
    encoded=json.dumps(doc,ensure_ascii=False,indent=2)
    if not with_token:assert not re.search(r'(?i)\b[0-9a-f]{64}\b',encoded)
    out=HERE/'ecs_candidate'/('SecsTransferReports06_Auto_Ready_LOCAL_WITH_TOKEN.json' if with_token else 'SecsTransferReports06_Auto_IMPORT_CANDIDATE.json')
    out.write_text(encoded,encoding='utf-8')
    print('PASS: Reports06 28 nodes/27 edges, actual assigned vehicle mapping, 10 API nodes, one submit; no upload.')
    print('Created: '+str(out))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--with-existing-token',action='store_true');build(p.parse_args().with_existing_token)
