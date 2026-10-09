# TestTrack

TestTrack helps Android developers run closed testing crews and exchange private app feedback.
The Android app is built with Kotlin and Compose. Records live in the TestTrack API; Firebase is
used for Google sign-in, push and app configuration. The Firestore rules in this repository protect
retained legacy records.

Circle contains feedback requests, owner approvals/disputes and community activity. Crew contains
open crews, your lobby, nightly testing and completed runs. Ranks shows community standing; My apps
and Profile show the signed-in account's records. Guests can browse public screens before signing in.

Feedback is private for both live apps and closed tests. A reviewer installs through Play, uses the
app, writes feedback in TestTrack, and submits a screenshot from the tested app. Tix reward accepted
private feedback; the flow does not ask for a rewarded public Play rating. Eligible accounts receive
60 starter tix once per verified account and reported installation.

Proof and custom profile images upload directly to the signed-in user's connected Google Drive.
The API keeps references/metadata, not new image bytes. **Anyone with a proof link can view it.**
The developer and authorized dispute jurors receive that link. Inspect screenshots and hide personal
details locally before sending. You own the files and can remove them or their public-link permission
in Drive; disconnecting Drive alone does not remove public access. Pending proof and notes stay on
this device until sent or abandoned. Crew visits send testing records and usage duration, not images.

The phone reports installation and UsageStats measurements; these are not hardware-attested facts.
The server enforces elapsed-time limits, membership, permissions, state transitions and payments.
Historical backend-upload proof remains readable through its access-controlled compatibility route.

Read [current architecture](docs/architecture.md), [app rules](docs/app-rules.md) and
[verification](docs/verification.md) before changing the app. Historical planning documents are
labelled as plans and may describe superseded behavior. Never commit local credentials, signing
keys, tokens or private backend configuration to this public repository.

Local checks:

```powershell
python scripts/check_app.py
.\gradlew.bat ktlintCheck testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin lintDebug
```

These compile instrumented tests; they do not execute them. The release hook also checks store
assets, notes, Data safety and Play access through mkit. See [store instructions](store/README.md).
Push, deployment, Firebase writes and Play publication require explicit authorization.

[Privacy](https://eazyverse.com/apps/testtrack/privacy) ·
[Request account deletion](https://eazyverse.com/apps/testtrack/delete-account)

Account deletion is a processed request with retention details, not an instant delete-everything
button. Review the current policy and request page before submitting a request.
