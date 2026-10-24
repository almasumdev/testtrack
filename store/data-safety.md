# Data safety evidence

What the app collects, why, and the code that backs each Play Data safety answer. The answers cover
release builds. `datasafety.csv` is generated from the declaration in `../mkit.toml`.

| Declared type | Evidence and purpose |
| --- | --- |
| Name, email and user IDs | Google/Firebase sign-in, API accounts, public names and legacy cohort identity/member rows. Account management and functionality; account/device comparisons prevent duplicate testing. |
| Photos | Optional. Feedback screenshots upload directly to the user's Drive; the backend verifies transient-grant metadata and stores references only, and new Drive images are explicitly shared with anyone who has the link. A chosen profile photo is cropped on the phone and sent to the backend (`PUT /me/photo`), which redraws it as a 400 px JPEG and serves it publicly by its hash. Legacy Drive and historical backend images remain readable. Owners and eligible jurors can read feedback evidence. |
| Other user-generated content | Written feedback, rejection reasons, crew entry notes and disputes, displayed to the people handling that work. |
| Installed apps | Named package install/source/version checks through `InstalledApps`, review milestones and legacy version/proof records. The whole installed-package inventory is not uploaded. |
| In-app search history | `AskViewModel` sends lookup queries to `/apps/lookup`; the lookup service does not persist them. Optional, ephemeral collection. |
| App interactions | Target-app foreground time, visits, testing records and Firebase Analytics events. Testing results are visible to other members and app owners. |
| Device or other IDs | Salted `ANDROID_ID`, backend sessions and Firebase Auth/Analytics/Crashlytics/Messaging identifiers. Functionality, analytics and fraud prevention. |
| Approximate location | Firebase Analytics derives coarse geography from masked IP addresses. The app has no location permission and does not read GPS. |
| Crash logs and diagnostics | Release builds enable Crashlytics and Analytics; `Telemetry` records caught failures and counts. |

The existing declarations include sharing of identity, photos, testing signals and written
content. Retained private account email fields now have owner/admin Firestore access;
current account identity uses the backend. Reconcile the coordinated release and any remaining
legacy clients before narrowing a published declaration. Google excludes some expected user-initiated
transfers from mandatory sharing disclosure; the mixed legacy/backend flows do
not provide one consent boundary covering every transfer, so these transfers
are included. Firebase and hosting are service providers. Transfers to them
are collection, not a separate advertising or resale purpose. No purchase or
advertising flow exists in TestTrack.

Configured network endpoints use HTTPS. Chosen photos, written content and
lookup queries are optional. SDK diagnostics/identifiers are not marked
optional because release collection has no in-app opt-out control.

The deletion URLs point at the existing request forms. `deletion = "yes"`
means users can request deletion, not that an in-app deletion endpoint exists.
The privacy page must cover backend records, historical uploads and publicly linked Drive images.
The app describes a manual request rather than instant deletion; the operator procedure in the
backend operations guide still requires a cross-store rehearsal and retention-policy review.

References:

- [Google Play Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Firebase Android disclosures](https://firebase.google.com/docs/android/play-data-disclosure)
- [Google Analytics for Firebase disclosures](https://support.google.com/analytics/answer/11582702)
