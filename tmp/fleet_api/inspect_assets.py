from pathlib import Path
from urllib.request import urlopen
from urllib.parse import urljoin
import re,sys
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).parent
for name,url in [('v2','http://192.168.0.45:5000/'),('v1','http://192.168.0.45:5000/v1/')]:
    html=urlopen(url,timeout=8).read().decode()
    (root/(name+'.html')).write_text(html,encoding='utf-8')
    assets=re.findall(r'(?:src|href)=["\x27]([^"\x27]+)',html)
    print(name,assets)
    for path in assets:
        if '.js' not in path: continue
        target=urljoin(url,path)
        content=urlopen(target,timeout=15).read().decode(errors='replace')
        local=root/(name+'_'+path.split('/')[-1].split('?')[0])
        local.write_text(content,encoding='utf-8')
        print(local.name,len(content))
