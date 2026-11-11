# unDraw art: license note

Checked on 2026-11-11 at https://undraw.co/license (copyright 2026, Katerina Limpitsouni).

## What the license allows

- Free use of any unDraw image in personal and commercial work.
- No permission and no credit needed.
- Download, copy, modify, distribute and use, including recolouring (what `recolor.py` does).

## What it forbids

- Compiling unDraw assets to copy unDraw or build a similar or competing service.
- Distributing the assets in packs or otherwise as a set. The license says this "extends to
  automated and non-automated ways to link, embed, scrape, search or download the assets
  included on the website without our consent".
- Any use for AI or machine learning: training, fine-tuning, datasets, validating or testing models.
- Brand logos in some images stay their owners' trademarks. None of the files below has one.

## One line to be aware of

The short summary at the top of the page says: "If you find unDraw or its illustrations to be in
the center of what you are doing (e.g. sell or re-distribute one/some of them, train an ai model,
add them in an app), then you probably should not proceed." Read with the full text, this is
aimed at apps whose product is the illustrations: a picker, a pack, an integration. Onboarding
art that improves an app's look is the case the summary allows ("use illustrations to improve
its appearance, modified or not"). If in doubt, ask unDraw before release.

## How we keep to it

- Only the art we use is in this folder. It is not published as a set, and nothing links to
  or embeds the unDraw CDN at runtime.
- To compare candidates, we opened five unDraw search pages and fetched 23 SVGs from their
  own links with a short script. Only the files below are in the repo.
  That small scripted fetch is close to the license's "automated ... download" line. It was for
  our own use, not to build a collection, but don't repeat it at scale.
- `original/` holds the files exactly as downloaded. `light/` and `dark/` are made from them
  by `recolor.py`.

## Files

The five in use, all from the first round's comparison.

| Page | File | unDraw title | Found on | SVG source |
|---|---|---|---|---|
| 1. Get your 12 testers | true-friends_1h3v.svg | True Friends | https://undraw.co/search/team | https://cdn.undraw.co/illustration/true-friends_1h3v.svg |
| 2. Real user feedback | online-review_08y6.svg | Online Review | https://undraw.co/search/feedback | https://cdn.undraw.co/illustration/online-review_08y6.svg |
| 3. A few minutes a night | work-chat_kw8x.svg | Work Chat | https://undraw.co/search/team | https://cdn.undraw.co/illustration/work-chat_kw8x.svg |
| 4. One link | mobile-application_uc2q.svg | Mobile application | https://undraw.co/search/mobile | https://cdn.undraw.co/illustration/mobile-application_uc2q.svg |
| 5. Look around first | searching_pqji.svg | Searching | https://undraw.co/search/explore | https://cdn.undraw.co/illustration/searching_pqji.svg |

One more file was fetched by hand, by its direct URL, while looking for a better page 1:
https://cdn.undraw.co/illustration/chatting_29rn.svg ("Chatting", 2026-11-12). It shows a phone
with message bubbles but no people, so it was not used and is not in the repo.

Changes made by `recolor.py` (the license allows changes):
- **Removed:** floors and shadows from all five, so no picture has a ground line. Also gone:
  the backpacks and the apple in true-friends, the browser frame in online-review, and the
  plants in work-chat and searching.
- **true-friends:** each person now holds a phone, a swap mark in robot blue sits between
  them, and the clothes are mostly ink, so the blue stays small.
- **online-review:** the man holds a phone, and the review bubble is an outline.
- **work-chat:** the outer two people are moved closer to the middle one, with a light
  14-night calendar card above them.
- **mobile-application:** the phone's screen content is replaced with a Play-link field,
  the found app and two buttons.
- **searching:** one of the two blue tiles is grey.
- **All five:** hair and light clothing are marked so they look natural at night, and each
  file is framed so every standing figure comes out the same height. `measure.html` measures
  the content boxes the framing uses.

## Rebuilding the colours

```powershell
python docs/design/onboarding/undraw/recolor.py
```

Light: unDraw's violet #6C63FF becomes robot blue #32729B, and its purple-grey inks become the
app's blue-grey ink. Dark: white and the palest greys become #22262A and #1A1D20, light greys
become the edges a step lighter, near-black clothes and frames are lifted to mid-dark greys so
they show on #0F1011, hair becomes a deep brown #3D2F2B, skin tones stay, and the accent becomes
#5B93B8. Tonight's night on page 3 is the one mint touch. The script stops with a
list if a file uses a colour that is not in the map.
