# New app icon: two phones swapping

Chosen on 2026-11-11. Nothing is in the app yet.

## The choice

- **Icon:** colour combo #2, "Robot's swap on tile blue". The colours come from the robot mascot (`app/src/main/res/drawable/testtrack_robot.xml`):
  - left phone coral #F99B80 to #EF806B, right phone mint #77D6C2 to #54C5AC;
  - cream screens #FFFCF1 and ink checks #17253B;
  - robot-blue arrows #32729B on a tile-blue background #E7F4FC to #D5EAF7.
- **No borders:** the phones have no outline lines.
- **Centred:** the artwork is centred on the 108 grid (54, 54). The bottom arrow is an exact half-turn of the top one.
- **Turned down:** the owner looked at the Figma V2 theme (bright blue #4A82FF) and said "ours was better", so the robot blue stays.
- **Launch animation:** option C, "Rise and turn", 950 ms. The phones rise into place, the arrows fade in slowly while making a half turn with a small bounce, then the checks draw, left first.
- **Launch screen:**
  - Only the icon's foreground, with no plate and no circle.
  - Light theme: plain #F7FCFF.
  - Dark theme: plain #0F1011. Here the arrows are light blue #80BCDE, because #32729B disappears on black.

## Files

| File | What it is |
|---|---|
| `icon-foreground.svg` | Adaptive icon foreground, 108 grid |
| `icon-background.svg` | Adaptive icon background (tile-blue gradient) |
| `icon-combined.svg` | Both layers together, for Play (512 px) and previews |
| `icon-monochrome.svg` | Themed-icon layer: one colour, cut-outs done with evenodd, no masks |
| `splash-foreground-light.svg` / `splash-foreground-dark.svg` | Launch-screen icon (no background) for the day and night themes |
| `numbers.json` | Every shape number (phone size, tilt, arrow curve and so on) |
| `pages/launch-animation.html` | The chosen animation, playing on light and dark launch screens |
| `pages/icon-on-app-backgrounds.html` | The icon on the app's light and dark backgrounds, on the Figma V2 theme, and as themed icons |
| `pages/robot-colour-options.html` | All 19 colour options (10 from the robot, 9 from Codex); #2 is the pick |
| `pages/tune.html` | The slider tuner (needs `ref.png` next to it) |
| `source/` | Generators. `_t.js` is the tuner's script; `final.js <outdir>` rebuilds the final SVGs; `anim.js` rebuilds the animation page |

## Still to do (needs the owner's go)

1. **Build the icon into the app.** The coder turns the SVGs into adaptive-icon vector drawables (foreground, background, monochrome) plus the 512 px Play icon.
   - Vector drawables can't use the SVG drop-shadow filters, so either leave the shadows out or bake them into the foreground.
2. **Build the launch animation.** Turn "Rise and turn" into an animated vector drawable: `drawable-v31/testtrack_splash_vector_animated.xml`, plus a `-night` copy with the #80BCDE arrows.
   - It uses only what animated vectors support: rotation, translation, scaling and opacity on groups, and trimPathEnd on the check strokes. The timings are in `source/anim.js` (css block, "C").
   - The launch-screen icon has to stay inside Android's 2/3 circle. The artwork reaches about 32 of the allowed 36 units from the centre.
3. **Later:** the app review from onboarding (the UI is too bulky) and a full hand-test setup.
