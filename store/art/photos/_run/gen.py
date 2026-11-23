"""TestTrack store photos: write one prompt per image and run Codex image generation for each.

usage: python gen.py [name ...]   (no names = all)
"""
import pathlib
import subprocess
import sys

CX = r'C:\Users\namcs\AppData\Local\Programs\OpenAI\Codex\bin\codex.exe'
D = pathlib.Path(r'H:\android\onGoing\test_track\store\art\photos')

T = ("Use EXACTLY the phone in the attached phone_template.png: slim black body, thin even bezel, rounded corners, small punch-hole camera, "
     "screen height 2.17 x screen width; never taller, narrower or thicker. ")
SCR = ("The phone screen faces the camera almost flat (under 10 degrees of tilt) and is one flat, evenly lit, full-brightness pure green #00FF00 "
       "from edge to edge, with no reflections, glare, icons, UI or gradient, and it casts no green light on the hand, face or clothes. "
       "The WHOLE phone is visible with clear space below it (it is never cut by the frame edge), and thumb and fingers touch only its side "
       "edges and back: no finger, thumb or object covers any part of the green screen. ")
MEN = "Only men in the picture: no women or girls anywhere, not even in the background. "
END = ("Real, candid, premium app-store lifestyle photography, bright natural daylight, faces clearly lit and in sharp focus, eyes open, natural "
       "friendly expressions, shallow depth of field on a real, recognisable background. Colours: white, light oak, sky blue, denim and ocean "
       "blue with a small warm golden accent; no green anywhere except the phone screen (no plants, no green clothes). No text, letters, "
       "numbers, signs, logos or brand marks anywhere (laptop lids, mugs, clothes and bags are plain). Real hands with five fingers. ")
CODE = ("The laptop screen shows a code editor: a dark theme with soft, out-of-focus coloured lines of code that cannot be read, no logos, "
        "no window titles. ")


def portrait(name, who, scene, pose):
    return (f"Generate one photorealistic PORTRAIT image, 1024x1536, saved as {name}.png in the current folder. {T}{MEN}"
            f"{who} {pose} {SCR}"
            "Framing: the phone's screen is about 38% of the image width, its centre about 40% from the left edge, phone top edge at about "
            "32% of the image height and phone bottom no lower than 86%. His face is beside the phone (to its right), fully visible, eyes at "
            "about 30 to 36% of the image height, never covered by the phone or a hand. The top 22% of the image is softly blurred real "
            "background with no head, face or hand in it, because the title goes there. Keep the person and phone inside the middle 84% of "
            f"the width. Scene: {scene} {END}")


P = {}
P['land_hero'] = (
    "Generate one photorealistic LANDSCAPE image, 1536x1024, saved as land_hero.png in the current folder. " + T + MEN +
    "A smiling Bangladeshi man in his early 30s, short black hair, neat short beard, white tee under an open light denim shirt, a software "
    "developer, sits at his desk waist-up in the RIGHT 45% of the frame and holds that phone up toward the camera with one hand, beside his "
    "face (face fully visible, warm open smile showing teeth, looking at the camera). Phone height about 50% of the image height. " + SCR +
    "An open laptop sits on the light oak desk in front of him at the right edge, its screen angled so part of it is visible. " + CODE +
    "Scene: a bright home office in morning daylight: a big window with soft sky, a white wall, a light oak shelf with a few books with plain "
    "spines and a white mug, softly out of focus but clearly a real room. The LEFT 55% is calm, bright and low contrast (white wall and window "
    "light, softly blurred), with no faces, people or hard-edged objects, because large text goes there. Keep his head below 14% from the top "
    "and the phone bottom above 84%, since the top and bottom 8% get cropped. " + END)
P['land_feature'] = (
    "Generate one photorealistic LANDSCAPE image, 1536x1024, saved as land_feature.png in the current folder. " + T + MEN +
    "A cheerful Indian man in his late 20s, wavy black hair, light stubble, sky-blue crew-neck tee, sits at a bright co-working table, waist-up "
    "in the RIGHT 45% of the frame, holding that phone up toward the camera with one hand at shoulder height, beside his face (face fully "
    "visible, relaxed smile, looking at the camera). His other hand rests on an open laptop beside him. " + CODE +
    "Phone height about 45% of the image height. " + SCR +
    "Scene: a bright co-working space with big windows, white walls, light oak tables, a couple of blurred men working in the far background. "
    "The LEFT 55% is calm, bright and softly blurred, empty enough for large text. IMPORTANT: this is cropped to a wide 1024x500 banner, "
    "which cuts the top 13% and bottom 13%: frame him a little smaller with generous empty wall above his head; the TOP of his hair is at about 22% of the image height (never higher than 20%), his chin at about 45%, and the whole phone sits between 24% and 74% of the image height. Nothing important in the top 20% or bottom 20%. " + END)
