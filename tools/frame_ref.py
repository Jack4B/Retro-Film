"""Render referencyjny ramki FilmSim. Wszystkie wymiary jako ulamek boku S."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
SPEC = dict(side=0.05, top=0.05, photo_h=0.70, caption_y=0.825, caption_size=0.075,
            exif_y=0.925, exif_size=0.026, label_size=0.018)
def render(photo, text, S=2160, bg="#FFFFFF", ink="#1F3A8A", exif=None):
    c = Image.new("RGB", (S, S), bg); d = ImageDraw.Draw(c)
    bw, bh = int(S*(1-2*SPEC["side"])), int(S*SPEC["photo_h"])
    p = photo.copy(); p.thumbnail((bw, bh), Image.LANCZOS)
    c.paste(p, ((S-p.width)//2, int(S*SPEC["top"]) + (bh-p.height)//2))
    f = ImageFont.truetype(str(Path(__file__).with_name("Caveat.ttf")), int(S*SPEC["caption_size"]))
    d.text((S/2, S*SPEC["caption_y"]), text, font=f, fill=ink, anchor="mm")
    if exif:
        fv = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", int(S*SPEC["exif_size"]))
        fl = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", int(S*SPEC["label_size"]))
        col = S*(1-2*SPEC["side"])/len(exif)
        for i,(v,l) in enumerate(exif):
            x = S*SPEC["side"] + col*(i+.5)
            d.text((x, S*SPEC["exif_y"]), v, font=fv, fill="#222", anchor="mm")
            d.text((x, S*(SPEC["exif_y"]+.035)), l, font=fl, fill="#888", anchor="mm")
            if i: d.line([(x-col/2, S*(SPEC["exif_y"]-.012)), (x-col/2, S*(SPEC["exif_y"]+.045))], fill="#ccc", width=2)
    return c
src = Image.open(sys.argv[1]).convert("RGB")
W,H = src.size; photo = src.crop((int(W*.027), int(H*.016), int(W*.976), int(H*.783)))
ex = [("6400","ISO"),("1,8","F"),("24","mm"),("1/7","S")]
a = render(photo, "Jack4B", exif=ex)
lw = photo.width; land = photo.crop((0, int(photo.height*.25), lw, int(photo.height*.25+lw*2/3)))
b = render(land, "Jack4B")
sheet = Image.new("RGB", (2*1080+60, 1080+40), "#DDD")
sheet.paste(a.resize((1080,1080)), (20,20)); sheet.paste(b.resize((1080,1080)), (1100,20))
sheet.save(sys.argv[2] if len(sys.argv)>2 else "frame_ref.png")
