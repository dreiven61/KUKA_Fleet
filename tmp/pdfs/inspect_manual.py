from pathlib import Path
import json, sys, re
sys.stdout.reconfigure(encoding='utf-8')
base=Path(__file__).parent
key=sys.argv[1]
pages=json.loads((base/(key+'.json')).read_text(encoding='utf-8'))
spec=sys.argv[2] if len(sys.argv)>2 else '1'
cap=int(sys.argv[3]) if len(sys.argv)>3 else 0
if spec.startswith('?'):
    pat=re.compile(spec[1:],re.I)
    for i,p in enumerate(pages):
        hits=[re.sub(r'\s+',' ',p[max(0,m.start()-150):m.end()+350]) for m in pat.finditer(p)]
        if hits: print(f'PAGE {i+1}: '+ ' | '.join(hits[:4]))
else:
    indexes=[]
    for part in spec.split(','):
        a,*b=part.split('-')
        indexes.extend(range(int(a),int(b[0])+1) if b else [int(a)])
    for i in indexes:
        p=pages[i-1]
        print(f'\n=== {key} PDF PAGE {i} ===\n'+(p[:cap] if cap else p))
