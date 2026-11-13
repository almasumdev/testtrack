# Verification

## Before every commit

```powershell
python scripts/check_app.py
.\gradlew.bat ktlintCheck testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin lintDebug
```

`check_app.py` enforces the layering and file-size rules in [app-rules.md](app-rules.md). Run
`ktlintFormat` on its own before `ktlintCheck` if formatting needs fixing; running both in one Gradle
invocation can race. These checks compile the instrumented tests but do not run them.

## Instrumented tests

Run them on an emulator, not a personal phone, with system animations switched off (window,
transition and animator scales at 0): several screens animate continuously, and Compose's test
idling never settles while they do.

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Most UI tests draw a screen from plain values and need no account or network. `CrewGalleryTest`
draws every crew state and saves the pictures to the app's external files directory under
`gallery/`, for review rather than assertion.

## Firestore rules

The legacy records kept in Firestore are guarded by `firestore.rules`. Their emulator tests live in
[firestore-tests](../firestore-tests/README.md) and need the Firebase CLI and a JDK.

## Authenticated journeys

`ReviewJourneyTest` and the other journeys under `androidTest/.../net` run against an isolated
staging API with a seeded session and real Drive fixtures. `scripts/e2e.sh` drives them and refuses
to run unless `TESTTRACK_STAGING_ACK=isolated-staging` is set, along with `TESTTRACK_REVIEWER_EMAIL`,
`TESTTRACK_DRIVE_TOKEN`, `TESTTRACK_LIVE_DRIVE_PROOF` and `TESTTRACK_CLOSED_DRIVE_PROOF`. Without
those fixtures the journeys skip, which is not a pass. Keep tokens out of commits and logs, and never
point the seeder at production.

These journeys do not cover Android permission consent or real usage measurement. Check those by
hand: Google sign-in and account switching; Drive disconnected, denied and reconnected; gallery and
capture permission cancellation; proof preview, zoom and redaction; retry after process death; and
sign-out.

## Release builds

Release signing reads the keys listed in `local.properties.example`. Without them only debug builds
are produced.

The release hook (`scripts/check_release.py`) also refuses to run while the live server is behind the
app. It reads `https://testtrack.eazyverse.com/api/v1/version` and compares its schema number with
`TESTTRACK_SERVER_SCHEMA` in `gradle.properties`. Raise that number whenever an app change needs a new
server migration, and promote the backend before releasing the app. If the version can't be read, the
check fails too. `--skip-server-check` skips it for an emergency and prints a warning. The comparison
logic has a self-check: `python scripts/check_release.py --self-test`.

## Emulators

Decided 1 November 2026: an emulator gets a block screen and never reaches our server. It is decided on the phone alone, with no server and no Play Integrity, in `domain/EmulatorSigns.kt`.

Only names and files that belong to emulator products count, because a real phone blocked is worse than an emulator missed. Somebody who changes an emulator to hide them gets past it. The block screen has a mail link that says what the app saw, and each block logs `emulator_blocked` in Analytics with the phone's make and model.

Debug builds and Firebase Test Lab, which runs Play's pre-launch report, are never blocked.

**To lift it for everybody without a release:** set `allowEmulators` to `true` in the Firestore document `config/app`. A blocked screen checks about once a minute and lets the person in. Set it back to `false` to block again. While blocked, the app still reads that one setting and logs to Firebase; it never calls our own API.
