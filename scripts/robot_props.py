"""Cartoon props the robot holds, drawn in the robot's own 880x980 space (raw coords 185..1065, 110..1090).

Writes res/drawable/robot_prop_*.xml and build/robot_props/preview.html, each prop in the robot's hands
(open it in a browser). Run: python scripts/robot_props.py
"""
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app/src/main/res/drawable/')
OUT_DIR = os.path.join(ROOT, 'build/robot_props')

INK = '#17405E'      # cartoon outline
SW = 13              # outline width
NAVY = ('#245F84', '#174866')
CORAL = ('#F99B80', '#EF806B')
GOLD = ('#FFD96B', '#F2A92E')
GOLD_D = '#E0922A'
WOOD = ('#C98A55', '#9C6236')
SKY = ('#EAF5FC', '#CFE5F4')
WHITE = ('#FFFFFF', '#E3ECF3')
GREEN = ('#5ACB8E', '#2E9A66')
SILVER = ('#E6EDF3', '#AFC0CE')
RED = ('#F27B6A', '#D34C3E')


# ---------------------------------------------------------------- shapes

def f(v):
    return ('%.1f' % v).rstrip('0').rstrip('.')


def rr(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return (f'M{f(x + r)} {f(y)} H{f(x + w - r)} A{f(r)} {f(r)} 0 0 1 {f(x + w)} {f(y + r)} '
            f'V{f(y + h - r)} A{f(r)} {f(r)} 0 0 1 {f(x + w - r)} {f(y + h)} H{f(x + r)} '
            f'A{f(r)} {f(r)} 0 0 1 {f(x)} {f(y + h - r)} V{f(y + r)} A{f(r)} {f(r)} 0 0 1 {f(x + r)} {f(y)} Z')


def circle(cx, cy, r):
    return f'M{f(cx - r)} {f(cy)} A{f(r)} {f(r)} 0 1 0 {f(cx + r)} {f(cy)} A{f(r)} {f(r)} 0 1 0 {f(cx - r)} {f(cy)} Z'


def ellipse(cx, cy, rx, ry):
    return f'M{f(cx - rx)} {f(cy)} A{f(rx)} {f(ry)} 0 1 0 {f(cx + rx)} {f(cy)} A{f(rx)} {f(ry)} 0 1 0 {f(cx - rx)} {f(cy)} Z'


def poly(*pts):
    return 'M' + ' L'.join(f'{f(x)} {f(y)}' for x, y in pts) + ' Z'


def line(*pts):
    return 'M' + ' L'.join(f'{f(x)} {f(y)}' for x, y in pts)


def gear(cx, cy, ro, ri, n, phase=0.0):
    p = 2 * math.pi / n
    pts = []
    for i in range(n):
        a = i * p + phase
        for frac, r in ((0.0, ri), (0.22, ri), (0.42, ro), (0.78, ro), (0.98, ri)):
            pts.append((cx + r * math.cos(a + frac * p), cy + r * math.sin(a + frac * p)))
    return poly(*pts)


def sparkle(cx, cy, r):
    k = r * 0.22
    return (f'M{f(cx)} {f(cy - r)} Q{f(cx + k)} {f(cy - k)} {f(cx + r)} {f(cy)} '
            f'Q{f(cx + k)} {f(cy + k)} {f(cx)} {f(cy + r)} Q{f(cx - k)} {f(cy + k)} {f(cx - r)} {f(cy)} '
            f'Q{f(cx - k)} {f(cy - k)} {f(cx)} {f(cy - r)} Z')


def star5(cx, cy, ro, ri):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = ro if i % 2 == 0 else ri
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return poly(*pts)


def scallop(cx, cy, r_in, r_out, n):
    p = 2 * math.pi / n
    d = f'M{f(cx + r_in)} {f(cy)}'
    for i in range(n):
        a1, am, a2 = i * p, (i + 0.5) * p, (i + 1) * p
        d += (f' Q{f(cx + r_out * math.cos(am))} {f(cy + r_out * math.sin(am))} '
              f'{f(cx + r_in * math.cos(a2))} {f(cy + r_in * math.sin(a2))}')
    return d + ' Z'


def arc(cx, cy, r, a1, a2):
    x1, y1 = cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1))
    x2, y2 = cx + r * math.cos(math.radians(a2)), cy + r * math.sin(math.radians(a2))
    return f'M{f(x1)} {f(y1)} A{f(r)} {f(r)} 0 0 1 {f(x2)} {f(y2)}'


