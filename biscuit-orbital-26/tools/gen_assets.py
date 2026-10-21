# Generates ALL original art for Biscuit Orbital (no external/copyrighted assets).
import numpy as np, math, random
from PIL import Image, ImageDraw, ImageFilter
OUT = "src/main/resources/assets/biscuit_orbital/textures"
rng = np.random.default_rng(7); random.seed(7)

def fbm(w, h, octaves=6, base=4, persistence=0.5, seed=0):
    r = np.random.default_rng(seed); total = np.zeros((h, w), np.float32); amp = 1.0; norm = 0
    for o in range(octaves):
        n = base * (2 ** o)
        small = r.random((n, max(2, int(n * h / w)))).astype(np.float32)
        img = Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        total += np.asarray(img, np.float32) / 255.0 * amp; norm += amp; amp *= persistence
    return total / norm

def save(img, name): img.save(f"{OUT}/{name}"); print("wrote", name, img.size)

# ---------- stars (transparent, tileable-ish) ----------
S = 1024; st = np.zeros((S, S, 4), np.float32)
for _ in range(900):
    x, y = rng.integers(0, S, 2); b = rng.random() ** 3 * 0.9 + 0.1
    tint = rng.choice([[1, 1, 1], [0.75, 0.85, 1], [1, 0.9, 0.75], [1, 0.8, 0.8]])
    st[y, x] = [*(np.array(tint) * b), min(1, b * 1.2)]
for _ in range(70):
    x, y = rng.integers(2, S - 2, 2); b = 0.9
    for dx, dy, f in [(0, 0, 1), (1, 0, .5), (-1, 0, .5), (0, 1, .5), (0, -1, .5)]:
        st[y + dy, x + dx] = [b * f, b * f, b * f, f]
save(Image.fromarray((np.clip(st, 0, 1) * 255).astype(np.uint8), "RGBA"), "gui/stars.png")

# ---------- earth ----------
E = 512; cx = cy = E / 2; R = 236
yy, xx = np.mgrid[0:E, 0:E].astype(np.float32); nx = (xx - cx) / R; ny = (yy - cy) / R
r2 = nx * nx + ny * ny; inside = r2 <= 1.0; nz = np.sqrt(np.clip(1 - r2, 0, 1))
lon = np.arctan2(nx, nz); lat = np.arcsin(np.clip(-ny, -1, 1))
elev_map = fbm(1024, 512, 7, 5, 0.6, seed=11)
cloud_map = fbm(1024, 512, 6, 9, 0.6, seed=23)
u = ((lon / (2 * math.pi) + 0.5 + 0.15) % 1.0) * 1023; v = (0.5 - lat / math.pi) * 511
ui, vi = u.astype(int) % 1024, np.clip(v.astype(int), 0, 511)
el = elev_map[vi, ui]; cl = cloud_map[vi, ui]
thr = np.quantile(elev_map, 0.60); land = el > thr; absl = np.abs(lat) / (math.pi / 2)
col = np.zeros((E, E, 3), np.float32)
deep = np.array([0.02, 0.08, 0.28]); shallow = np.array([0.05, 0.28, 0.55])
oc = deep + (shallow - deep) * np.clip((el / thr), 0, 1)[..., None] ** 6
green = np.array([0.10, 0.36, 0.12]); tan = np.array([0.55, 0.45, 0.25]); snow = np.array([0.95, 0.97, 1.0])
hl = np.clip((el - thr) / 0.12, 0, 1)[..., None]
ld = green + (tan - green) * hl
ld = ld + (snow - ld) * np.clip((absl - 0.72) / 0.15, 0, 1)[..., None]
col = np.where(land[..., None], ld, oc)
col = np.where((absl > 0.88)[..., None], snow, col)
cthr = np.quantile(cloud_map, 0.66); cloud = np.clip((cl - cthr) * 9.0, 0, 1)[..., None] * 0.8
col = col * (1 - cloud) + cloud
L = np.array([-0.6, -0.45, 0.65]); L /= np.linalg.norm(L)
lam = np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0, 1)
shade = 0.03 + 0.97 * (lam ** 0.8)
col = col * shade[..., None]
spec = (np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0, 1) ** 40) * (~land) * 0.35
col += spec[..., None]
rim = np.clip(1 - nz, 0, 1) ** 3
atm = np.array([0.35, 0.6, 1.0])
col = col + atm * rim[..., None] * (0.25 + 0.75 * lam[..., None]) * 0.9
alpha = inside.astype(np.float32)
d = np.sqrt(r2); halo = np.clip(1 - (d - 1) / 0.09, 0, 1) * (d > 1) * (0.35 + 0.65 * np.clip(-(nx * L[0] + ny * L[1]) , 0, 1) ** 0.3) * 0.7
for c in range(3): col[..., c] = np.where(inside, col[..., c], atm[c])
alpha = np.maximum(alpha, halo)
save(Image.fromarray((np.dstack([np.clip(col, 0, 1), alpha]) * 255).astype(np.uint8), "RGBA"), "gui/earth.png")

