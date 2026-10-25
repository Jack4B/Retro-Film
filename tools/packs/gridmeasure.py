import sys,json
sys.path.insert(0,'/workspace/Retro-Film/tools')
from pathlib import Path
from multiprocessing import Pool
import numpy as np
from hald2lut import load_hald, trilinear, grid, quant, lab, de2000
from luteval import evaluate, tetrahedral, stats
A=Path('/workspace/Retro-Film/core/engine/src/main/assets/luts'); H=Path('/workspace/hald/HaldCLUT'); O=Path('/workspace/gridpack')
def mid(h):
    n=h.shape[0]; v=(np.arange(n-1)+0.5)/(n-1); out=[]
    for bi in range(0,n-1,16):
        b,g,r=np.meshgrid(v[bi:bi+16],v,v,indexing='ij'); p=np.stack([r,g,b],-1)
        out.append(de2000(lab(np.clip(trilinear(h,p),0,1)),lab(np.clip(tetrahedral(h,p),0,1))).ravel())
    return stats(np.concatenate(out))
def work(c):
    h=load_hald(H/c['source_hald']); g=trilinear(h,grid(65)); r={'id':c['id']}
    for bits in (8,16):
        q,l=quant(g,bits); f=O/f'65_{bits}'/(c['id']+f'.rgb{bits}'); f.parent.mkdir(parents=True,exist_ok=True); f.write_bytes(q.reshape(-1,3).tobytes())
        r[f'65_{bits}']=evaluate(h,l,trilinear)
    f=O/'144_8'/(c['id']+'.rgb8'); f.parent.mkdir(parents=True,exist_ok=True); f.write_bytes(np.round(h*255).astype(np.uint8).reshape(-1,3).tobytes())
    r['144_mid_tri_vs_tetra']=mid(h)
    print(r,flush=True); return r
cat=json.load((A/'catalog.json').open())['luts']
with Pool(6) as p: res=p.map(work,cat)
json.dump(res,open(O/'stats.json','w'),indent=1)
