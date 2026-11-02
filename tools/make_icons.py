from PIL import Image, ImageDraw, ImageFont
"""Génère la bannière Android TV et l'icône de l'appli (appelé par la compilation GitHub)."""
import os, sys
R = sys.argv[1]
for d in ("drawable-xhdpi", "mipmap-xhdpi", "mipmap-xxxhdpi"):
    os.makedirs(os.path.join(R, d), exist_ok=True)
def font(sz, bold=True):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
              "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"]:
        try: return ImageFont.truetype(p, sz)
        except Exception: pass
    return ImageFont.load_default()
def gradient(w,h):
    img = Image.new("RGB",(w,h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t=y/h
        d.line([(0,y),(w,y)], fill=(int(6+8*t),int(20+23*t),int(26+16*t)))
    return img
def crescent(d, cx, cy, r, bg):
    img=d._image
    m=Image.new("L",img.size,0); md=ImageDraw.Draw(m)
    md.ellipse([cx-r,cy-r,cx+r,cy+r], fill=255)
    o=r*0.38
    md.ellipse([cx-r+o,cy-r-o*0.35,cx+r+o,cy+r-o*0.35], fill=0)
    img.paste((217,180,74),(0,0),m)
S=4
# banner 320x180 (xhdpi)
W,H=320*S,180*S
img=gradient(W,H); d=ImageDraw.Draw(img)
bg_at=img.getpixel((60*S,90*S))
crescent(d, 62*S, 90*S, 40*S, bg_at)
d.text((118*S,58*S),"Salati", font=font(38*S), fill=(246,241,228))
d.text((118*S,100*S),"Time", font=font(30*S), fill=(217,180,74))
img.resize((320,180), Image.LANCZOS).save(f"{R}/drawable-xhdpi/banner.png")
# launcher icons
for folder,sz in [("mipmap-xhdpi",96),("mipmap-xxxhdpi",192)]:
    N=sz*S
    ic=gradient(N,N)
    mask=Image.new("L",(N,N),0); ImageDraw.Draw(mask).rounded_rectangle([0,0,N-1,N-1], radius=N//5, fill=255)
    d=ImageDraw.Draw(ic)
    crescent(d, N//2, N//2, int(N*0.32), ic.getpixel((N//2,N//2)))
    out=Image.new("RGBA",(N,N),(0,0,0,0)); out.paste(ic,(0,0),mask)
    out.resize((sz,sz), Image.LANCZOS).save(f"{R}/{folder}/ic_launcher.png")
