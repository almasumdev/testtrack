# TestTrack onboarding

Open `preview.html` for an HTML preview of the five pages in both themes.

The introduction is an illustrated pager: brand and Skip,
large artwork, a short headline and explanation, progress dots, a primary action, and an
existing-account sign-in link. Every page keeps TestTrack's Ocean palette. There are no per-page
background or button color changes. Coral and mint are small, recurring illustration accents.

## Pages and behavior

Pictures are unDraw art recoloured to the Ocean palette (see `undraw/LICENSE-NOTE.md`). Open
`undraw.html` for the approved layout in both themes.

1. Get your 12 testers, free (`true-friends`).
2. Get real user feedback, free (`online-review`).
3. A few minutes a night, no cost (`work-chat`). Add the crew chat sentence when chat ships.
4. One link is all it takes (`mobile-application`).
5. Look around first (`searching`).

The tour introduces the current Circle and Crew flows without promising automatic Play approval,
public review rewards, or automatic proof collection without setup.

- A new guest sees the tour before home. Returning accounts retain the existing home/setup flow.
- Swipe or Next advances; Android Back returns to the previous page.
- Skip and Explore TestTrack persist completion and replace the tour with home. Back cannot reopen it.
- Sign in opens the app's existing Google sign-in sheet. Dismissing it returns to the same slide;
  successful sign-in completes the introduction. Guest browsing remains available.
- A pending group notification waits through onboarding/setup, then opens from home.
- The pager restores its page across configuration changes. Only artwork gets subtle parallax.
  Next changes pages immediately when system animations are disabled.
- The picture sits in a 328 x 330 dp box, the same on every page, and shrinks on smaller screens. Large text scrolls within the slide; controls remain available.
  Text is never scaled down to fit. Artwork is decorative to accessibility services; dots announce
  the current page. The controls and copy use native text, not text inside illustrations.

## Editable assets

`undraw/original` holds the untouched unDraw files. `undraw/recolor.py` makes `undraw/light` and
`undraw/dark` from them. `scripts/build_onboarding.py` converts those into the Android
VectorDrawables (`res/drawable` for light, `res/drawable-night` for dark). Do not edit the
drawables by hand.

```powershell
python scripts/build_onboarding.py
```

No bitmap dependency or network artwork fetch is needed in the app. The launcher/store icon is a
separate asset.
