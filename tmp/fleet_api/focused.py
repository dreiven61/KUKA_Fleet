from pathlib import Path
import sys
sys.stdout.reconfigure(encoding='utf-8')
path=next(Path(__file__).parent.glob(sys.argv[1]))
s=path.read_text(encoding='utf-8')
width=int(sys.argv[3]) if len(sys.argv)>3 else 2500
for token in sys.argv[2].split('|'):
    p=s.find(token)
    print('\nTOKEN',token,'INDEX',p,'\n',s[max(0,p-150):p+width] if p>=0 else 'NOT FOUND')
