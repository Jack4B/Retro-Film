import sys,json
sys.path.insert(0,'/workspace/Retro-Film/tools')
from pathlib import Path
import numpy as np
from PIL import Image
from hald2lut import load_hald, trilinear, grid, quant, lab, de2000
from photocompare import FILMS, PHOTOS, regions, u8
R=Path('/workspace/Retro-Film'); A=R/'core/engine/src/main/assets/luts'; H=Path('/workspace/hald/HaldCLUT'); O=Path('/workspace/photo65'); O.mkdir(exist_ok=True)
cat={c['id']:c for c in json.load((A/'catalog.json').open())['luts']}
imgs={p:np.asarray(Image.open(R/f'tools/golden/photos/{p}.jpg').convert('RGB'),float)/255 for p in PHOTOS}
S={}
for f in FILMS:
    h=load_hald(H/cat[f]['source_hald']); l65=quant(trilinear(h,grid(65)),8)[1]
    for p,img in imgs.items():
        a=trilinear(h,img); b=trilinear(l65,img); d=de2000(lab(np.clip(a,0,1)),lab(np.clip(b,0,1)))
        S.setdefault(f,{})[p]=dict(mean=round(float(d.mean()),3),p99=round(float(np.percentile(d,99)),3),max=round(float(d.max()),3),gt2=int((d>2).sum()),gt3=int((d>3).sum()),gt3_regions=regions(d>3))
        Image.fromarray(np.concatenate([u8(a),u8(b),u8(np.clip(abs(a-b)*10,0,1))],1)).save(O/f"{f.replace('/','__')}__{p}__65.jpg",quality=95)
        print(f,p,S[f][p],flush=True)
json.dump(S,open(O/'stats.json','w'),indent=1)