# ---------------------------------------------------------------- items

class Path:
    def __init__(self, d, fill=None, stroke=INK, sw=SW, alpha=1.0, even=False, box=None):
        self.d, self.fill, self.stroke, self.sw, self.alpha, self.even, self.box = d, fill, stroke, sw, alpha, even, box


class Group:
    def __init__(self, items, rotate=0, px=0, py=0, dx=0, dy=0, scale=1):
        self.items, self.rotate, self.px, self.py, self.dx, self.dy, self.scale = items, rotate, px, py, dx, dy, scale


def shape(d, colors, box, stroke=INK, sw=SW, even=False):
    """A filled, outlined shape; a pair of colours is a top-left to bottom-right gradient over box."""
    return Path(d, fill=colors, stroke=stroke, sw=sw, even=even, box=box)


def flat(d, color, alpha=1.0, even=False):
    return Path(d, fill=color, stroke=None, alpha=alpha, even=even)


def ink(d, color=INK, w=SW, alpha=1.0):
    return Path(d, fill=None, stroke=color, sw=w, alpha=alpha)


def tube(d, color, w):
    """A thick line with a cartoon outline."""
    return [ink(d, INK, w + 2 * SW), ink(d, color, w)]


def shine(d, w=14, alpha=0.75):
    return ink(d, '#FFFFFF', w, alpha)


# ---------------------------------------------------------------- props

FACE = '#FFFCF1'
DARK = '#17253B'


def music():
    """Feedback, nobody asking: headphones on, grooving to a cassette. Face and headphones go on the head."""
    face = [
        ink('M428 548 Q481 488 534 548', DARK, 24),
        ink('M719 548 Q772 488 825 548', DARK, 24),
        flat(rr(552, 578, 150, 56, 20), FACE),
        shape('M578 588 Q627 652 676 588 Z', (DARK, DARK), (578, 588, 676, 642), sw=6),
        flat('M598 616 Q627 642 656 616 Q627 604 598 616 Z', '#F27B6A'),
        flat(ellipse(404, 604, 36, 19), '#F9A99A', 0.65),
        flat(ellipse(850, 604, 36, 19), '#F9A99A', 0.65),
    ]
    phones = [
        *tube('M240 500 C240 150 1014 150 1014 500', CORAL[1], 30),
        shine('M300 330 C360 250 450 214 540 206', 9, 0.6),
        shape(rr(194, 436, 92, 190, 40), CORAL, (194, 436, 286, 626)),
        shape(rr(968, 436, 92, 190, 40), CORAL, (968, 436, 1060, 626)),
        flat(rr(264, 470, 22, 122, 10), '#1D4E6E'),
        flat(rr(968, 470, 22, 122, 10), '#1D4E6E'),
        shine('M216 470 V540', 10, 0.7),
        shine('M990 470 V540', 10, 0.7),
    ]
    tape = [
        shape(rr(424, 846, 406, 200, 30), GOLD, (424, 846, 830, 1046)),
        flat(rr(452, 866, 350, 34, 12), CORAL[1]),
        flat(rr(476, 878, 120, 10, 5), '#FFFFFF', 0.8),
        shape(rr(482, 912, 290, 82, 22), ('#2C4B63', '#1A3346'), (482, 912, 772, 994), sw=9),
        flat(rr(560, 966, 134, 14, 7), '#7A4A28'),
        shape(poly((500, 1046), (530, 1006), (724, 1006), (754, 1046)), ('#F6BF45', GOLD_D), (500, 1006, 754, 1046), sw=9),
        *(flat(circle(x, y, 7), '#B7791F') for x, y in ((446, 868), (808, 868), (446, 1024), (808, 1024))),
        shine('M444 900 V1000', 10, 0.5),
    ]

    def reel(cx):
        spokes = ' '.join(
            f'M{f(cx)} {f(953)} L{f(cx + 24 * math.cos(math.radians(a)))} {f(953 + 24 * math.sin(math.radians(a)))}'
            for a in (0, 60, 120, 180, 240, 300))
        return [shape(circle(cx, 953, 33), WHITE, (cx - 33, 920, cx + 33, 986), sw=8), ink(spokes, '#7F92A3', 7),
                flat(circle(cx, 953, 9), '#1A3346')]

    def note(x, y, color, size=1.0):
        s = size
        return [flat(ellipse(x, y, 21 * s, 16 * s), color),
                ink(f'M{f(x + 18 * s)} {f(y)} V{f(y - 74 * s)} Q{f(x + 52 * s)} {f(y - 60 * s)} {f(x + 48 * s)} {f(y - 28 * s)}', color, 10 * s)]

    return {
        'music_face': face,
        'music_headphones': phones,
        'music_tape': tape,
        'music_reel_left': reel(562),
        'music_reel_right': reel(692),
        'music_note1': note(236, 380, CORAL[1]),
        'music_note2': note(980, 330, '#1D5C82', 1.1),
        'music_note3': note(260, 230, '#1D5C82', 0.85),
        'music_note4': note(960, 196, CORAL[1], 0.9),
    }


