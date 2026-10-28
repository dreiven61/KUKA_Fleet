from pathlib import Path
import json
import pypdfium2 as pdfium
from PIL import Image, ImageDraw
base=Path(__file__).parent
manifest={x['key']:x for x in json.loads((base/'manifest.json').read_text(encoding='utf-8'))}
groups=[ [('hardware_p',21),('hardware_i',9),('studio21',64),('training_ko',69)], [('fleet214',223),('fleet215',240),('api',25),('release2151',1)] ]
for n, group in enumerate(groups,1):
    sheet=Image.new('RGB',(1600,2360),'#e0e0e0')
    draw=ImageDraw.Draw(sheet)
    for k,(key,pno) in enumerate(group):
        doc=pdfium.PdfDocument(manifest[key]['file'])
        bitmap=doc[pno-1].render(scale=1.4).to_pil().convert('RGB')
        bitmap.thumbnail((780,1120))
        x=(k%2)*800+(800-bitmap.width)//2
        y=(k//2)*1180+35
        sheet.paste(bitmap,(x,y))
        draw.text(((k%2)*800+20,(k//2)*1180+10),f'{key} / PDF page {pno}',fill='black')
        doc.close()
    target=base/f'review_sheet_{n}.png'
    sheet.save(target)
    print(target)
