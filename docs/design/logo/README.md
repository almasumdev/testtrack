# Test Exchange

The selected robot redrawn as editable vector paths. The paired app cards, checks and arrows
represent reciprocal app testing. Geometry and gradients have one source, `test-exchange.svg`.
The SVG has a transparent background and no embedded raster image.

Open `preview.html` for a replayable artwork, light splash and dark splash comparison.
`test-exchange-animated.svg` also plays the motion once when opened in a browser.

Regenerate the Android resources and browser assets after editing the source:

```powershell
python scripts/build_splash.py
```

## Native launch

- Android 12 / API 31 and newer use a platform AnimatedVectorDrawable.
- Android 8–11 use the same vector in its completed pose through AndroidX SplashScreen.
- The 900 ms animation pops the robot, wobbles the antenna, blinks the eyes, tilts the cards,
  turns the exchange arrows slightly and draws the two checks. It does not loop.
- A 288 dp transparent icon canvas uses a 0.78 scale around its centre to keep the visible
  artwork inside Android's 192 dp circular safe area, including the slight overshoot.
- The native background matches Compose: `#E9F0F4` in light mode, `#0F1011` in dark mode.
- `installSplashScreen()` runs before `super.onCreate()`. App composition proceeds underneath
  the splash. The exit listener finishes only the unplayed part of the icon, capped at 900 ms,
  then fades the splash for 120 ms. No network request gates the splash.
- Missing animation metadata, pre-Android-12 devices and disabled system animations skip
  the animation wait. Normal resumes do not create a second in-app splash.

This change supplies the SVG and native splash. Launcher/store icon assets are separate.

Platform reference: [Android splash screen guidance](https://developer.android.com/develop/ui/views/launch/splash-screen).

## Verified locally

- Android 17 / API 37 emulator: animated cold launch in light and dark modes, disabled
  system animations, and returning to an existing activity. No crash-buffer entries.
- Android 10 / API 29 emulator: the complete static artwork appears before the app opens.
- 87 unit tests pass, including missing/expired splash metadata and the maximum animation wait.
- `ktlintCheck`, `assembleDebug`, `compileDebugAndroidTestKotlin` and the architecture checker pass.
  Instrumentation tests were compiled; they were not run.
- Browser preview: no JavaScript errors; reduced motion produces zero active animations.

Review files: [artwork and motion](preview.html), [animated SVG](test-exchange-animated.svg),
[native light launch](native-splash-light.mp4), [native dark launch](native-splash-dark.mp4),
[disabled animations](native-splash-reduced.mp4), [Android 10 fallback](native-splash-android10.mp4).
