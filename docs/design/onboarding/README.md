# TestTrack onboarding

Open `preview.html` for the live HTML design preview of the new gradient background in both themes.
`preview.png` and `preview-dark.png` are earlier native review sheets, before the background update.

The introduction follows ListKin and ValueGuard's illustrated pager structure: brand and Skip,
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
the complete app cards and foreground arrow tips. The splash and onboarding robot now use the
app's Ocean blue. The approved 900 ms splash animation is unchanged.

```powershell
python scripts/build_splash.py
python scripts/build_onboarding.py
```

No bitmap dependency or network artwork fetch is needed in the app. Browser review screenshots
are documentation artifacts, not app resources. The launcher/store icon is a separate asset.

## Verification (2026-10-03)

- API 37 emulator: the original three-page flow in light/dark, swipe, Next, Android Back, sign-in sheet and
  dismiss, completion and relaunch, Skip, and a cold-launch notification retained through the tour.
- Compact 320 dp-wide display and 200% system font size: artwork adapts and slide content scrolls.
- System animator duration scale 0: Next reaches the following page without a pager animation.
- `ktlintCheck`, `assembleDebug`, `testDebugUnitTest`, and `compileDebugAndroidTestKotlin` pass.
  There are 90 passing unit tests, including launch routing for new, returning and expired accounts.
- `scripts/check_app.py`: no new violations; the 40 pre-existing violations remain.
- Instrumentation tests were compiled only. Live Google sign-in was not performed during this review.
- Browser gallery: both themes load, all images resolve, and no JavaScript errors were reported.

Only the emulator was used. Existing emulator session preferences were backed up and restored.

## Five-page extension

Pages four and five add app lookup/sharing and guest exploration. The page count, dots, Back
behavior and final action are driven by the same page list. Existing completion flags remain valid;
people who already finished the introduction are not shown it again just because it gained pages.
The gallery now includes all five native screens in both themes.

Validation: debug build and Kotlin lint pass, no new architecture violations. Manual emulator review
checks all five progress indicators, Next on pages one through four, the final action on page five,
Back through the new pages, and persisted completion. Existing emulator session data is restored.
The earlier small-screen and large-text captures document the shared layout before this extension.

The app now shares `ui/theme/ExchangeBackground.kt` across its main tabs, onboarding, forms
and detail screens. A soft Ocean radial gradient replaces the old decorative shapes.
The gradient palette stays consistent; cards and dialogs retain solid surfaces for readability.
This update was build-checked only; visual review is left to the user.
