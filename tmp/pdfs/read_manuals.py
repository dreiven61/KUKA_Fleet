from pathlib import Path
from pypdf import PdfReader
import json

root = Path(r'C:\work\KUKA')
out = root / 'tmp' / 'pdfs'
out.mkdir(parents=True, exist_ok=True)
files = [
 ('hardware_p', root/'교육/BA_KMP_1500P_series_en.pdf'),
 ('hardware_i', root/'교육/KMP_1500I_diffDrive_en_V2.0.pdf'),
 ('studio21', root/'교육/AMR training/KUKA.AMR_Studio_2.1_en_V1.pdf'),
 ('fleet214', root/'교육/AMR training/KUKA_AMR_Fleet_2.14_en V2.pdf'),
 ('training_ko', root/'교육/AMR training/AMR 교육자료-Kor 260120.pdf'),
 ('api', root/'교육/AMR training/KUKA.AMR Fleet Standard API Documentation_EN.pdf'),
 ('release2151', root/'SW/KUKA.AMR_Fleet_2.15.1/KUKA.AMR Fleet 2.15.1 Release Notes - EN.pdf'),
 ('fleet215', root/'SW/KUKA.AMR_Fleet_2.15.1/KUKA_AMR_Fleet_2.15_en_V2.pdf'),
]
manifest=[]
for key, path in files:
    reader=PdfReader(path)
    pages=[p.extract_text() or '' for p in reader.pages]
    (out/(key+'.txt')).write_text('\n'.join(f'\n=== PDF PAGE {i+1} ===\n{t}' for i,t in enumerate(pages)),encoding='utf-8')
    (out/(key+'.json')).write_text(json.dumps(pages,ensure_ascii=False),encoding='utf-8')
    info={'key':key,'file':str(path),'pages':len(pages),'characters':sum(map(len,pages)),'empty_pages':[i+1 for i,t in enumerate(pages) if len(t.strip())<40]}
    manifest.append(info)
    print(json.dumps(info,ensure_ascii=False))
    print('\n'.join(f'PAGE {i+1}: {t[:7000]}' for i,t in enumerate(pages[:min(7,len(pages))])))
(out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
