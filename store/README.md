# TestTrack store files

The package is `com.eazyverse.testtrack`. The live listing has one language,
`en-US`; `en` is its file code. The brand title, contact address and website
are retained from the listing pulled on 2026-10-03. The new description
covers the current tabbed app rather than the old cohorts.

## Before a release

Run `python scripts/check_release.py`. mkit also runs it through `pre_deploy`
before changing the version. It checks architecture, Kotlin formatting, unit
tests, debug assembly, instrumented-test compilation, Android lint, release notes, listing
validity/Play access, Data safety and artwork, then rebuilds the CSV and local
images. Listing differences are expected; only validation failures block it.
It never runs device tests.

Replace `whatsnew/whatsnew-en-US` with the next release's changes. If a locale
is added, add its listing, captions and release note in the same change.
The current note covers the new tab and design changes after 1.5.11.

The icon comes directly from `app/src/main/ic_launcher-playstore.png`.
Raw screenshots are in `art/shots`; the design and captions live beside them.
Capture at 1080 × 2340; this is the input size required by mkit's renderer.
Barlow font copies use mkit's filenames; their source and license are the
app's `res/font` resources and `docs/licenses/Barlow-OFL.txt`.
Built screenshots and feature graphics are ignored and rebuilt by `mkit art build`.
Replace the raw capture when its screen changes. Use real release captures;
the debug Ranks sample board must never appear in store artwork.

For a local minified build, use
`./gradlew :app:assembleRelease -x :app:uploadCrashlyticsMappingFileRelease`.
Gradle otherwise uploads the
mapping automatically even though it is only building an APK. A release build
passed during this setup; its mapping upload ran before that exclusion was
identified. No Play release or Firebase configuration was changed.

The captures show the signed-out release app. No debug sample board is used.
The public backend snapshot includes demo/test accounts, so captions identify
the screen content as illustrative rather than evidence of active testers.
The art matches the Ocean palette and shows Circle in both themes plus Ranks.
It makes no claim that TestTrack guarantees Play production access. The app's
tab names and actual jobs are the listing's keyword vocabulary.

## Data safety

`mkit.toml` holds the declaration; `mkit datasafety build` regenerates the CSV.
See [data-safety.md](data-safety.md) for the code evidence and SDK disclosures.
Rebuild the CSV whenever those answers change. `mkit datasafety check` checks
the form, not whether the product fulfils Play's account-deletion requirements.

## Looking and publishing

`mkit doctor`, `mkit listing plan`, `mkit whatsnew check`, `mkit datasafety check`
and `mkit art check` inspect the setup. `mkit art build` writes local images.
Listing backups are stored under `backups/` and ignored by git.
Open [preview.html](preview.html) after building to review the local images.

Only an explicit user request authorizes `mkit deploy`, listing or Data safety
publication, Firebase writes, promotion or pushing. Deploy publishes a release,
Firestore rules and the update document, sends Telegram, and by default pushes
its release commits. Configuring these steps does not authorize running them.

## Product work still needed before publishing the new app

Confirm the public privacy/deletion pages cover current API records and user-owned Drive images.
Account deletion remains a processed website request with documented retention, not an instant
self-service backend delete. Activity uses stored records; the backend sweeper handles review
deadlines and crew closure. Those features are implemented. Guest crew counts come from the API.
The audit fixes replace rewarded public-rating instructions with private feedback and require
transient Drive verification. Release the matching backend/migration and app together, and check
real Google/Drive, capture consent, recovery, signed builds and current Play declarations before
publication. Local checks do not establish production behavior. See docs/verification.md.