def trophy():
    body = [
        *tube('M496 792 C404 790 402 896 530 890', GOLD_D, 24),
        *tube('M758 792 C850 790 852 896 724 890', GOLD_D, 24),
        shape(rr(508, 990, 238, 54, 14), WOOD, (508, 990, 746, 1044)),
        flat(rr(574, 1004, 106, 26, 7), '#FFE7A3'),
        shape('M598 912 L656 912 L672 992 L582 992 Z', GOLD, (582, 912, 672, 992)),
        shape(ellipse(627, 946, 44, 15), GOLD, (583, 931, 671, 961)),
        shape('M487 762 L767 762 Q772 882 690 918 Q627 944 564 918 Q482 882 487 762 Z', GOLD, (487, 762, 767, 944)),
        shape(rr(472, 742, 310, 36, 18), ('#FFE79A', '#F6BF45'), (472, 742, 782, 778)),
        shape(star5(627, 840, 46, 20), ('#FFFDF2', '#FFE9B0'), (580, 794, 674, 886), sw=9),
        shine('M522 796 Q524 862 566 892'),
    ]
    return {
        'trophy': body,
        'trophy_spark1': [flat(sparkle(432, 726, 36), '#FFD96B'), flat(sparkle(432, 726, 12), '#FFFFFF')],
        'trophy_spark2': [flat(sparkle(838, 716, 28), '#FFD96B'), flat(sparkle(452, 1010, 22), '#FFD96B')],
    }


def phone():
    body = [
        shape(rr(356, 786, 542, 258, 44), ('#3A7BA3', '#1D4E6E'), (356, 786, 898, 1044)),
        shape(rr(392, 812, 470, 206, 22), SKY, (392, 812, 862, 1018), sw=8),
        flat(circle(374, 915, 8), '#0F3550'),
        flat('M560 812 L640 812 L540 1018 L460 1018 Z', '#FFFFFF', 0.35),
    ]
    for x in (432, 572):
        body.append(shape(rr(x, 860, 112, 112, 28), ('#F4F9FD', '#DCEAF4'), (x, 860, x + 112, 972), stroke='#9DC2DB', sw=7))
    plus = [
        shape(rr(712, 860, 112, 112, 28), CORAL, (712, 860, 824, 972), sw=9),
        ink('M768 888 V944 M740 916 H796', '#FFFFFF', 15),
    ]
    return {'phone': body, 'phone_plus': plus}


def key():
    body = [
        shape(rr(540, 884, 344, 42, 21), GOLD, (540, 884, 884, 926)),
        shape('M774 915 L774 982 L812 982 L812 950 L838 950 L838 982 L874 982 L874 915 Z', GOLD, (774, 915, 874, 982)),
        shape(circle(468, 905, 92) + ' ' + circle(468, 905, 36), GOLD, (376, 813, 560, 997), even=True),
        ink(circle(468, 905, 64), GOLD_D, 8, 0.6),
        shine(arc(468, 905, 74, 200, 260), 13, 0.85),
        shine('M600 896 H760', 10, 0.7),
    ]
    return {'key': body, 'key_spark': [flat(sparkle(712, 820, 34), '#FFD96B'), flat(sparkle(712, 820, 11), '#FFFFFF')]}


