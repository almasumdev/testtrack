# Store files

The Google Play listing for `com.eazyverse.testtrack`, kept beside the code so a change to the app and
its listing can land together. Publishing uses the maintainer's release tool (mkit, configured in
`../mkit.toml`); nothing here is needed to build or run the app.

| Path | What it is |
|---|---|
| `listing/en.md` | Title, short and full description (`en-US`) |
| `whatsnew/whatsnew-en-US` | Release notes for the next version |
| `art/` | Raw screenshots, captions, layout and fonts the store art is rendered from |
| `data-safety.md` | Play Data safety answers, with the code that backs each one |
| `datasafety.csv` | The same answers in Play Console's import format |

The icon comes from `app/src/main/ic_launcher-playstore.png`. Rendered screenshots, the feature
graphic and listing backups are built locally and gitignored.

## Before a release

`python scripts/check_release.py` runs the app's checks (architecture, formatting, unit tests, a
debug build, instrumented-test compilation and lint) and then validates the release notes, listing,
Data safety answers and artwork. It never runs device tests or publishes anything.

- Replace `whatsnew/whatsnew-en-US` with the new version's changes.
- Capture raw screenshots at 1080 × 2340 from a release build, signed out, and replace a capture
  whenever its screen changes.
- Rebuild `datasafety.csv` whenever the answers in `data-safety.md` change.

To build a minified release locally without uploading a Crashlytics mapping, run
`./gradlew :app:assembleRelease -x :app:uploadCrashlyticsMappingFileRelease`.
