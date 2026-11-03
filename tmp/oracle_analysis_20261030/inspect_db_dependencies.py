"""Read-only inventory of extracted Fleet binaries. Counts are static candidates,
not executed SQL or a complete database migration inventory. No credentials.
"""
import collections,hashlib,json,pathlib,re,struct,sys,zipfile
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]/'rcs_native_link_inspect'
OUT=pathlib.Path(__file__).resolve().parent

def constants(data):
    if data[:4]!=b'\xca\xfe\xba\xbe':return []
    count=struct.unpack('>H',data[8:10])[0];offset=10;i=1;values=[]
    while i<count:
        tag=data[offset];offset+=1
        if tag==1:
            size=struct.unpack('>H',data[offset:offset+2])[0];offset+=2
            values.append(data[offset:offset+size].decode('utf-8','replace'));offset+=size
        elif tag in (3,4):offset+=4
        elif tag in (5,6):offset+=8;i+=1
        elif tag in (7,8,16,19,20):offset+=2
        elif tag in (9,10,11,12,17,18):offset+=4
        elif tag==15:offset+=3
        else:raise ValueError('Unknown constant tag '+str(tag))
        i+=1
    return values

patterns={
 'limit':r'\blimit\b','distinct_on':r'\bdistinct\s+on\s*\(',
 'on_conflict':r'\bon\s+conflict\b','returning':r'\breturning\b',
 'pg_cast':r'::\s*(varchar|text|jsonb?|int\w*|numeric|timestamp|date|bool\w*)\b',
 'sequence_functions':r'\b(nextval|currval|lastval|setval)\s*\(',
 'string_agg':r'\bstring_agg\s*\(','ilike':r'\bilike\b',
 'json_pg':r'\bjsonb?_\w+\s*\(|->>|#>>',
 'empty_string_compare':r"(?:!=|<>|=)\s*''",
 'select_without_from':r"^\s*select\s+[^;]+$",
}
all_entries=[];configs=[];jars=[];refs=[]
for component in ['wcs-engine','basic-data']:
    for path in sorted((ROOT/component).glob('*.jar')):
        vendor=path.name.startswith(('wcs-','krcs_','krcs-','basic-data'))
        if not vendor:continue
        jars.append(dict(component=component,jar=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                if name.endswith('.class') and name.startswith(('com/kuka/','com/kukarobotics/')):
                    values=constants(z.read(name))
                    for value in values:
                        if 'org/postgresql/' in value or 'org.postgresql.' in value:
                            refs.append(dict(component=component,jar=path.name,resource=name,reference=value))
                        if re.search(r'\b(select|insert\s+into|update|delete\s+from|create\s+table|alter\s+table)\b',value,re.I) and re.search(r'\bt_[a-z_]+\b',value):
                            hits=[k for k,p in patterns.items() if re.search(p,value,re.I)]
                            if 'select_without_from' in hits and re.search(r'\bfrom\b',value,re.I):hits.remove('select_without_from')
                            all_entries.append(dict(component=component,jar=path.name,resource=name,sql=value,patterns=hits))
                    if name.endswith('PageHelperConfig.class'):
                        configs.append(dict(component=component,jar=path.name,resource=name,constants=[v for v in values if v in ['helperDialect','postgresql','oracle']]))
                elif name.endswith(('.properties','.yml','.yaml')) and ('application' in name or 'database' in name):
                    lines=[]
                    for line in z.read(name).decode('utf-8','replace').splitlines():
                        if any(v in line.lower() for v in ['datasource','driver-class','dialect','jdbc:']) and not any(v in line.lower() for v in ['password','secret','token']):lines.append(line)
                    if lines:configs.append(dict(component=component,jar=path.name,resource=name,lines=lines))
counts=collections.Counter(h for e in all_entries for h in e['patterns'])
summary=dict(scope='Static SQL constants in locally extracted WCS/basic-data vendor JARs; generated SQL, other services and runtime schema not fully inventoried',jars=len(jars),sqlConstantLocations=len(all_entries),distinctSqlTexts=len(set(e['sql'] for e in all_entries)),patternLocations=dict(counts),byComponent=dict(collections.Counter(e['component'] for e in all_entries)),postgresClassReferences=len(refs))
result=dict(summary=summary,jars=jars,configuration=configs,postgresReferences=refs,sql=all_entries)
(OUT/'static_inventory.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False,indent=2))
for key in patterns:
    found=[e for e in all_entries if key in e['patterns']]
    if found:
        e=found[0];print(key,e['resource'],re.sub(r'\s+',' ',e['sql'])[:330])
