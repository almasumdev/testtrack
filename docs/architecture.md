# Current architecture

How the app is built and how it talks to the TestTrack API. The crew and feedback planning files
next to this one record design history; where they disagree with this page, this page is right.

## App and API

Compose screens use view models and the wire client in net/. The API owns membership, crew seating,
deadlines, review state, eligibility and the tix ledger. The app reports Play installation and
UsageStats; those reports and its installation hash are not hardware attestation. The server caps
feedback usage by elapsed server time and bounds submitted durations.

Public feed/activity, open crews and ranking are readable without an account. Personal apps,
profile, review evidence, crew detail and moderation require the appropriate authenticated identity.
Guest screens ask for sign-in instead of describing an unqueried account as empty.

Firebase provides Google sign-in, FCM and update configuration. Current product records use the API,
not the legacy Firestore cohorts. Retained Firestore users are private to their owner/admin;
publicProfiles exposes only safe names/photos. Older clients must migrate to that public collection
or upgrade before the tightened rules are deployed.

## Sessions

The app exchanges a Firebase Google ID token for an access/refresh pair. Access is normally fifteen
minutes; refresh is normally thirty days. The API requires a verified Google provider, signature,
audience/issuer and active database session. Admin access is tied to stable Firebase UIDs.

Refresh is serialized in the app and locked on the server. A rotation retires the previous pair.
Logout revokes the whole rotation family. Android clears local state immediately and persists an
offline revocation job using WorkManager, retrying when connected. Session generation prevents an
in-flight response restoring a signed-out account. Tokens are private app preferences, excluded from
backup. A connected socket rechecks authorization/expiry every ten seconds.

## Feedback and images

Both live and closed apps receive private written feedback. Live/closed controls listing and
installation eligibility; neither asks for compensated public ratings. Install, use, feedback,
proof and waiting are the guided steps. The capture button opens the tested app. The proof editor
supports inspection, zoom and opaque pixel masks; JPEG export strips source metadata and bounds size.

Proof and custom profile images upload directly to the user's Drive with the narrow drive.file grant.
Files receive anyone-with-link read access, disclosed before upload. The API receives only a Drive
reference and transient grant, checks metadata/existence/ownership/size/permission, and binds a proof
file to one review. It does not persist the grant or new image bytes. Existing backend-upload proof
has an access-controlled read path; new POST /uploads writes are retired with no fallback.

Pending feedback/proof is saved locally. A reserved Drive file ID is persisted before upload, so
uncertain responses retry the same file. Submission is separately idempotent. Posting retries keep
an immutable request ID while the form is unchanged. Replacing an already-uploaded screenshot can
leave a discarded user-owned Drive file; users can remove it from the TestTrack Proofs folder.

The backend sweeper runs feedback/claim/dispute deadlines, crew reminders, missed days and closure.
Those are implemented jobs, not missing features. Activity comes from stored events/records.

## Crews and live updates

Crew visits store the seat, visitor, local day and usage duration. They do not send screenshots.
The backend bounds visit writes separately from general changes and sign-in attempts. Google group
membership uses a mirrored roster with a separate refresh transaction and shared refresh lock;
a stale positive is re-read after refresh. Joining remains the user's own action.

Events and push notifications enter a durable transaction outbox and are retried after commit.
Shared feed events contain IDs; private evidence is fetched through authorized endpoints. Redis
subscription is supervised. Android refetches on reconnect/sequence gaps and periodically while a
screen is in the foreground; events are invalidations, not an ordered source of truth.

## Checks and release

Local checks run the architecture and theme checker, ktlint, unit tests, a debug build,
instrumented-test compilation and lint; instrumented tests run on an emulator. The architecture
baseline (`scripts/app-baseline.txt`) lists the known violations and may only shrink. See
[verification.md](verification.md) for the commands. The API lives in a separate, private
repository.
