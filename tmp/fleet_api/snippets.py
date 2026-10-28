from pathlib import Path
import re,sys
sys.stdout.reconfigure(encoding='utf-8')
pattern=re.compile(sys.argv[1],re.I)
limit=int(sys.argv[2]) if len(sys.argv)>2 else 60
count=0
for path in Path(__file__).parent.glob('v1_*.js'):
    if any(s in path.name for s in ['vendors','login.']): continue
    text=path.read_text(encoding='utf-8')
    for m in pattern.finditer(text):
        print(path.name, text[max(0,m.start()-220):m.end()+500])
        count+=1
        if count>=limit: sys.exit()
