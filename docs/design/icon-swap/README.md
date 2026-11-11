# New app icon: two phones swapping

Chosen on 2026-11-11, bug added the same day after the meaning and ASO review. Only the launch screen is in the app so far.

## The choice

- **Icon:** colour combo #2, "Robot's swap on tile blue". The colours come from the robot mascot (`app/src/main/res/drawable/testtrack_robot.xml`):
  - left phone coral #F99B80 to #EF806B, right phone mint #77D6C2 to #54C5AC;
  - cream screens #FFFCF1 and ink checks #17253B;
  - robot-blue arrows #32729B on a tile-blue background #E7F4FC to #D5EAF7.
- **Bug and check (variant C):** the left phone shows a bug and the right phone a check, so it reads "found it, fixed it" instead of phone transfer.
  - The check stroke is 2.3 (it was 2.9).
  - The bug is in `source/_bug.js`, at scale 0.78, a little lighter than the check, as the owner asked (`pages/bug-sizes.png` compares sizes).
  - Scores after the change, me / Codex: meaning 8 / 7, ASO 6.5 / 7, craft 8 / 8. Before, Codex had 6 / 6 / 8.
  - The owner kept the pale tile with robot-blue arrows over the deep-blue and other light backgrounds.
- **No borders:** the phones have no outline lines.
- **Centred:** the artwork is centred on the 108 grid (54, 54). The bottom arrow is an exact half-turn of the top one.
- **Turned down:** the owner looked at the Figma V2 theme (bright blue #4A82FF) and said "ours was better", so the robot blue stays.
- **Launch animation:** option C, "Rise and turn", 950 ms. The phones rise into place, the arrows fade in slowly while making a half turn with a small bounce, then the bug pops in on the left phone and the check draws on the right.
- **Launch screen:**
  - Only the icon's foreground, with no plate and no circle.
  - Light theme: plain #F7FCFF.
  - Dark theme: plain #0F1011. Here the arrows are light blue #80BCDE, because #32729B disappears on black.

## For Android Studio (Image Asset)

Use the files in the repo-root `branding/` folder:
- **Foreground layer:** `foreground.png` (1024 px, keeps the soft shadows) or `foreground.svg` (vector, no shadows).
- **Background layer:** `background.png` or `background.svg`, a tile-blue gradient from #E7F4FC to #D5EAF7.
- **Monochrome layer:** `monochrome.png` or `monochrome.svg`.
- **Play icon:** Android Studio writes `app/src/main/ic_launcher-playstore.png` together with the launcher set. `mkit.toml` `[store] icon` points there, so no copy is kept here (mkit guide store).

The check is at size 0.9 (it was 0.99), so it balances the bug at 0.78.

## Files

| File | What it is |
|---|---|
| `icon-foreground.svg` | Adaptive icon foreground, 108 grid |
| `icon-background.svg` | Adaptive icon background (tile-blue gradient) |
| `icon-combined.svg` | Both layers together, for Play (512 px) and previews |
| `icon-monochrome.svg` | Themed-icon layer: one colour, cut-outs done with evenodd, no masks |
| `splash-foreground-light.svg` / `splash-foreground-dark.svg` | Launch-screen icon (no background) for the day and night themes |
| `branding/splash-light.svg`, `branding/splash-dark.svg` | Launch-animation sources. Every moving part is a named group (`arrows`, `arrow_top`, `arrow_bottom`, `phone_left` with `bug`, `phone_right` with `check`), with no filters |
| `numbers.json` | Every shape number (phone size, tilt, arrow curve and so on) |
| `pages/launch-animation.html` | The chosen animation, playing on light and dark launch screens |
| `pages/icon-on-app-backgrounds.html` | The icon on the app's light and dark backgrounds, on the Figma V2 theme, and as themed icons |
| `pages/robot-colour-options.html` | All 19 colour options (10 from the robot, 9 from Codex); #2 is the pick |
| `pages/one-cue-variants.html`, `pages/bug-and-check.html`, `pages/light-backgrounds.html` | The variants (robot, 12-tester ring, bug), the old and new bug, and light backgrounds by arrow colour |
| `pages/tune.html` | The slider tuner (needs `ref.png` next to it) |
| `source/` | Generators. `_t.js` is the tuner's script; `final.js <outdir>` rebuilds the final SVGs (the Studio layers and splash sources it writes there are the ones kept in `branding/`); `anim.js` rebuilds the animation page |

## Still to do (needs the owner's go)

1. **Build the icon into the app.** In the monochrome version the bug is a solid shape, without the cream details. The coder turns the SVGs into adaptive-icon vector drawables (foreground, background, monochrome) plus the 512 px Play icon.
   - Vector drawables can't use the SVG drop-shadow filters, so either leave the shadows out or bake them into the foreground.
2. **Launch animation: done.** `python scripts/build_swap_splash.py` turns `branding/splash-light.svg` and `branding/splash-dark.svg` into the animated vector drawables (`drawable-v31`, `drawable-night-v31`), the static icons for Android 11 and older (`drawable`, `drawable-night`) and the easing curves in `res/interpolator`. Backgrounds are `splash_background` (`values/splash.xml`, `values-night/splash.xml`).
3. **Later:** the app review from onboarding (the UI is too bulky) and a full hand-test setup.