def clipboard():
    body = [
        shape(rr(372, 758, 510, 304, 30), WOOD, (372, 758, 882, 1062)),
        shape(rr(410, 790, 434, 246, 14), WHITE, (410, 790, 844, 1036), sw=8),
        shape(rr(555, 732, 144, 62, 18), SILVER, (555, 732, 699, 794)),
        flat(rr(595, 746, 64, 16, 8), '#7F92A3'),
    ]
    for i, y in enumerate((848, 912, 976)):
        body.append(shape(rr(442, y - 22, 44, 44, 11), ('#FFFFFF', '#F1F6FA'), (442, y - 22, 486, y + 22), stroke='#8FB3CC', sw=7))
        body.append(flat(rr(510, y - 9, 250 - i * 60, 18, 9), '#D3E3EE'))
    # A pencil resting on the page, drawn flat and turned to lie across it.
    pencil = [
        shape(poly((640, 1000), (684, 983), (684, 1017)), ('#F6D7AE', '#E2B47E'), (640, 983, 684, 1017), sw=9),
        flat(poly((640, 1000), (655, 994), (655, 1006)), '#30475A'),
        shape(rr(684, 983, 140, 34, 4), GOLD, (684, 983, 824, 1017), sw=9),
        shape(rr(824, 983, 22, 34, 3), SILVER, (824, 983, 846, 1017), sw=9),
        shape(rr(846, 983, 30, 34, 10), CORAL, (846, 983, 876, 1017), sw=9),
    ]
    return {'clipboard': body, 'clipboard_pencil': [Group(pencil, rotate=-38, px=640, py=1000)]}


def bell():
    body = [
        shape(circle(627, 734, 22), GOLD, (605, 712, 649, 756)),
        shape(circle(627, 1022, 27), (GOLD_D, '#C87A1E'), (600, 995, 654, 1049)),
        shape('M627 752 C540 752 516 812 511 880 C506 930 482 950 452 972 L802 972 C772 950 748 930 743 880 '
              'C738 812 714 752 627 752 Z', GOLD, (452, 752, 802, 972)),
        shape(rr(432, 962, 390, 40, 20), ('#FFD25A', GOLD_D), (432, 962, 822, 1002)),
        ink('M516 884 Q627 906 738 884', GOLD_D, 12),
        shine('M562 800 Q540 860 546 930', 16),
    ]
    rings = [ink(d, CORAL[1], 15) for d in (
        'M456 792 Q430 836 442 882', 'M412 768 Q376 836 394 904',
        'M798 792 Q824 836 812 882', 'M842 768 Q878 836 860 904')]
    return {'bell': body, 'bell_rings': rings}


def box():
    body = [
        shape('M436 822 L470 724 L784 724 L818 822 Z', ('#E7B47A', '#C98B4E'), (436, 724, 818, 822)),
        shape('M398 838 L312 766 L346 728 L436 822 Z', ('#E7B47A', '#C98B4E'), (312, 728, 436, 838)),
        shape('M856 838 L942 766 L908 728 L818 822 Z', ('#E7B47A', '#C98B4E'), (818, 728, 942, 838)),
        shape('M398 818 L856 818 L874 856 L380 856 Z', ('#7A4C2A', '#5A341B'), (380, 818, 874, 856)),
        shape('M380 852 L874 852 L858 1062 L396 1062 Z', ('#EDB679', '#C98B4E'), (380, 852, 874, 1062)),
        flat(rr(598, 852, 58, 84, 4), '#F6DDB4', 0.85),
        shape(rr(452, 962, 132, 64, 12), WHITE, (452, 962, 584, 1026), sw=7),
        flat(rr(472, 980, 86, 10, 5), '#C9D6E0'),
        flat(rr(472, 1000, 56, 10, 5), '#C9D6E0'),
    ]
    motes = [flat(circle(x, y, r), '#BFD8E8', 0.95) for x, y, r in ((586, 786, 11), (662, 752, 8), (712, 792, 13), (540, 742, 7))]
    return {'box': body, 'box_motes': motes}


