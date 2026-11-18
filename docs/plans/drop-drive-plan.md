# Drop Google Drive, keep pictures on our VPS, prune finished crews

Owner decisions 2026-11-18. Planner (Opus) read both repos and the admin site.

## Fixed by the owner
1. Remove Google Drive completely. All pictures go to our VPS.
2. While a crew runs, everything works as now (one picture per app per night, proof, checks, queue, retries). Small changes are fine, not a redesign. Never drop proof.
3. 3 days after a crew ends, delete all images and heavy data. Keep forever, per user per crew: crew name, start/end, days; their app (name, package, icon); nights tested of run; missed / let off / reported nights; daily rate; finished or left (day); their app's result (testers stayed, counted vs 12); tix spent and refunded.
4. The finished crew page later gets 2 views: days 0-3 full detail, then a slim summary from the kept stats.

## Approach
- Phone mints a UUID, saves it, then `PUT /api/v1/uploads/{uuid}?kind=visit|crew_feedback|review[&seat_id=]` with raw bytes (no multipart). Then calls the same JSON endpoint as today with the UUID as `proof_ref`. Retry safe: same id + same owner = 200, other owner = 409.
- Server decodes with Pillow (max 40 MP), exif_transpose, re-encodes WebP: crew 720 px wide q70, review max 1080x2400 q80. Only our bytes are kept. Files on volume `testtrack_uploads` at `/srv/uploads/crew/<crew_id>/<id>.webp` and `circle/<yyyy-mm>/<id>.webp`.
- Bare UUID refs already display in old builds and the admin panel (`proofSource`, `GET /uploads/{id}`).
- `GET /uploads/{id}`: `may_see` by kind (visit: visited app's owner; crew_feedback: team only; review: sender, reviewer, post owner, eligible jurors) plus `Cache-Control: private, max-age=604800, immutable`. New `GET /admin/proofs/{id}` for staff (panel keeps `fresh()`). API hosts are grey-cloud; check no `cf-ray`.
- Phone shrinks crew pictures to 720 px WebP q70 before upload (`VisitCameraService.save`, new `ProofImages.visit`).
- Disk guard: `uploads_min_free_bytes` 3 GB; below it uploads get 507 `storage_full` and the phone keeps them queued (507 not in `PictureQueue.refusedForGood`). Sweeper warns below 5 GB.
- Budget: 2,940 pictures per 14-night crew; ~206 MB per crew at ~70 KB; 10 running + 3 in the window = ~2.7 GB.
- Ops: `backup.sh` must exclude `crew/`; live nginx `client_max_body_size` at least 5m.

## Kept stats and prune
- New `crew_records` (one row per member per crew, unique crew_id+user_id): crew number/name, started_at, ended_at, run_days, member_nights; app name/package/icon; nights kept/missed/let_off/reported/short; opens owed/on_time/counted, rate_pct; outcome (finished/left/removed/moved), left_day; testers_stayed, testers_every_day, counted; tix_spent, tix_refunded; perfect, first7_kept. `opens_counted`, `perfect`, `first7_kept` are needed by Ranks and Activity streaks.
- `crews.done_at`, `crews.pruned_at`; `visit_daily_counts(day, opens)` keeps the admin insights chart.
- Builder `services/crew_records.py` reuses admin_crew_insights pieces; same function for live view and snapshot.
- Prune (sweeper stage, off by default, `PRUNE_AFTER_DAYS = 3`, one crew per pass, one transaction with crew lock): upsert records, add daily counts, delete visits / play_feedback / uploads for the crew, set pruned_at; after commit remove the folder. Keep crews, seats, seat_appeals, tix_ledger, night_excuses, seat_absences, seat_verdicts.
- Readers that switch to records for pruned crews: standing tallies (Ranks), activity streaks, admin member records, admin crews / seat detail / insights, crew_view progress, visit_pictures.for_owner (410 `details_gone`), admin insights visits. `admin_member_delete` also deletes records and files.
- API: `GET /crews/{id}` adds `done_at`, `details_until`, `pruned`; new `GET /crews/{id}/record`, `GET /me/crew-records`.
- `scripts/backfill_done_at.py` for existing crews.

## Moving off Drive
- P0 backend accepts both: `drive:` refs via `drive.verify`, UUIDs via `uploads.claim`; `drive_token` fields optional (default "").
- P1 app release with no Drive. Internal first, production on the owner's word.
- P2 `scripts/migrate_drive_refs.py` copies Drive thumbnails (public `thumbnail?id=&sz=w1600`, ~2/s, resumable, dry-run default) for reviews, running crews, and Drive profile photos into `profile_photos`. Dev first.
- P3 owner sets `minSupportedBuild`.
- P4 ~3 days later rerun P2, then `drive:` gets 426 `update_app` (old builds keep the picture), remove Drive code and fields.
- P5 prune on, after `prune_report` dry run and a pg_dump.
- P6 owner: remove `drive.file` scope in Google Cloud, Play Data safety, site privacy text.
- Phones updating: non-UUID reserved ids become empty and get a new id; `drive:` in `ProofStore.uploaded` is ignored and re-uploaded from the local file.

## Pieces
Backend (`H:\_group_projects\test_track_backend`, dev branches from `infra/dev-first` once pushed, dev deploy only):
- B1 storage: migration 0033 (uploads path/crew_id/subject_id/claimed_at, crews done_at/pruned_at, crew_records, visit_daily_counts), `services/uploads.py` store/claim/may_see/path_of, PUT route, admin_proofs, body_limit per path, config keys. Tests: oversize, fake image, bomb, repeat PUT, foreign owner, claim rules, may_see per kind, 401, 507, EXIF gone.
- B2 six write flows accept UUID refs via `services/proof_refs.accept` (feed.submit, review_check, dispute_evidence.bind_picture, visits.attach_picture with retake delete, crew_rules.send_feedback). Drive tests stay.
- B3 records builder, done_at set at close/delete, record endpoints, backfill script. Tests: perfect run, taken out day 6, moved seat, extended run, cancelled before fill, under floor; live equals snapshot.
- B4 readers use records when pruned. Test: snapshot, delete visits, output unchanged (Ranks, streaks, admin records).
- B5 prune job + `scripts/prune_report.py`. Tests: idempotent, not before 3 days, never a running crew, files gone, leftovers cleaned, daily counts.
- B6 `migrate_drive_refs.py` + inventory counts.
- B7 retire Drive (after P3 + ~3 days).
- B8 ops: backup exclude, nginx, df.

App (this repo; compile + touched tests only):
- A1 `net/PictureApi.kt` (Api.kt is at 499 lines), `data/ServerUpload.kt` with 507/426 lines, drop `drive_token` in wire.
- A2 crew pictures: VisitPictures, VisitPicturesWork, NightPictures, CrewExtrasViewModel, 720 px WebP. Tests: 426/507 retry, ProofImages width <= 720.
- A3 review pictures: ReviewSend, ProofUpload, ProofStore UUID; remove checkDrive.
- A4 remove Drive UI and access (DriveRepo, TestingDriveViewModel, JoinStep.DRIVE, join guide, open tab, seat screen/details, picture asks, Me screens, debug, Session, AuthRepo Drive parts, Config, ReviewScreen/ReviewProof DriveConnectCard, ProofEditor/CrewPicturesDue/CrewRunPictureUpdates text). Keep `drive:` display in `proofSource` until P4 is done. Update Drive tests.
- A5 docs and store text.

Website/admin (`H:\next\eazyverse`, dev first, live on "promote"):
- W1 admin panel proofs by UUID, "Details removed after 3 days" + record summary.
- W2 privacy, delete-account, delete-data, description, lib/data.ts:140.

Order: B1, B2, dev. Then B3-B5 in parallel with A1-A5. Promote B1-B4 (B5 shipped off). App internal, then production on the word. P2, P3, B7. Prune report, B8, prune on. W1 with B4, W2 + Play/Google changes with the production app.

## Risks
- Ranks points, Activity streaks and admin records read visit rows: B4 before any prune.
- Old builds drop pictures on 400/403/404/409/422: retire Drive with 426.
- Backup copies uploads onto the same disk.
- Prune is permanent: dry run and pg_dump first.
- Users' own Drive copies stay; privacy text says they can delete them.
- Blockers before coding: the owner's uncommitted app work touches the same files; backend `infra/dev-first` is ~20 commits ahead of `origin/dev` with uncommitted `app/core/rules.py`.

## Owner answers 2026-11-18 (these override the plan above)
- STILL PLANNING. Build nothing until the owner says.
- Rolling picture window: while a crew runs, keep only the last 3 days of visit pictures; older ones are deleted daily (rows stay until the crew prune). Then everything heavy goes 3 days after the crew ends as planned.
- App: the next release removes Drive completely (no picture display via Drive needed beyond plain links, no Drive UI, no scope). It revokes the old Drive grant on first launch.
- Server: keeps accepting `drive:` refs until the owner says stop (no fixed P4 date; 426 only on the owner's word).
- No copying: do NOT copy Drive pictures to the VPS (drop B6 / P2). New pictures go to the VPS; old pictures keep their Drive links as they are. Existing VPS files also stay as they are.
- Circle review proofs are deleted when the review is approved or its dispute completes.
- Low-disk alert goes to the Telegram bot.
- Small night rows (night_excuses, seat_absences, seat_verdicts): keep (planner default, not contested).

## Storage with the rolling 3-day window
15 members x 14 apps x 3 nights = 630 pictures per crew x ~70 KB = ~45 MB per crew; 10 crews ~0.45 GB (+ finished crews in their 3-day window, same size).
