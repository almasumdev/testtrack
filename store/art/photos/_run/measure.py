"""Measure the green screen in each photo: box, height/width, cut at edges, other green blobs."""
import sys
import pathlib
import numpy as np
from PIL import Image
from scipy import ndimage

D = pathlib.Path(r'H:\android\onGoing\test_track\store\art\photos')
for f in sys.argv[1:] or sorted(p.name for p in D.glob('*.png')):
    if f == 'phone_template.png':
        continue
    im = Image.open(D / f).convert('RGB')
    a = np.asarray(im).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    m = (g > 120) & (g - r > 60) & (g - b > 60)
    lab, n = ndimage.label(m)
    if not n:
        print(f, im.size, 'NO GREEN'); continue
    sizes = ndimage.sum(m, lab, range(1, n + 1))
    k = 1 + int(np.argmax(sizes))
    ys, xs = np.nonzero(lab == k)
    y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
    W, H = im.size
    filled = ndimage.binary_fill_holes(lab == k)
    holes = filled.sum() - (lab == k).sum()
    others = sorted(sizes, reverse=True)[1:3]
    pure = a[lab == k].mean(0).round()
    print(f'{f} {W}x{H} box x{x0}-{x1} y{y0}-{y1} w{(x1-x0)/W:.0%} top{y0/H:.0%} bot{y1/H:.0%} '
          f'h/w {(y1-y0)/(x1-x0):.2f} holes {holes} mean {pure} others {[int(o) for o in others]}'
          + (' EDGE-CUT' if y1 >= H - 3 or x0 <= 2 or x1 >= W - 3 or y0 <= 2 else ''))
    # builder hint for portrait: scale 1.25 to 1920 tall, screen width and top in slide px
    if H > W:
        s = max(1080 / W, 1920 / H)
        print(f'   builder: phone_w ~{int((x1-x0)*s)} phone_top ~{int(y0*s)}')
