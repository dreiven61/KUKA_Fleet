"""Create an offline Fleet layout candidate; never contacts Fleet or a robot."""
from pathlib import Path
from copy import deepcopy
import json, math, hashlib
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).parent
SOURCE = ROOT / 'map_lay01_dist1_tl7.json'
OUTPUT = ROOT / 'map_lay01_dist1_aligned_2m_v2.json'
original_bytes = SOURCE.read_bytes()
original = json.loads(original_bytes)
data = deepcopy(original)
floor = data['floorList'][0]
nodes = {str(n['nodeLabel']): n for n in floor['nodeList']}

# Keep the established 3 x 5 grid and snap only the manually placed terminals.
targets = {
    '84': (14,23), '86': (14,11), '87': (18,17), '88': (10,17),
    '89': (12,23), '90': (16,23), '93': (10,19), '94': (18,19),
    '95': (10,15), '96': (18,15), '97': (12,11), '98': (16,11),
}
for label, (x,y) in targets.items():
    nodes[label]['xCoordinate'], nodes[label]['yCoordinate'] = x,y

workstations = ['84','86','89','90','93','94','95','96','97','98']
template = deepcopy(nodes['84']['functionList'][0])
for label in workstations:
    nodes[label]['functionList'] = [deepcopy(template)]

# Each workstation queues at the immediately preceding graph node.
# Reuse the queue serialization present in the user's original Fleet export.
queue_template = deepcopy(nodes['68']['functionList'][0])
station_to_queue = {
    '89':'68', '84':'69', '90':'70',
    '93':'65', '94':'67', '95':'59', '96':'61',
    '97':'56', '86':'57', '98':'58',
}
for station, queue in station_to_queue.items():
    queue_function = deepcopy(queue_template)
    queue_function['relativeNodeLabel'] = station
    nodes[queue]['functionList'] = [queue_function]

# Function numbers are specified in Fleet 2.15 manual p.706 and bundled SDK.
def function(kind, **fields):
    result = deepcopy(template)
    result.update(functionType=kind, containerModel='', photoelectricSupport=0)
    result.pop('relativeNodeLabel', None)
    result.update(fields)
    return result

chargers = [('62','88',0), ('64','87',180)]
for entry, dock, heading in chargers:
    device = f'offline_device-lay01-dist1-{entry}'
    nodes[entry]['functionList'] = [function(
        4, relativeNodeLabel=dock, angle=heading, robotTypeCode='KMP1500I',
        deviceId=device, serialNumber=0)]
    nodes[dock]['functionList'] = [function(
        10, relativeNodeLabel=entry, angle=heading, robotTypeCode='KMP1500I',
        deviceId=device, serialNumber=0)]

# Keep the original bidirectional charging links as explicitly requested.
# Charging function relationships remain in addition to those graph edges.
docks = {'87','88'}
for edge in floor['edgeList']:
    a,b = nodes[edge['beginNodeLabel']],nodes[edge['endNodeLabel']]
    edge['edgeLength'] = round(math.hypot(a['xCoordinate']-b['xCoordinate'],
                                         a['yCoordinate']-b['yCoordinate']),6)

# Validate IDs, axis alignment, exact spacing and both-direction links.
assert len(nodes)==27 and len({n['nodeNumber'] for n in nodes.values()})==27
assert len({n['nodeUuid'] for n in nodes.values()})==27
assert len({(n['xCoordinate'],n['yCoordinate']) for n in nodes.values()})==27
pairs={(e['beginNodeLabel'],e['endNodeLabel']) for e in floor['edgeList']}
assert len(pairs)==len(floor['edgeList'])==68
for a,b in pairs:
    na,nb=nodes[a],nodes[b]
    dx,dy=abs(na['xCoordinate']-nb['xCoordinate']),abs(na['yCoordinate']-nb['yCoordinate'])
    assert (dx,dy) in [(2,0),(0,2)], (a,b,dx,dy)
    assert (b,a) in pairs
for n in nodes.values():
    for fn in n.get('functionList',[]):
        if 'relativeNodeLabel' in fn: assert fn['relativeNodeLabel'] in nodes
for entry,dock,_ in chargers:
    assert math.dist((nodes[entry]['xCoordinate'],nodes[entry]['yCoordinate']),
                     (nodes[dock]['xCoordinate'],nodes[dock]['yCoordinate']))==2
    assert (entry,dock) in pairs and (dock,entry) in pairs
