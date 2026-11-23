# TestTrack store photos, direction (2026-11-23)

Photos for the English Play photo slides, built like Daytone's (mkit `scripts/photo_slides/build_app.py` + `composite.py`).
Made with Codex image generation, with `phone_template.png` attached every time (Daytone's copy, 1080x2340 screen shape like TestTrack's captures).
Full prompts: `_run/p_<name>.md`. Generator: `_run/gen.py [names]`. Green screen check: `_run/measure.py`.

## Owner rules applied to every photo
Men only, including the background. Bright daylight, faces visible and sharp, real backgrounds (desk, cafe, co-working, campus, home office). No hand-only shots, no dark moody shots. The whole phone is always visible, the screen is flat pure #00FF00, and fingers stay on the bezel. No text, logos or brand marks. Laptop code is soft and unreadable. Mostly South Asian men, plus one Black and one Indonesian man. Colours are white, light oak, sky and denim blue, with gold accents. Nothing green except the screen.

## Shared prompt blocks (in gen.py)
- T: use EXACTLY the template phone (slim black, thin even bezel, punch-hole, screen height 2.17 x width).
- SCR: screen faces the camera almost flat, one flat full-brightness #00FF00, no glare, icons or green spill; whole phone visible with space below; thumb and fingers on side edges and back only.
- MEN: only men, no women or girls anywhere.
- Portrait framing: screen about 38% of width, phone top about 32%, bottom no lower than 86%; face beside the phone (right), eyes at 30 to 36%; top 22% calm for the title; everything inside the middle 84% of width.
- END: candid premium lifestyle photo, bright daylight, faces lit and sharp, shallow depth of field, colour list, no text or logos, five fingers.

## Images
| file | who / where |
|---|---|
| land_hero.png 1536x1024 | Bangladeshi man early 30s, denim shirt over white tee, bright home office, laptop with code at right; phone beside face; left 55% calm white wall |
| land_feature.png 1536x750 | Indian man late 20s, sky-blue tee, co-working space, laptop with code; phone at shoulder. Pre-cropped to the 1024x500 ratio (rows 51 to 801 of the 1536x1024 original, `_old/land_feature_v2_full.png`) so the builder's centre crop does not cut his hair |
| photo_proof.png | Bangladeshi man late 20s, rimless glasses, white oxford, home office desk with laptop; calm focused half smile |
| photo_free.png | Pakistani man early 30s, grey henley, white sofa in a sunny flat; big relieved smile |
| photo_crew.png | Three men in a co-working space: Bangladeshi (front, green phone), Black, Sri Lankan; the back two hold their phones back to camera |
| photo_feedback.png | Indonesian man mid 20s, cream sweater, cafe by a window, hand on laptop with code, coffee cup |
| photo_volunteer.png | Bangladeshi student early 20s, denim overshirt, navy backpack, sunny campus walkway, male students far behind |
| photo_dark.png | South Asian man early 30s, navy tee, home office at dusk, warm desk lamp on his face, blue window, laptop with code |
| phone_3d_a.png | Phone alone, about 15 degrees yaw (left edge near), side buttons on the right, pale sky blue to white studio backdrop |
| phone_3d_b.png | Phone alone, about 10 degrees yaw the other way, floating with soft shadow, light ocean blue backdrop |

## Green screen measures and builder notes
The portraits are 1024x1536. The builder scales them 1.25x to 1080x1920, so it can shift them sideways by about 100 px but not up or down.
The phones in the people photos are smaller than Daytone's hand shots (21 to 27% of the width), so the faces fit beside them.
Set `phone_w` per slide to the value below (the native size, no zoom). The default of 560 would zoom in about 1.6x and cut off the faces.
`phone_top` has no effect at the native size.
Use lifts and cards to carry the app detail.

| file | screen box (src px) | h/w | phone_w | screen top on slide |
|---|---|---|---|---|
| photo_proof | x235-482 y520-1079 | 2.26 | ~308 | ~650 |
| photo_free | x192-469 y500-1115 | 2.22 | ~346 | ~625 |
| photo_crew | x379-643 y733-1333 | 2.27 | ~330 | ~916 |
| photo_feedback | x155-430 y502-1150 | 2.36 | ~343 | ~627 |
| photo_volunteer | x194-433 y540-1075 | 2.24 | ~298 | ~675 |
| photo_dark | x228-458 y563-1052 | 2.13 | ~287 | ~703 |
| phone_3d_a | x296-703 y424-1374 | 2.33 (perspective) | ~508 | ~530 |
| phone_3d_b | x323-716 y431-1379 | 2.41 (perspective) | ~491 | ~538 |
| land_hero | x713-894 y203-631 | 2.36 | | composite squeezes about 8% vertically |
| land_feature | x840-952 y215-472 | 2.29 | | small phone, about 5% squeeze |

photo_feedback's phone sits left of centre (x 155 to 430), so the composite clamps the crop at the left edge, which is fine. Its face stays in frame.

## Retries
- land_feature v1: hair at 5% from the top, cut by the banner crop. v2 still at about 9%, so it was pre-cropped by hand (above).
- phone_3d_b v1: yaw too strong, the screen read 2.60 tall (a 17% squeeze). v2 has a gentler angle.
- photo_proof v1: screen only 21% of the width. v2 is 24%, still small. The model keeps phones held at arm's length small.
Originals are in `_old/`.

## Open
- photo_dark has a dark potted plant top left. It is too dark to key as green, so the composite is safe.
- The people-photo phones are small for showing a full capture. If the owner wants bigger phones, regenerate with the phone held near the lens as the main subject and the face smaller behind it.