def plug():
    left = [
        *tube('M402 940 C440 946 452 906 496 906', '#DDE7EF', 24),
        shape(rr(486, 860, 98, 92, 24), WHITE, (486, 860, 584, 952)),
        shape(rr(584, 876, 52, 16, 7), SILVER, (584, 876, 636, 892), sw=8),
        shape(rr(584, 920, 52, 16, 7), SILVER, (584, 920, 636, 936), sw=8),
        shine('M506 878 V928', 10, 0.8),
    ]
    right = [
        *tube('M852 940 C814 946 802 906 758 906', '#DDE7EF', 24),
        shape(rr(668, 852, 98, 108, 26), CORAL, (668, 852, 766, 960)),
        flat(rr(686, 876, 22, 16, 7), '#5A2A20'),
        flat(rr(686, 920, 22, 16, 7), '#5A2A20'),
    ]
    spark = [
        ink('M664 784 L642 826 L668 826 L646 868', '#FFC23D', 13),
        flat(circle(612, 806, 7), '#FFC23D'),
        flat(circle(690, 846, 6), '#FFC23D'),
    ]
    big = dict(scale=1.3, px=627, py=906)
    return {'plug_left': [Group(left, **big)], 'plug_right': [Group(right, **big)], 'plug_spark': [Group(spark, **big)]}


def gears():
    big = [
        shape(gear(545, 902, 138, 112, 12) + ' ' + circle(545, 902, 22), GOLD, (407, 764, 683, 1040), even=True),
        ink(circle(545, 902, 70), GOLD_D, 10, 0.7),
        shine(arc(545, 902, 92, 195, 250), 13, 0.8),
    ]
    small = [
        shape(gear(715, 797, 90, 70, 8, phase=math.pi / 8) + ' ' + circle(715, 797, 18), ('#A8D8F2', '#5E9DC5'),
              (625, 707, 805, 887), even=True),
        ink(circle(715, 797, 42), '#4C8DB7', 9, 0.7),
    ]
    return {'gear_big': big, 'gear_small': small}


def hourglass():
    body = [
        shape(rr(538, 768, 18, 260, 9), WOOD, (538, 768, 556, 1028), sw=9),
        shape(rr(698, 768, 18, 260, 9), WOOD, (698, 768, 716, 1028), sw=9),
        shape('M566 772 C566 850 608 872 614 898 C608 924 566 946 566 1024 L688 1024 C688 946 646 924 640 898 '
              'C646 872 688 850 688 772 Z', ('#F4FBFF', '#D3EAF7'), (566, 772, 688, 1024), sw=10),
        flat('M584 836 C598 866 614 882 627 892 C640 882 656 866 670 836 Z', '#F5C062'),
        flat('M574 1020 C590 980 614 968 627 968 C640 968 664 980 680 1020 Z', '#F5C062'),
        flat(rr(624, 892, 6, 80, 3), '#F5C062'),
        shine('M582 790 C584 830 596 852 606 866', 9, 0.9),
        shape(rr(508, 734, 238, 40, 16), WOOD, (508, 734, 746, 774)),
        shape(rr(508, 1022, 238, 40, 16), WOOD, (508, 1022, 746, 1062)),
    ]
    return {'hourglass': [Group(body, scale=1.22, px=627, py=898, dy=-26)]}


def seal():
    body = [
        shape('M566 930 L528 1062 L572 1040 L600 1072 L632 950 Z', ('#3FA874', '#24784F'), (528, 930, 632, 1072)),
        shape('M688 930 L726 1062 L682 1040 L654 1072 L622 950 Z', ('#3FA874', '#24784F'), (622, 930, 726, 1072)),
        shape(scallop(627, 870, 122, 148, 18), GREEN, (480, 722, 774, 1018)),
        ink(circle(627, 870, 96), '#E8FFF2', 8, 0.6),
        ink('M574 872 L612 910 L684 832', '#FFFFFF', 32),
        shine(arc(627, 870, 110, 200, 245), 12, 0.6),
    ]
    return {'seal': body, 'seal_spark1': [flat(sparkle(442, 760, 34), '#FFD96B')],
            'seal_spark2': [flat(sparkle(820, 742, 26), '#FFD96B'), flat(sparkle(836, 1010, 20), '#FFD96B')]}


