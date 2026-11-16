from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
BG = '#080C16'
CYAN = '#36E6D0'
PINK = '#FF5CA8'
WHITE = '#F1F5FF'


def symbol(canvas, box):
    left, top, size = box
    draw = ImageDraw.Draw(canvas)

    def poly(points, color):
        draw.polygon([(left + x * size / 108, top + y * size / 108) for x, y in points], fill=color)

    poly([(34,24),(74,24),(84,34),(84,74),(74,84),(34,84),(24,74),(24,34)], '#13212E')
    poly([(34,24),(74,24),(84,34),(84,48),(80,48),(80,36),(72,28),(36,28),(28,36),(28,72),(36,80),(48,80),(48,84),(34,84),(24,74),(24,34)], CYAN)
    poly([(80,56),(84,56),(84,74),(74,84),(56,84),(56,80),(72,80),(80,72)], PINK)
    poly([(36,36),(60,36),(60,42),(66,42),(66,54),(60,54),(60,60),(48,60),(48,72),(36,72)], CYAN)
    poly([(48,42),(58,42),(58,54),(48,54)], '#13212E')
    poly([(52,60),(60,60),(60,66),(66,66),(66,72),(54,72),(54,66),(48,66),(48,60)], WHITE)
    poly([(70,48),(74,48),(74,52),(70,52)], PINK)


def icon(size, rounded=False):
    scale = 4
    image = Image.new('RGBA', (size * scale, size * scale), BG)
    symbol(image, (0, 0, size * scale))
    if rounded:
        mask = Image.new('L', image.size, 0)
        ImageDraw.Draw(mask).ellipse((0, 0, image.width - 1, image.height - 1), fill=255)
        image.putalpha(mask)
    return image.resize((size, size), Image.Resampling.LANCZOS)


for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    icon(size).save(RES / f'mipmap-{density}/ic_launcher.png')
    icon(size, True).save(RES / f'mipmap-{density}/ic_launcher_round.png')

icon(1024).save(ROOT / 'art/app-icon-source.png')
icon(512).convert('RGB').save(ROOT / 'icon-playstore-512.png')

banner = Image.new('RGB', (1280,720), BG)
draw = ImageDraw.Draw(banner)
for x in range(0, 1280, 32):
    draw.line((x, 0, x, 720), fill='#101725', width=1)
for y in range(0, 720, 32):
    draw.line((0, y, 1280, y), fill='#101725', width=1)
symbol(banner, (-30, 35, 650))
font = ImageFont.truetype(str(RES / 'font/press_start_2p.ttf'), 64)
draw.text((570, 292), 'RETRO', font=font, fill=WHITE)
draw.text((570, 380), 'BIT', font=font, fill=CYAN)
draw.rectangle((577, 487, 1067, 492), fill=PINK)
banner.save(ROOT / 'art/tv-banner-source.png')
banner.resize((320,180), Image.Resampling.LANCZOS).save(RES / 'drawable-xhdpi/tv_banner.png')

feature = Image.new('RGB', (1024, 500), BG)
draw = ImageDraw.Draw(feature)
for x in range(0, 1024, 32):
    draw.line((x, 0, x, 500), fill='#101725', width=1)
for y in range(0, 500, 32):
    draw.line((0, y, 1024, y), fill='#101725', width=1)
symbol(feature, (50, 20, 460))
feature_font = ImageFont.truetype(str(RES / 'font/press_start_2p.ttf'), 60)
draw.text((480, 156), 'RETRO', font=feature_font, fill=WHITE)
draw.text((480, 238), 'BIT', font=feature_font, fill=CYAN)
draw.rectangle((486, 330, 900, 336), fill=PINK)
feature.save(ROOT / 'art/feature-graphic-1024x500.png')
print('Generated original icon densities, 512px artwork, TV banner and feature graphic.')
