# TestTrack store photos, direction (set 2, 2026-11-23)

Photos for the English Play photo slides, built like Daytone's (mkit `scripts/photo_slides/build_app.py` + `composite.py`).
Made with Codex image generation. `character_ref.png` and `phone_template.png` were attached to every photo of the set.
`phone_template.png` is Daytone's copy, with a 1080x2340 screen shape like TestTrack's captures.
Generator: `_run/gen2.py [names]` (writes `_run/p_<name>.md` and `_run/<name>.log`). Green screen check: `_run/measure.py`.
Set 1 (several different men) with its `gen.py` and prompts is in `_old/set1_2026-11-23/`.

## Main character (owner feedback 2026-11-23)
One man on every photo that shows a face. Reference: `character_ref.png` (1024x1536, head and chest, straight on).
- A Bangladeshi software developer, about 28. He has warm medium-brown skin and short thick black hair with a soft side part, swept up a little at the front, with tapered sides.
- He has a neatly trimmed short full black beard joined to a moustache, and thin black rectangular full-rim glasses. Slim build.
- Clothes: an open light-wash denim shirt, sleeves rolled to mid forearm, over a plain white crew-neck tee. In the dusk photo he wears a plain dark navy tee.
- Hands-only photos show his skin tone and the denim cuff.

The words are kept in `HIM` and `SHIRT` in gen2.py. Every prompt also says the man is EXACTLY the person in character_ref.png.

## Owner rules applied to every photo
- Men only, including the background.
- Bright daylight. The dusk photo is the exception, and its face is still clearly lit by the lamp.
- Real developer environments: a desk with a laptop and an external monitor showing soft, unreadable dark-theme code, a keyboard, a coffee mug, light oak, and shelves with plain-spined books.
- The whole phone is always visible, the screen is flat pure #00FF00, and fingers stay on the edges.
- No text, logos or brand marks.
- Colours: white, light oak, sky and denim blue, gold accents. Nothing green except the screen, so no plants.
- Phones are bigger than in set 1. Each prompt gives the phone's pixel box and says "SIZE IS THE MOST IMPORTANT RULE" (arm stretched toward the lens).
- Hands-only slides: free, feedback and volunteer (owner's request).

## Images
| file | content |
|---|---|
| character_ref.png 1024x1536 | Reference portrait of the main character (not a slide). A plant shows on the shelf behind him; it is not used in any slide |
| land_hero.png 1820x1024 (16:9) | He sits at his desk and holds the phone up beside his face. Monitor and laptop with code, keyboard and mug at right, shelf with books. Left 46% is a calm white wall. The left wall was widened from the 1536x1024 original (`_old/set2_try2/land_hero.png`, columns 0 to 400 stretched smoothly) so the phone clears the title block |
| land_feature.png 1536x750 | The same man, same desk set with the monitor and laptop of code at left, phone held up beside his face. Pre-cropped to the 1024x500 ratio from rows 50 to 800 of `_old/set2_try2/land_feature.png`. About 55 px of wall above his hair |
| photo_proof.png | At his dev desk with monitor, laptop, mug and keyboard. Phone held toward the lens, calm half smile, glasses |
| photo_crew.png | Three men in a bright co-working space. He is in front with the green phone, a Black man (white tee) and a Sri Lankan man (sky-blue polo) behind him, their phones back to camera. This is try 1 of set 2 |
| photo_dark.png | The same man in a navy tee, home office at dusk. Brass lamp lights his face warmly, monitor glow, blue dusk window |
| photo_free.png | Hands only: right hand holds the phone, denim cuff. Monitor and laptop with code, mug, shelf |
| photo_feedback.png | Hands only: left hand holds the phone, right hand on the laptop keyboard, latte in a sky-blue mug, monitor with code |
| photo_volunteer.png | Hands only: both hands cup the bottom of the phone. Blank notebook and pen, latte, monitor and laptop with code |
| phone_3d_a.png, phone_3d_b.png | Unchanged from set 1 |

## Green screen measures and builder notes
The portraits are 1024x1536. The builder scales them 1.25x to 1080x1920.

| file | screen box (src px) | width | h/w | phone_w | screen top on slide |
|---|---|---|---|---|---|
| photo_proof | x214-513 y485-1169 | 29% | 2.29 | ~373 | ~606 |
| photo_crew | x352-624 y739-1337 | 27% | 2.20 | ~340 | ~923 |
| photo_dark | x186-498 y486-1192 | 30% | 2.26 | ~390 | ~607 |
| photo_free | x297-726 y435-1368 | 42% | 2.17 | ~536 | ~543 |
| photo_feedback | x330-683 y415-1189 | 34% | 2.19 | ~441 | ~518 |
| photo_volunteer | x297-726 y433-1367 | 42% | 2.18 | ~536 | ~541 |
| land_hero | x843-1078 y98-602 | 13% | 2.14 | | 16:9, no vertical crop. On a 1920x1080 slide the phone is at about x889-1137 |
| land_feature | x709-950 y164-688 | 16% | 2.17 | | On the 1024x500 banner the phone is at about x467-640, so the title column must end by about x450 |

## Retries
- Try 1: all phones too small in the face photos: land 8%, proof 19%, dark 24%, crew 27%. Kept in `_old/set2_try1/`.
- Try 2: added the pixel boxes and "SIZE IS THE MOST IMPORTANT RULE". Proof went to 29%, dark 30%, hero 15% and feature 16%.
- Crew dropped to 21% in try 2 (`_old/set2_try2/photo_crew.png`), so crew try 1 is kept. Note that `_run/p_photo_crew.md` holds the try 2 prompt.
- Hero try 2 put the phone at 36% of the width, under the title block. Its left wall was widened instead of running a third generation.

## Open
- The face portraits are at 27 to 30% screen width, below the 35 to 45% target. The model keeps arm-held phones smaller than asked. A third try could ask for the phone as the foreground subject with his face smaller behind it.
- land_feature: the text column of the old feature comp runs to x596 and needs narrowing to about x450, or the photo needs a regeneration with the phone further right.