def scales():
    stand = [
        shape('M540 1046 L714 1046 L686 1006 L568 1006 Z', GOLD, (540, 1006, 714, 1046)),
        shape(rr(615, 772, 24, 240, 10), GOLD, (615, 772, 639, 1012), sw=10),
    ]
    beam = [
        shape(rr(430, 774, 394, 24, 12), GOLD, (430, 774, 824, 798), sw=10),
        shape(circle(627, 772, 22), ('#FFE79A', GOLD_D), (605, 750, 649, 794), sw=10),
    ]

    def pan(cx):
        return [
            ink(line((cx - 50, 908), (cx, 786), (cx + 50, 908)), '#B07C24', 7),
            shape(f'M{cx - 72} 904 L{cx + 72} 904 Q{cx + 62} 958 {cx} 958 Q{cx - 62} 958 {cx - 72} 904 Z', GOLD,
                  (cx - 72, 904, cx + 72, 958), sw=10),
        ]
    return {'scales_stand': stand, 'scales_beam': beam, 'scales_pan_left': pan(440), 'scales_pan_right': pan(814)}


def wallet():
    body = [
        shape(rr(420, 800, 414, 232, 34), ('#A8693C', '#7A4A28'), (420, 800, 834, 1032)),
        flat(rr(446, 820, 362, 64, 18), '#4E2E18'),
        shape(rr(420, 872, 414, 160, 30), ('#C58450', '#9C6236'), (420, 872, 834, 1032)),
        ink(rr(440, 890, 374, 124, 20), '#E2AE7C', 5, 0.7),
        shape(rr(752, 912, 82, 62, 20), ('#9C6236', '#7A4A28'), (752, 912, 834, 974)),
        shape(circle(796, 943, 12), GOLD, (784, 931, 808, 955), sw=7),
    ]
    moth = [
        shape('M624 762 C588 722 556 736 570 770 C580 794 608 784 624 772 Z', ('#E8EDF1', '#B9C5CF'), (556, 722, 624, 794), sw=8),
        shape('M630 762 C666 722 698 736 684 770 C674 794 646 784 630 772 Z', ('#E8EDF1', '#B9C5CF'), (630, 722, 698, 794), sw=8),
        shape(ellipse(627, 768, 10, 24), ('#8E9AA6', '#6B7884'), (617, 744, 637, 792), sw=7),
        ink('M622 746 Q612 726 600 722 M632 746 Q642 726 654 722', INK, 6),
    ]
    return {'wallet': body, 'wallet_moth': moth}


def flag():
    pole = [
        shape(rr(466, 708, 26, 356, 13), WOOD, (466, 708, 492, 1064), sw=10),
        shape(circle(479, 700, 20), GOLD, (459, 680, 499, 720), sw=10),
    ]
    cloth = [
        shape('M492 716 C560 694 612 744 682 722 C724 708 750 716 768 724 L768 856 C748 848 722 842 682 854 '
              'C612 876 560 826 492 848 Z', WHITE, (492, 694, 768, 876)),
        ink('M520 770 C560 760 600 790 640 780', '#C9D6E0', 8, 0.8),
    ]
    return {'flag_pole': pole, 'flag_cloth': cloth}


