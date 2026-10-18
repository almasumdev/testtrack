# TestTrack onboarding

Open `preview.html` for an HTML preview of the five pages in both themes.

The introduction is an illustrated pager: brand and Skip,
large artwork, a short headline and explanation, progress dots, a primary action, and an
existing-account sign-in link. Every page keeps TestTrack's Ocean palette. There are no per-page
background or button color changes. Coral and mint are small, recurring illustration accents.

## Pages and behavior

1. Testing together: the approved exchange robot with a circle of developers.
2. Useful feedback: an app session, observation and response, with the same mascot.
3. Daily progress: a testing calendar, check-ins and the mascot.
4. Share your app: a Play link becomes an app listing, with the mascot and a lookup magnifier.
5. Explore first: an open community window and compass introduce guest browsing before sign-in.

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
- Artwork shrinks on smaller screens. Large text scrolls within the slide; controls remain available.
  Text is never scaled down to fit. Artwork is decorative to accessibility services; dots announce
  the current page. The controls and copy use native text, not text inside illustrations.

## Editable assets

`scripts/build_onboarding.py` builds `together.svg`, `feedback.svg`, `progress.svg`, `share.svg`,
`explore.svg`, and the matching
Android VectorDrawables. It reuses the canonical robot from `../logo/test-exchange.svg`, preserving
the complete app cards and foreground arrow tips. The splash and onboarding robot use the app's Ocean blue.

```powershell
python scripts/build_splash.py
python scripts/build_onboarding.py
```

No bitmap dependency or network artwork fetch is needed in the app. The launcher/store icon is a
separate asset.