# ---------- galaxy (tilted spiral) ----------
G = 1024; gy, gx = np.mgrid[0:G, 0:G].astype(np.float32); gx = (gx - G / 2) / (G / 2); gy = (gy - G / 2) / (G / 2)
ang = math.radians(-28); rx = gx * math.cos(ang) - gy * math.sin(ang); ry = (gx * math.sin(ang) + gy * math.cos(ang)) / 0.52
rad = np.sqrt(rx * rx + ry * ry) + 1e-4; th = np.arctan2(ry, rx)
arm = 0.5 + 0.5 * np.cos(2 * (th - 2.4 * np.log(rad + 0.06)))
nz1 = fbm(G, G, 6, 6, 0.55, seed=5); nz2 = fbm(G, G, 5, 14, 0.6, seed=9)
disk = np.exp(-rad / 0.5) * (0.12 + 0.95 * arm ** 2.4) * (0.5 + 1.1 * nz1) * 1.5
bulge = np.exp(-(rad / 0.12) ** 2) * 1.6
dens = np.clip((disk + bulge) * np.clip(1.15 - rad, 0, 1), 0, 2)
dust = np.clip((nz2 - 0.5) * 2.2, 0, 1) * np.exp(-rad / 0.4) * arm
dens = np.clip(dens - dust * 0.35, 0, 2)
blue = np.array([0.55, 0.7, 1.0]); warm = np.array([1.0, 0.9, 0.65]); pink = np.array([1.0, 0.45, 0.6])
mix = np.clip(rad / 0.6, 0, 1)[..., None]
gc = warm * (1 - mix) + blue * mix
hii = (np.clip((nz2 - 0.62) * 5, 0, 1) * arm * (rad > 0.12))[..., None]
gc = gc * (1 - hii * 0.6) + pink * hii * 0.6
gc = gc * (0.7 + 0.5 * dens[..., None]) + np.clip(dens - 1.0, 0, 1)[..., None] * 0.8
ga = np.clip(dens * 1.25, 0, 1) ** 0.8
tiny = rng.random((G, G)) > 0.9985; ga = np.maximum(ga, tiny * 0.8 * (rad < 0.9))
save(Image.fromarray((np.dstack([np.clip(gc, 0, 1), ga]) * 255).astype(np.uint8), "RGBA"), "gui/galaxy.png")

# ---------- glows ----------
def glow(name, rgb, size=256, power=2.2):
    g = np.mgrid[0:size, 0:size].astype(np.float32); d = np.sqrt(((g[1] - size / 2) / (size / 2)) ** 2 + ((g[0] - size / 2) / (size / 2)) ** 2)
    a = np.clip(1 - d, 0, 1) ** power
    arr = np.dstack([np.full((size, size), rgb[0]), np.full((size, size), rgb[1]), np.full((size, size), rgb[2]), a])
    save(Image.fromarray((arr * 255).astype(np.uint8), "RGBA"), name)
glow("gui/glow_red.png", (1, 0.12, 0.2)); glow("gui/glow_white.png", (1, 1, 1)); glow("gui/glow_gold.png", (1, 0.8, 0.35), power=1.6)

# ---------- station (original design) ----------
W, H = 768, 384; im = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
def grad_rect(box, c1, c2, vertical=True):
    x0, y0, x1, y1 = box
    for i in range((y1 - y0) if vertical else (x1 - x0)):
        t = i / max(1, ((y1 - y0) if vertical else (x1 - x0)) - 1); c = tuple(int(c1[k] + (c2[k] - c1[k]) * t) for k in range(3)) + (255,)
        if vertical: d.line([(x0, y0 + i), (x1, y0 + i)], fill=c)
        else: d.line([(x0 + i, y0), (x0 + i, y1)], fill=c)
# solar wings
for wy, flip in [(40, 1), (250, -1)]:
    pts = [(250, 190), (330, wy + (0 if flip == 1 else 90)), (520, wy + (20 if flip == 1 else 70)), (470, 190)]
    d.polygon(pts, fill=(20, 28, 52, 255))
    for k in range(1, 9):
        t = k / 9; x = 250 + (330 - 250) * t; y2 = 190 + ((wy + (0 if flip == 1 else 90)) - 190) * t
        d.line([(250 + (470 - 250) * t, 190), (330 + (520 - 330) * t, wy + (20 if flip == 1 else 70))], fill=(70, 95, 150, 255), width=1)
    d.line([pts[1], pts[2]], fill=(140, 160, 210, 255), width=2)
