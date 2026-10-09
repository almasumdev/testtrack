# Verification

Run the architecture checker, ktlint, unit tests, debug build, instrumented-test Kotlin compilation
and lint before committing. Run ktlintFormat separately if needed, then check; combining format and
check in one Gradle invocation can race. Compile-only checks do not establish an instrumented pass.

The 10 October fixes passed the above sequence. Lint has zero errors; warnings remain. The Firestore
emulator suite protects retained private account fields and safe publicProfiles, and npm audit reports
zero advisories for the pinned/overridden test dependency tree. Run `npm test` from firestore-tests
with a supported Java runtime to reproduce the rules checks; this does not publish rules.

The compact UI was inspected on emulator-5554 at 320×640 dp and 200% text. My apps/Profile actions
are reachable by scrolling, the brand stays on one line, and all five navigation targets meet
48 dp. Inspect other small screens, landscape, both themes, focus/TalkBack and real filled/error
states before accessibility sign-off. Do not use a connected physical phone for these checks.

The local debug proof fixture exercises the actual editor with invented email/private-note pixels.
Its saved JPEG was checked for opaque black pixels across the sensitive region and zero EXIF fields.
The editor scrolls at 200% text while retaining image space. ProofFixtureActivity is a debug-only
component and was verified absent from the signed/minified release manifest.

## Authenticated staging journeys

Use an isolated API/database and test Google account. The backend seed script creates privileged
fixture sessions only; it does not bypass Drive verification. ReviewJourneyTest now requires two
real owned Drive proof references (liveDriveProof/closedDriveProof), a short-lived driveToken, and
a fixture reviewer whose email matches that account. The closed-test account must have verified
membership in the staging mirror. Missing credentials/fixtures skip the journey and must not be
reported as a pass. The server's elapsed-time requirement is observed by actual waits in this test.

Before explicit instrumented execution, pass the required session/post and Drive arguments through
the staging runner. scripts/e2e.sh requires TESTTRACK_STAGING_ACK=isolated-staging,
TESTTRACK_REVIEWER_EMAIL, TESTTRACK_DRIVE_TOKEN, TESTTRACK_LIVE_DRIVE_PROOF and
TESTTRACK_CLOSED_DRIVE_PROOF; its backend seed uses --reviewer-email. Only emulator-5554 is allowed.
Keep tokens out of commits/logs and do not point the fixture seeder at production.
A successful API fixture journey still does not test Android permission consent or real UsageStats.

Manually exercise Google sign-in/account switching, Drive disconnected/denied/reconnected, gallery
and capture permission cancellation, preview zoom/redaction, image retry and process death, backend
rejection/retry, approval/dispute privacy and logout revocation. Confirm the backend stores metadata
only, no screenshot bytes or Drive grants. Check custom profile upload separately.

## Release

Run `python scripts/check_release.py` for the complete local mkit release hook. It does not publish.
For local release/minification use the documented assembleRelease command with the Crashlytics mapping
upload task excluded.
The local signed/minified APK built successfully and its v2 signature verified; this is build/signature
validation, not an authenticated runtime pass. Play/Firebase writes, push and deployment require
explicit authorization. Publish
only the coordinated client/backend/rules revision after admin/proxy configuration, legacy evidence
compatibility, readiness, production backup and privacy/deletion checks are complete.