def clock():
    body = [
        *tube('M560 1002 L532 1046', '#C2453A', 18),
        *tube('M694 1002 L722 1046', '#C2453A', 18),
        Group([shape('M500 772 A48 48 0 0 1 596 772 Z', GOLD, (500, 724, 596, 772))], rotate=-32, px=548, py=772),
        Group([shape('M658 772 A48 48 0 0 1 754 772 Z', GOLD, (658, 724, 754, 772))], rotate=32, px=706, py=772),
        shape(rr(620, 718, 14, 40, 6), SILVER, (620, 718, 634, 758), sw=8),
        shape(circle(627, 896, 132), RED, (495, 764, 759, 1028)),
        shape(circle(627, 896, 104), ('#FFFDF7', '#F5EBD8'), (523, 792, 731, 1000), sw=8),
    ]
    for i in range(12):
        a = math.radians(i * 30)
        r1, r2 = (78, 94) if i % 3 == 0 else (86, 94)
        body.append(ink(line((627 + r1 * math.sin(a), 896 - r1 * math.cos(a)), (627 + r2 * math.sin(a), 896 - r2 * math.cos(a))),
                        '#1D4E6E', 9 if i % 3 == 0 else 6))
    body += [
        ink('M627 896 L627 826', '#1D4E6E', 13),
        ink('M627 896 L668 918', '#1D4E6E', 11),
        shape(circle(627, 896, 13), CORAL, (614, 883, 640, 909), sw=6),
        shine(arc(627, 896, 118, 200, 250), 12, 0.6),
    ]
    lines = [ink(d, CORAL[1], 14) for d in (
        'M470 830 Q452 870 462 912', 'M436 812 Q410 870 422 930',
        'M784 830 Q802 870 792 912', 'M818 812 Q844 870 832 930')]
    return {'clock': body, 'clock_lines': lines}


PROPS = [music, trophy, phone, key, clipboard, bell, box, plug, gears, hourglass, seal, scales, wallet, flag, clock]


# ---------------------------------------------------------------- emit

def grad_xml(colors, box, ind):
    x1, y1, x2, y2 = box
    return (f'{ind}<aapt:attr name="android:{{attr}}">\n'
            f'{ind}  <gradient android:type="linear" android:startX="{f(x1)}" android:startY="{f(y1)}" '
            f'android:endX="{f(x2)}" android:endY="{f(y2)}">\n'
            f'{ind}    <item android:offset="0" android:color="{colors[0]}" />\n'
            f'{ind}    <item android:offset="1" android:color="{colors[1]}" />\n'
            f'{ind}  </gradient>\n{ind}</aapt:attr>\n')


def xml_items(items, ind):
    out = ''
    for it in items:
        if isinstance(it, list):
            out += xml_items(it, ind)
        elif isinstance(it, Group):
            attrs = ''
            if it.rotate or it.scale != 1:
                attrs += f' android:pivotX="{f(it.px)}" android:pivotY="{f(it.py)}"'
            if it.rotate:
                attrs += f' android:rotation="{f(it.rotate)}"'
            if it.scale != 1:
                attrs += f' android:scaleX="{f(it.scale)}" android:scaleY="{f(it.scale)}"'
            if it.dx or it.dy:
                attrs += f' android:translateX="{f(it.dx)}" android:translateY="{f(it.dy)}"'
            out += f'{ind}<group{attrs}>\n' + xml_items(it.items, ind + '  ') + f'{ind}</group>\n'
        else:
            # Outline first as its own path, so the fill sits over the inner half of the stroke.
            if it.fill is not None and it.stroke is not None:
                out += emit_path(Path(it.d, fill=None, stroke=it.stroke, sw=it.sw * 2, alpha=it.alpha, even=it.even), ind)
                out += emit_path(Path(it.d, fill=it.fill, stroke=None, alpha=it.alpha, even=it.even, box=it.box), ind)
            else:
                out += emit_path(it, ind)
    return out


def emit_path(it, ind):
    a = f'{ind}<path android:pathData="{it.d}"'
    if it.even:
        a += ' android:fillType="evenOdd"'
    if it.stroke:
        a += (f' android:strokeColor="{it.stroke}" android:strokeWidth="{f(it.sw)}" android:strokeLineCap="round" '
              f'android:strokeLineJoin="round"')
        if it.alpha < 1:
            a += f' android:strokeAlpha="{f(it.alpha)}"'
    if isinstance(it.fill, str):
        a += f' android:fillColor="{it.fill}"'
        if it.alpha < 1:
            a += f' android:fillAlpha="{f(it.alpha)}"'
    if isinstance(it.fill, tuple):
        return a + '>\n' + grad_xml(it.fill, it.box, ind + '  ').replace('{attr}', 'fillColor') + f'{ind}</path>\n'
    return a + ' />\n'