# main truss
grad_rect((120, 176, 560, 204), (96, 100, 110), (40, 42, 50))
for x in range(130, 560, 22): d.line([(x, 176), (x, 204)], fill=(25, 27, 32, 255))
# command module
grad_rect((150, 140, 270, 240), (120, 124, 134), (46, 48, 58), True)
d.rectangle([150, 140, 270, 240], outline=(200, 205, 215, 255), width=2)
for k in range(6): d.rectangle([162 + k * 18, 160, 172 + k * 18, 170], fill=(255, 210, 120, 255))
# railgun barrel
grad_rect((540, 170, 760, 210), (170, 175, 185), (50, 52, 60), True)
for x in range(560, 750, 26):
    grad_rect((x, 160, x + 12, 220), (255, 90, 110), (150, 10, 30), True)
d.ellipse([748, 175, 766, 205], fill=(255, 140, 160, 255))
# antenna + details
d.line([(180, 140), (180, 70)], fill=(210, 215, 225, 255), width=3); d.ellipse([170, 56, 190, 76], outline=(255, 120, 140, 255), width=3)
d.rectangle([300, 150, 330, 176], fill=(70, 74, 84, 255)); d.rectangle([420, 204, 460, 232], fill=(70, 74, 84, 255))
im = im.filter(ImageFilter.GaussianBlur(0.6)); save(im, "gui/station.png")

# ---------- card thumbnail ----------
T = Image.new("RGBA", (128, 80)); td = ImageDraw.Draw(T)
for y in range(80): td.line([(0, y), (128, y)], fill=(8 + y // 8, 6 + y // 14, 28 + y // 3, 255))
for _ in range(60): td.point((random.randint(0, 127), random.randint(0, 79)), fill=(255, 255, 255, 255))
td.line([(10, 70), (118, 14)], fill=(255, 40, 80, 255), width=3); td.line([(10, 70), (118, 14)], fill=(255, 190, 200, 255), width=1)
td.ellipse([84, 36, 118, 70], fill=(40, 110, 200, 255)); td.ellipse([92, 42, 112, 62], fill=(60, 160, 90, 255))
save(T, "gui/thumb.png")

# ---------- remote item (32x32 pixel art, original) ----------
P = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); pd = ImageDraw.Draw(P)
pd.rectangle([22, 1, 23, 9], fill=(60, 60, 66, 255)); pd.rectangle([21, 0, 24, 1], fill=(200, 40, 50, 255))
pd.rectangle([9, 8, 26, 30], fill=(34, 34, 40, 255)); pd.rectangle([10, 9, 25, 29], fill=(52, 54, 62, 255))
pd.rectangle([12, 11, 23, 17], fill=(20, 8, 12, 255)); pd.rectangle([13, 12, 22, 16], fill=(180, 24, 36, 255)); pd.line([(14, 13), (21, 13)], fill=(255, 120, 130, 255))
for r in range(3):
    for c in range(3): pd.rectangle([12 + c * 4, 20 + r * 3, 14 + c * 4, 21 + r * 3], fill=(150, 154, 166, 255))
pd.rectangle([5, 14, 10, 28], fill=(240, 200, 30, 255))
for k in range(0, 15, 4): pd.polygon([(5, 14 + k), (10, 14 + k), (10, 16 + k), (5, 18 + k)], fill=(30, 30, 30, 255))
pd.line([(9, 8), (26, 8)], fill=(120, 124, 136, 255)); pd.line([(9, 8), (9, 30)], fill=(120, 124, 136, 255))
save(P, "item/uplink_remote.png")

# ---------- mod icon ----------
ic = Image.new("RGBA", (128, 128), (8, 6, 26, 255)); idr = ImageDraw.Draw(ic)
for _ in range(80): idr.point((random.randint(0, 127), random.randint(0, 127)), fill=(255, 255, 255, 255))
idr.line([(10, 118), (118, 10)], fill=(255, 40, 80, 255), width=5); idr.ellipse([60, 60, 120, 120], fill=(40, 110, 200, 255)); idr.ellipse([76, 74, 108, 106], fill=(60, 160, 90, 255))
ic.save("src/main/resources/assets/biscuit_orbital/icon.png"); print("wrote icon.png")