P['photo_proof'] = portrait(
    'photo_proof',
    "A focused Bangladeshi man in his late 20s, short black hair, rimless glasses, white oxford shirt with rolled sleeves, an app developer,",
    "a bright home office desk by a window: an open laptop beside him, a white mug, a light oak desk, a white wall and window light behind, softly out of focus. " + CODE,
    "sits at the desk and holds the phone out close to the lens in his left hand, as if showing the proof he just checked; a calm, focused, "
    "satisfied half smile, looking at the camera. Because the phone is near the camera it looks LARGE: its green screen is a full 40% of "
    "the image width (about 410 px wide and 890 px tall), much bigger than his face.")
P['photo_free'] = portrait(
    'photo_free',
    "A pleased, relaxed Pakistani man in his early 30s, short beard, light grey henley tee,",
    "a sunny apartment living room: he leans back on a white sofa with a sky-blue cushion, a big bright window and white curtains behind him, a light oak side table, softly out of focus.",
    "holds the phone up toward the camera in one hand, a big happy, relieved smile showing teeth, looking at the camera.")
P['photo_crew'] = (
    "Generate one photorealistic PORTRAIT image, 1024x1536, saved as photo_crew.png in the current folder. " + T + MEN +
    "Three young male app developers in their 20s stand close together as a happy team in a bright co-working space: in front, a Bangladeshi "
    "man with short black hair in a light denim shirt holds that phone up toward the camera, smiling; behind his right shoulder a Black man "
    "with short hair in a white tee and, on the other side, a Sri Lankan man with curly hair in a sky-blue polo lean in, smiling at the camera, "
    "each holding his own phone low at chest level with its back toward the camera (only the front man's screen is visible). " + SCR +
    "Framing: the front phone's screen is about 34% of the image width, centred horizontally, its top edge at about 40% of the image height "
    "and the phone bottom no lower than 86%. All three faces are fully visible and unobstructed, between 20% and 40% of the image height, "
    "none behind the front phone. The top 18% is softly blurred background with no heads, because the title goes there. Keep all three "
    "inside the middle 86% of the width. Scene: a bright co-working space with white walls, big windows, light oak tables and laptops softly "
    "out of focus behind them. " + END)
P['photo_feedback'] = portrait(
    'photo_feedback',
    "A friendly Indonesian man in his mid 20s, short black hair, cream knit sweater over a white tee,",
    "a bright cafe by a big window: a light oak table, a cup of coffee, an open laptop beside him on the table, white walls, softly out of focus. " + CODE,
    "sits at the cafe table with his right hand on the laptop keyboard and holds the phone up toward the camera in his left hand, as if "
    "showing the feedback he just wrote; a warm, helpful smile, looking at the camera.")
P['photo_volunteer'] = portrait(
    'photo_volunteer',
    "A cheerful Bangladeshi university student in his early 20s, short black hair, light blue overshirt over a white tee, a plain navy backpack on one shoulder,",
    "a sunny university campus walkway: pale stone paths, white and red-brick buildings with big windows, a clear blue sky, a couple of male students walking far behind, softly out of focus. No trees or grass.",
    "stands on the walkway on his way to class and holds the phone up toward the camera in one hand, a bright open smile, looking at the camera.")