def to_xml(items):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by scripts/robot_props.py; drawn in the robot layers\' own space. -->\n'
            '<vector xmlns:aapt="http://schemas.android.com/aapt" xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:width="56dp" android:height="64dp" android:viewportWidth="880" android:viewportHeight="980">\n'
            '  <group android:translateX="-185" android:translateY="-110">\n'
            + xml_items(items, '    ') + '  </group>\n</vector>\n')


# ---- preview: Android vector XML -> SVG

A = '{http://schemas.android.com/apk/res/android}'
gid = [0]


def svg_from_vector(path):
    root = ET.parse(path).getroot()
    defs = []

    def walk(el):
        out = ''
        for c in el:
            if c.tag == 'group':
                tr = ''
                if c.get(A + 'translateX') or c.get(A + 'translateY'):
                    tr += f'translate({c.get(A + "translateX", "0")} {c.get(A + "translateY", "0")}) '
                px, py = c.get(A + "pivotX", "0"), c.get(A + "pivotY", "0")
                if c.get(A + 'rotation'):
                    tr += f'rotate({c.get(A + "rotation")} {px} {py}) '
                if c.get(A + 'scaleX'):
                    tr += f'translate({px} {py}) scale({c.get(A + "scaleX")}) translate(-{px} -{py})'
                out += f'<g transform="{tr}">' + walk(c) + '</g>'
            elif c.tag == 'path':
                fill = c.get(A + 'fillColor', 'none')
                for attr in c:
                    if attr.get('name') == 'android:fillColor':
                        g = attr[0]
                        gid[0] += 1
                        stops = ''.join(f'<stop offset="{s.get(A + "offset")}" stop-color="{s.get(A + "color")}"/>' for s in g)
                        defs.append(f'<linearGradient id="g{gid[0]}" gradientUnits="userSpaceOnUse" x1="{g.get(A + "startX")}" '
                                    f'y1="{g.get(A + "startY")}" x2="{g.get(A + "endX")}" y2="{g.get(A + "endY")}">{stops}</linearGradient>')
                        fill = f'url(#g{gid[0]})'
                s = f'<path d="{c.get(A + "pathData")}" fill="{fill}"'
                if c.get(A + 'fillAlpha'):
                    s += f' fill-opacity="{c.get(A + "fillAlpha")}"'
                if c.get(A + 'fillType') == 'evenOdd':
                    s += ' fill-rule="evenodd"'
                if c.get(A + 'strokeColor'):
                    s += (f' stroke="{c.get(A + "strokeColor")}" stroke-width="{c.get(A + "strokeWidth")}" '
                          f'stroke-linecap="round" stroke-linejoin="round"')
                    if c.get(A + 'strokeAlpha'):
                        s += f' stroke-opacity="{c.get(A + "strokeAlpha")}"'
                out += s + '/>'
        return out
    body = walk(root)
    return ''.join(defs), body


def main():
    made = {}
    for fn in PROPS:
        for name, items in fn().items():
            made[name] = items
            open(RES + f'robot_prop_{name}.xml', 'w', encoding='utf-8', newline='\n').write(to_xml(items))
    groups = {}
    for name in made:
        groups.setdefault(re.split(r'_', name)[0], []).append(name)
    cells = ''
    for prop, layers in groups.items():
        defs, body = '', ''
        # A prop with its own face draws it in place of the usual eyes.
        eyes = [] if f'{prop}_face' in layers else ['level_robot_eyes']
        robot = ['level_robot_body', 'level_robot_head', *eyes, 'level_robot_antenna']
        for r in robot + [f'robot_prop_{n}' for n in layers] + ['level_robot_hands']:
            d, b = svg_from_vector(RES + r + '.xml')
            defs += d
            body += b
        cells += (f'<div class=c><svg viewBox="0 0 880 980" width="300" height="334"><defs>{defs}</defs>{body}</svg>'
                  f'<p>{prop}</p></div>')
    html = ('<html><body style="margin:0;background:#E3F0F8;font:20px sans-serif">'
            '<style>.c{display:inline-block;margin:6px;text-align:center}p{margin:0}</style>' + cells + '</body></html>')
    os.makedirs(OUT_DIR, exist_ok=True)
    open(os.path.join(OUT_DIR, 'preview.html'), 'w', encoding='utf-8').write(html)
    print(len(made), 'drawables;', ' '.join(groups))


main()
