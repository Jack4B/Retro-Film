import sys,json,argparse
import os
from pathlib import Path
# Sciezki: argumenty CLI lub zmienne srodowiskowe (HALD_DIR = rozpakowany HaldCLUT.zip, OUT_DIR = wyniki).
R = Path(os.environ.get("RF_ROOT", Path(__file__).resolve().parents[2]))
sys.path.insert(0,str(R/'tools'))
import numpy as np
from hald2lut import load_hald, trilinear, grid, quant, lab, de2000
ap=argparse.ArgumentParser(description='diagnoza maksimow dE'); ap.add_argument('--hald',default=os.environ.get('HALD_DIR')); ap.add_argument('--out',default=os.environ.get('OUT_FILE','worst-diagnosis.json'))
args=ap.parse_args(); assert args.hald, 'podaj --hald lub HALD_DIR'
H=Path(args.hald); cat={c['id']:c for c in json.load(open(R/'tools/packs/catalog-33-full.json'))['luts']}
F=["colorslide/fuji_superia_200_xpro","bw/fuji_neopan_1600","negative_old/fuji_neopan_1600_p","negative_old/fuji_neopan_1600_pp","instant_consumer/polaroid_px_70_cold_p"]
out={}
for f in F:
    h=load_hald(H/cat[f]['source_hald']); L=lab(h); pts=grid(144)
    nb=np.zeros(h.shape[:3])
    for ax in range(3):
        d=de2000(np.take(L,range(1,144),ax),np.take(L,range(0,143),ax))
        sl=[slice(None)]*3; sl[ax]=slice(1,None); nb[tuple(sl)]=np.maximum(nb[tuple(sl)],d)
        sl[ax]=slice(0,143); nb[tuple(sl)]=np.maximum(nb[tuple(sl)],d)
    r={'neighbor_dE_global':{'p50':round(float(np.median(nb)),3),'p99':round(float(np.percentile(nb,99)),3),'max':round(float(nb.max()),3)}}
    for n in (33,65):
        l=quant(trilinear(h,grid(n)),8)[1]
        e=np.concatenate([de2000(L[b:b+16],lab(np.clip(trilinear(l,pts[b:b+16]),0,1))) for b in range(0,144,16)])
        i=np.unravel_index(e.argmax(),e.shape); b,g,rr=i
        loc=nb[max(b-2,0):b+3,max(g-2,0):g+3,max(rr-2,0):rr+3]
        top=e>np.percentile(e,99.9)
        r[f'{n}']={'max':round(float(e.max()),3),'at_input_rgb8':[int(round(x*255)) for x in pts[i]],'src_out_rgb8':[int(round(x*255)) for x in h[i]],
          'neighbor_dE_local_max':round(float(loc.max()),3),
          'top0.1pct_mean_input_luma':round(float((pts[top]@[.2126,.7152,.0722]).mean()),3),
          'top0.1pct_mean_neighbor_dE':round(float(nb[top].mean()),3)}
    out[f]=r; print(f,json.dumps(r),flush=True)
json.dump(out,open(args.out,'w'),indent=1)