P['photo_dark'] = (
    "Generate one photorealistic PORTRAIT image, 1024x1536, saved as photo_dark.png in the current folder. " + T + MEN +
    "A calm, content South Asian man in his early 30s, short black hair, short beard, navy-blue tee, an indie developer working late, sits at "
    "his desk in the evening and holds the phone up toward the camera in his left hand, a quiet satisfied smile, looking at the camera. " + SCR +
    "Framing: the phone's screen is about 38% of the image width, its centre about 40% from the left edge, phone top edge at about 32% of the "
    "image height and phone bottom no lower than 86%. His face is beside the phone (to its right), fully visible, eyes at about 30 to 36% of "
    "the image height. The top 22% is softly blurred room with no head in it. Keep the person and phone inside the middle 84% of the width. "
    "Scene: a cosy home office in the evening, a deep blue dusk window behind him, a warm desk lamp at the side lighting his face clearly and "
    "evenly (warm, clear mid tones, no crushed blacks, not moody or gloomy), an open laptop on the desk. " + CODE +
    "The room is darker than daytime but the man, his face and hands are well lit and in sharp focus. Real, candid, premium lifestyle "
    "photography, shallow depth of field. Colours: deep ocean blue dusk, warm lamp gold, light oak. No green anywhere except the phone screen, "
    "and the screen casts no green light on him. No text, letters, numbers, logos or brand marks anywhere. Real hands with five fingers. ")
P3 = ("The attached phone_template.png shows the phone model: slim black-graphite metal body, rounded corners, small punch-hole camera, screen "
      "proportion 9:19.5 (screen height = 2.17 x width). Use that same phone, alone, with no hand and no person, as a clean, premium 3D product "
      "render. ")
P['phone_3d_a'] = (
    "Generate one photorealistic PORTRAIT image, 1024x1536, saved as phone_3d_a.png in the current folder. " + P3 +
    "Show it standing upright in a natural THREE-QUARTER view: the screen turned about 15 degrees (yawed around its vertical axis, LEFT edge "
    "slightly nearer the camera) and tilted back about 5 degrees, so perspective makes the near side slightly taller than the far side and a "
    "thin strip of the polished metal side frame with a soft highlight and the side buttons is visible along the RIGHT edge. The front frame is "
    "a clear, even, solid dark bezel about 2.5% of the phone width with a thin lighter metal rim. The screen is one flat, evenly lit, "
    "full-brightness pure green #00FF00 from edge to edge, with no reflections, glare, icons or gradient. The whole phone is visible, centred, "
    "screen about 45% of the image width, phone top at about 25% and bottom at about 88% of the image height, with a soft natural contact "
    "shadow below. Background: plain seamless studio backdrop, a smooth soft gradient from pale sky blue (#DCEBF5) at the top to white at the "
    "bottom, nothing else in the frame. Soft, bright studio light. No text, logos or brand marks anywhere, nothing green except the screen. ")
P['phone_3d_b'] = (
    "Generate one photorealistic PORTRAIT image, 1024x1536, saved as phone_3d_b.png in the current folder. " + P3 +
    "Show it floating upright in a natural THREE-QUARTER view: the screen turned only about 10 degrees the OTHER way (yawed around its vertical axis, "
    "RIGHT edge slightly nearer the camera) and tilted back about 3 degrees, so a gentle perspective makes the near side slightly taller than the far "
    "side and a thin strip of the polished metal side frame with a soft highlight is visible along the LEFT edge. The screen keeps its true 9:19.5 shape (not stretched tall or thin). The front frame is a clear, "
    "even, solid dark bezel about 2.5% of the phone width with a thin lighter metal rim. The screen is one flat, evenly lit, full-brightness "
    "pure green #00FF00 from edge to edge, with no reflections, glare, icons or gradient. The whole phone is visible, centred, screen about 45% "
    "of the image width, phone top at about 25% and bottom at about 88% of the image height, with a soft diffuse shadow below it. Background: "
    "plain seamless studio backdrop, a smooth soft gradient from light ocean blue (#BFDDF0) at the top to very pale blue-white at the bottom, "
    "nothing else in the frame. Soft, bright studio light. No text, logos or brand marks anywhere, nothing green except the screen. ")

if __name__ == '__main__':
    names = sys.argv[1:] or list(P)
    procs = []
    for n in names:
        f = D / '_run' / f'p_{n}.md'
        f.write_text(P[n], encoding='utf-8')
        args = [CX, 'exec', '-C', str(D), '-s', 'workspace-write', '--skip-git-repo-check', '--ephemeral',
                '-i', str(D / 'phone_template.png'), '-']
        procs.append((n, subprocess.Popen(args, stdin=open(f, 'rb'), stdout=open(D / '_run' / f'{n}.log', 'wb'),
                                          stderr=subprocess.STDOUT, creationflags=0x08000000)))
        print('started', n, flush=True)
    for n, p in procs:
        print(n, 'exit', p.wait(), flush=True)