for station,queue in station_to_queue.items():
    assert (station,queue) in pairs and (queue,station) in pairs
    assert nodes[station]['functionList'][0]['functionType']==1
    assert nodes[queue]['functionList'][0]['functionType']==8
    assert nodes[queue]['functionList'][0]['relativeNodeLabel']==station
    assert len({b for a,b in pairs if a==station})==1
seen=set(); pending=['63']
while pending:
    a=pending.pop()
    if a in seen: continue
    seen.add(a)
    pending.extend(b for x,b in pairs if x==a and b not in seen)
assert seen==set(nodes)
OUTPUT.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert json.loads(OUTPUT.read_text(encoding='utf-8'))==data
assert SOURCE.read_bytes()==original_bytes

# Also provide a strictly coordinate-only copy to isolate import issues.
geometry=deepcopy(original)
for n in geometry['floorList'][0]['nodeList']:
    if n['nodeLabel'] in targets:
        n['xCoordinate'],n['yCoordinate']=targets[n['nodeLabel']]
gn={n['nodeLabel']:n for n in geometry['floorList'][0]['nodeList']}
for e in geometry['floorList'][0]['edgeList']:
    a,b=gn[e['beginNodeLabel']],gn[e['endNodeLabel']]
    e['edgeLength']=round(math.dist((a['xCoordinate'],a['yCoordinate']),
                                   (b['xCoordinate'],b['yCoordinate'])),6)
assert all(e['edgeLength']==2 for e in geometry['floorList'][0]['edgeList'])
(ROOT/'map_lay01_dist1_geometry_2m.json').write_text(
    json.dumps(geometry,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

# Simple exported preview, using the matching local SLAM image for context.
bg=Image.open(ROOT.parent/'251216hhj_kuka_floor_2/251216hhj_kuka_floor_2.png').convert('RGB')
SCALE=2
bg=bg.resize((bg.width*SCALE,bg.height*SCALE))
draw=ImageDraw.Draw(bg)
font=ImageFont.truetype(r'C:\Windows\Fonts\arial.ttf',18)
def pixel(n):
    return (round(n['xCoordinate']/.05*SCALE),round((591-n['yCoordinate']/.05)*SCALE))
for a,b in pairs:
    if a<b: draw.line([pixel(nodes[a]),pixel(nodes[b])],fill='#333333',width=3)
for entry,dock,_ in chargers:
    draw.line([pixel(nodes[entry]),pixel(nodes[dock])],fill='#e38b00',width=4)
for label,n in nodes.items():
    x,y=pixel(n)
    color='#2367c9' if label in workstations else '#e88b00' if label in docks else '#009c80' if label in ['62','64'] else '#9937b0' if label in station_to_queue.values() else '#555555'
    draw.ellipse((x-7,y-7,x+7,y+7),fill=color,outline='white',width=2)
    draw.text((x+9,y-21),label,font=font,fill=color,stroke_width=2,stroke_fill='white')
draw.rectangle((8,8,650,110),fill='white')
draw.text((18,15),'2m grid | Blue: Workstation | Orange: Charge',font=font,fill='black')
draw.text((18,43),'Purple: Workstation queue | Green: Charge entry',font=font,fill='black')
draw.text((18,71),'Offline draft - not imported or equipment-tested',font=font,fill='black')
bg.save(ROOT/'map_lay01_dist1_aligned_2m_v2_preview.png')

report={
    'source_sha256':hashlib.sha256(original_bytes).hexdigest(),
    'source_nodes':27, 'source_directed_edges':68,
    'result_nodes':27,'result_directed_edges':68,
    'workstations':workstations, 'container_model':'SIM_TRAY_001',
    'workstation_to_queue':station_to_queue,
    'charge_entry_to_dock':{'62':'88','64':'87'},
    'grid_spacing_m':2, 'coordinate_changes':targets,
    'checks':['JSON round trip','unique node identities and coordinates',
              'all graph edges axis aligned and exactly 2m',
              'all graph edges bidirectional','all nodes graph-connected',
              'both charging docks have bidirectional edges',
              'all 10 workstations have immediate-predecessor queues',
              'all function references exist','source bytes unchanged'],
    'limits':['Fleet import not tested',
              'KMP1500I robotTypeCode assumed from model label; confirm in Fleet',
              'charging settings assembled from documented types and existing function defaults; confirm after import',
              'charge entries are not REST_POINT automatic parking nodes',
              'background map is a local reference; imported floor has laserMapId=-1',
              'aligned terminal 90 may overlap a mapped obstacle; inspect preview before any real motion']}
(ROOT/'map_lay01_dist1_aligned_2m_v2_validation.json').write_text(
    json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
print(OUTPUT)
