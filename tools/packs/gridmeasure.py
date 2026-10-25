import sys,json,argparse
import os
from pathlib import Path
# Sciezki: argumenty CLI lub zmienne srodowiskowe (HALD_DIR = rozpakowany HaldCLUT.zip, OUT_DIR = wyniki).
R = Path(os.environ.get("RF_ROOT", Path(__file__).resolve().parents[2]))
sys.path.insert(0,str(R/'tools'))
from multiprocessing import Pool
import numpy as np
from hald2lut import load_hald, trilinear, grid, quant, lab, de2000
from luteval import evaluate, tetrahedral, stats
ap=argparse.ArgumentParser(description='65^3 i 144^3 -> stats.json'); ap.add_argument('--hald',default=os.environ.get('HALD_DIR')); ap.add_argument('--out',default=os.environ.get('OUT_DIR','gridpack')); ap.add_argument('-j',type=int,default=6)
args=ap.parse_args(); assert args.hald, 'podaj --hald lub HALD_DIR'
A=R/'core/engine/src/main/assets/luts'; H=Path(args.hald); O=Path(args.out)
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
if __name__=='__main__':
  cat=json.load((A/'catalog.json').open())['luts']
  with Pool(args.j) as p: res=p.map(work,cat)
  json.dump(res,open(O/'stats.json','w'),indent=1)
