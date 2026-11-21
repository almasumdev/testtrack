# Plan: crew Volunteers and Private testers

Owner's rules 2026-11-21. Backend work: worktree `H:\_group_projects\tt_wt_devall` (feat/dev-all, head 0039_app_descriptions); never `test_track_backend` (on feat/vps-pictures). DEV only. UI per `docs/design/crew-volunteers/flow.html`.

## Approach
- Volunteers: new table `crew_volunteers` (not seats). Their opens go into `visits` (visitor_id = volunteer, seat_id = app opened), so owner grid, pictures, board, records barely change. Judging reuses `visits.Nights` with different inputs. Pay via `tix.move` with once-per-cause key.
- Private testers: tables `private_testers`, `private_visits`, routes `/v1/private-tests`, upload kind `private_visit`. Never touch `visits`, so grid/misses/records/skips/votes can't see them. In-force check on every read/write: X's seat CONFIRMED, X still owns it, crew FULL; sweep ends stale rows and tells Y.

## Rules and defaults
1. Volunteers join only once the run has begun (`crews.begun()`), not while FULL waiting for midnight.
2. Volunteer hard rule: a night is a miss if ANY owed app was left unopened (not the members' 3+ rule). One miss = out. Full night = 5 tix. Open recorded but picture pending counts as opened for the miss check; paid once the picture lands within grace. Picture never lands = neither paid nor miss. Night with nothing owed = neither.
3. Leaving is final: out/left/released never rejoin that crew (unique `(crew_id,user_id)` over all states).
4. No Play feedback rule for volunteers (10 s rule, Play copy and latest update still apply).
5. Volunteers count as testers for the owner (grid + Google 12 count, Volunteer tag).
6. Private tester open (owner-approved design): 10 s in front + Play copy, NO picture; no miss/update/feedback rule. So P3 has no picture route and U only needs the chat crew_id part (no private_visit kind).
7. Declining an invite is final for that app in that run.
8. Seat changing hands or X taken out ends private testers; put back does not resume them.
9. Crew closes only after every active volunteer's last night is over.
10. Volunteers have full chat (read + send). No reports, votes, appeals, quiet skips.

Pending owner (recommended defaults): A release newest volunteer when an app comes back into a 15-full crew; B 1 volunteer crew per account and per phone at a time; C honest "No TestTrack account uses that email" with 10/hour, 30/day invite limits; D no join/leave bot lines in chat v1; E admin endpoints only, panel UI later.

## Backend pieces (files <= 500 lines; ruff, mypy --strict, own tests; DEV only)

### B1 Volunteer foundation
- `app/db/models/volunteer.py`: `VolunteerState` active/left/out/released/removed/done. `CrewVolunteer` (`crew_volunteers`): id UUIDv7, crew_id FK CASCADE, user_id FK RESTRICT, device_fp String(64), timezone String(64), state String(16), joined_at, first_night int, ended_at null, last_night null (last whole night; null while active), end_reason String(280), judged_through int 0, reminded_day int 0. Indexes: unique(crew_id,user_id); FK indexes; partial active (crew_id); partial unique (crew_id,device_fp) active; if B=1 partial unique (user_id) active and (device_fp) active. Export in models/__init__.
- `alembic/versions/0040_crew_volunteers.py` (down 0039): table + `crew_chat_messages.author_volunteer_id` (FK SET NULL, indexed) with downgrade; model column in crew_chat.py.
- `app/repositories/volunteers.py`: add, locked, of_crew, active_count, of_user_in_crew, active_of_user, device_in_crew (any state), to_judge(since), active_user_ids. EXPLAIN comments.
- `app/core/rules.py`: VOLUNTEER_NIGHT_TIX=5, VOLUNTEER_PAY_GRACE_NIGHTS=2, VOLUNTEERS_AT_ONCE=1, PRIVATE_INVITES_PER_HOUR=10, PRIVATE_INVITES_PER_DAY=30.
- `app/schemas/volunteer.py`: VolunteerIn {crew_id, device_fp, timezone}; VolunteerMeOut {id, state, day, first_night, starts_in_ms, done_today, owed_today, night_done, nights_paid, tix_earned, end_reason, last_night}; VolunteerRowOut {id, user_id, name, photo, email, state, day, first_night, done_today, owed_today, night_done, out_at, opened_mine_today, opened_mine_at, opened_mine_days, opened_mine_picture, opened_mine_by_hand}; VolunteerSpotOut, VolunteerSpotsOut {spots, can_join, blocked}; VolunteerPicturesOut (TesterPicturesOut shape with volunteer_id).
- `app/schemas/crew.py` (defaults empty): CrewDetailOut.volunteers, CrewDetailOut.volunteer, CrewListOut.volunteering, CrewPicturesOut.volunteers. Volunteers NEVER in members/testers (old builds).
- Check migration up/down, scripts/check_routes.py.

### P1 Private-tester foundation
- `app/db/models/private_test.py`: `PrivateTester` (id, crew_id CASCADE, seat_id CASCADE, owner_id RESTRICT, tester_id RESTRICT, state invited/accepted/declined/removed/left/ended, timezone set on accept, answered_at, ended_at, end_reason; partial unique (seat_id,tester_id) where invited/accepted; (tester_id,state); FK indexes). `PrivateVisit` (id, private_tester_id CASCADE, crew_id, seat_id, tester_id, day, used_ms, needs_picture, proof_ref, proof_by_hand, proof_at; unique(private_tester_id, day); FK indexes).
- `0041_private_testers.py` (down 0040): both tables + `ix_users_email_lower` on lower(email).
- `app/repositories/private_tests.py`: add, locked, open_for, declined_before, of_seat, of_tester, sent_since, stale, visits_of, visit_one, delete_for_crew.
- `app/schemas/private_test.py`: InviteIn {seat_id, email}, AcceptIn {timezone}, PrivateTesterOut, PrivateResultsOut {seat_id, app_name, run_days, testers:[... nights: NightPictureOut]}, MyPrivateTestOut {id, state, app_name, package_name, icon, notes, owner_name, crew_name, day, first_night, run_days, opened_today, picture_due}.
- Email lookup: reuse `staff_repo.user_by_email` (staff.py:55). No second lookup.

### B2 Join, leave, read, visits (Codex review: last-spot race)
- `app/services/volunteers.py`:
  - join(db,user,crew_id,device_fp,timezone,now) in order: device_fp present; limits.spend; crew_repo.locked, FULL, not cancelled, begun(), first_day <= run_days; no seat row of any state (seat_repo.latest_mine); no volunteer row (wording per old state); decision B count; same phone via seat_repo.on_device_in_crew (crews.py:131) or volunteer_repo.device_in_crew -> SamePhone; seats counts[0] + active_count < rules.SEATS else Conflict "That crew has no free spots now."; insert first_night = days.first_day(...); end user's open private rows in this crew and tell X; publish crew.changed.
  - leave: lock, state left, ended_at, last_night = min(run_days, seat_day(now)-1), publish.
  - spots: running crews with free spots; icons only, never package names or notes.
  - make_room(db,crew,now): over 15 -> release newest active (released, last_night, push).
  - reader(db,crew_id,user_id) for ws, crew read, uploads.
- `app/services/volunteer_nights.py`: nights_for(...) -> visits.Nights (owed: confirmed seats via visits.first_night + absences.calendar(...).once; opened/waiting from visit_repo.opened_by_day/waiting_by_day; free/off via extra_repo.excuses_for(member_id=v.user_id); gone/optional from calendar; old_rule_through=0; no skips, no reports). progress(...) via visits.progress_on.
- `absences.calendar()` (:68-110): kw `zone`; seatless visitor with zone uses it, own=None (today returns empty Calendar at :86-87).
- Move record + attach_picture from visits.py:246-382 to `app/services/visit_record.py` taking MemberTester(seat) or VolunteerTester(row) (day(crew,now), mine_seat None for volunteers); visits.py re-exports. Resolution: seat via mine_in_crew, then active volunteer, else NotAllowed("Only members of a crew test its apps."). attach_picture also accepts ended volunteer for nights <= last_night.
- crew_rules.check_visit (:136): mine Seat|None, skip feedback branch when None.
- `app/services/volunteer_views.py`: rows(db,crew,me,mine_seat) (opened_mine_* via visit_repo.to_seat when reader owns a seat); crew_for_volunteer(db,crew,v): views.crew_view(members=True, me=v.user_id) then override day/first_night/starts_in_ms, patch opened_today (seats_on_day), picture_due (awaiting_picture), off_tonight/optional_tonight, feedback_due_day=0, skips=0, volunteer=VolunteerMeOut (nights_paid from new tix_repo.count_for_ref_prefix("volunteer_night", f"{v.id}:")).
- views.py (493): one hook line under `if members:` -> view["volunteers"].
- `app/services/crew_visits.py` facade; replace bodies of api/v1/crews.py:280-350 (no added lines).
- api/v1/crews.py: read_crew (:122) seatless with volunteer row -> crew_for_volunteer; list_crews fills volunteering (+ chat badges).
- `app/api/v1/volunteers.py` prefix /volunteers: GET /spots, POST "" (201 CrewDetailOut), POST /{id}/leave; register in router.py.
- ws.py:63 allow crew: channels for volunteer rows via volunteers.reader.
- uploads.py _crew_for (:226-239): seat holder or active volunteer; chat picture accepts optional crew_id when seatless (api/v1/uploads.py:56-57).
- Edges: join mid-night -> first night next midnight; before own day 1 -> first_night 1; last-spot race: crew lock + partial unique; old builds fine.
- Tests: test_volunteer_join.py, test_volunteer_visits.py (every refusal, capacity race, first_night, too late, feedback skipped); tests/api/test_volunteers_api.py (members never holds a volunteer).

### B3 Sweep and payout, full night or out (Codex review: tix)
- `app/services/volunteer_sweep.py` judge(db,now): rows from to_judge(now-(grace+1)d), lock, read crew.
  - Crew DONE and row active -> done, ended_at=done_at, last_night=min(run_days, day_of(done_at)-1).
  - last = min(today-1, run_days, settled, v.last_night if ended). Extract `crew_sweep.settled_day(crew,seats,when)` from enforce:188-196, shared.
  - Misses: d from judged_through+1..last, skip < first_night, pending=True. Miss = owed_on(d) non-empty and short(d) (any owed unopened). Do NOT use Nights.missed(). Active + miss -> out, ended_at=now, last_night=d-1, end_reason "Missed night {d}.", push, publish, stop. Set judged_through=d.
  - Pay: d from max(first_night, today-1-GRACE)..last whole night, pending=False; owed non-empty and all opened+counted -> tix.move(user,+5, reason="volunteer_night", ref_id=f"{v.id}:{d}", note=f"{crew.name}, night {d} as a volunteer"). Pictures pending: not miss, not paid yet; paid later within grace, never after.
  - add_left(db,crew,left,when); all_over(db,crew,when).
- tix.py:24-36 add "volunteer_night" to ONCE_PER_CAUSE.
- crew_sweep.py: remind (:260) calls volunteer_sweep.add_left (v.reminded_day); close_finished (:121) also requires all_over; settled_day extraction.
- sweeper.py _crews (:102): judge after enforce; return volunteers_out, volunteer_nights_paid.
- Edges: leave mid-night -> that night never judged/paid; left at 00:02 still judged/paid through last_night; app marked down/out/night let off -> not owed; nothing owed -> neither; cancelled crew pays finished nights only; leave vs judge both lock the row.
- Tests test_volunteer_sweep.py: nothing opened -> out + spot reopens; exactly 1 unopened -> out; 13/14 -> out; full pays 5 exactly once across two sweeps; ledger == balance; pictures pending -> not out, not paid, paid within grace, not after; marked-down app -> 13/13 paid; let-off night neither; close waits for volunteer zone; cancel; released/DONE finished nights paid.

### B4 Chat (Codex review: access)
- chat_access.py (:15-39): Reader {seat|None, volunteer|None, timezone, live}; reader_seat returns volunteer reader for any state; poster(db,crew,user) -> (seat_id, volunteer_id, timezone) or ChatClosed("out")/NotAllowed.
- crew_chat.py (498, no added lines): :310-318 -> poster; author_volunteer_id to append; :256 and :354 reader.timezone; delete_own (:457) poster; overflow -> chat_post.py (_mentioned, _answer).
- chat_views.py: Roster.volunteers; members() includes them, apps() not; person_out for volunteer seat_id=str(v.id), empty app fields, volunteer true; message_out/_reply author_seat_id = author_seat_id or author_volunteer_id; @[seat:<vid>] resolves, @[app:] seats only.
- chat_push.py:14-18 recipients + active volunteers; chat_limits.py:97 poster; schemas/chat.py person volunteer bool; docs/crew-chat.md.
- Tests: volunteer reads + posts; out/left reads, can't post; mention + push; admin chat with no seat; chat picture via crew_id.

### B5 Owner's grid
- visit_pictures.grid (:43): "volunteers" list (out, out_day=last_night, first_night); owner-only (for_owner :34).
- picture_cleanup._hold (:125-137): add volunteers' zones.
- Tests: owner sees rows; other member 403.

### B6 Admin and hooks (after B2)
- admin_seats _room_for/place (:368-379, :292, :381): make_room after an app goes in; refuse placing/handing to someone (or their phone) volunteering there.
- crew_appeals.put_back (:172): make_room. app_transfer._refused (:78): same refusal. admin_member_delete._take_down (:82): end volunteer (removed) and private rows.
- services/admin_volunteers.py + endpoints in api/v1/admin_crews.py: list, remove with reason, audit.record.
- Tests per hook.

### P2 Invites and lifecycle (Codex review: authorization)
- `app/services/private_tests.py`: live(row,seat,crew) = invited/accepted, seat CONFIRMED, owner matches, handed_to None, crew FULL, not pruned. invite: caller is seat owner; seat CONFIRMED + crew FULL; limits.spend(bucket="private_invite") (limits.py:39-42); daily cap via sent_since; exact email -> NoAccount 404 no_account; not self; target has no seat/volunteer row in crew; no earlier decline; open row returned unchanged. Insert, push Y (kind private, event invite), publish private.changed on Y's user channel. accept(timezone)/decline: caller is tester, invited, live; tell X. remove (X), leave (Y): tell other side. end_stale(db,now).
- `app/api/v1/private_tests.py` /private-tests: POST "", GET /mine, POST /{id}/accept|decline|remove|leave; router.
- sweeper._crews: end_stale. Events only on user channels.
- Tests: refusals (partial email -> no_account), case-insensitive match, limits, only Y answers, ends on take-out/crew end/hand-over, others can't read.

### P3 Private visits and results (Codex review; after P2)
- `app/services/private_visits.py`: record (tester, accepted + live, day 1..run_days by row.timezone, 10 s, Play installer, limits visit, one per day); attach_picture (kind private_visit); results_for_owner (current seat owner and row owner); mine_for_tester.
- `app/api/v1/private_visits.py`: POST /private-tests/{id}/visit, POST /private-tests/{id}/visit/picture, GET /private-tests/seat/{seat_id}.
- Tests: never in grid, crew_view, Nights, records, standing; one per night; others 403.

### U Upload auth (Codex review; after B2 + P1)
- uploads.py: PRIVATE_VISIT in KINDS, CREW_KINDS, limit_for; _crew_for private_test_id (accepted tester, crew not pruned); may_see (:325-350) uploader or row owner_id only. api/v1/uploads.py private_test_id param + Literal (:56).
- Tests: crew member, volunteer, X after seat moved: refused.

### C Cleanup (after P1 + B2)
- crew_prune.py (:108) delete_for_crew; picture_cleanup roll private-visit pictures by VISIT_PICTURE_DAYS. Tests.

Docs: backend docs/volunteers.md, docs/private-testers.md; app docs/crew.md section. Rule wording: one unopened owed app means out.

## App pieces (`app/src/main/java/com/eazyverse/testtrack/`)
Sheets via PickerSheet, results via Snacks, back via shared page bar (arrow_back_ios_new).
- A1 Wire/API: net/VolunteerWire.kt, VolunteerApi.kt (volunteerSpots, volunteer, leaveVolunteer), PrivateWire.kt, PrivateApi.kt as extension fns (Api.kt at 497). CrewsWire: volunteers, volunteer, volunteering, ApiCrewPictures.volunteers (defaults). ChatWire: ApiChatPerson.volunteer. PictureApi: upload crewId, privateTestId; privateVisit, privatePicture. Decode tests incl. old shape.
- A2 Models: CrewModels CrewRole {MEMBER, VOLUNTEER}, Crew.role, Crew.volunteer, Crew.volunteers, CrewMember.volunteer, CrewApp.privateTestId; out/standing/testers role-aware (testers adds active volunteers); volunteer at-risk = any owed app unopened tonight. ui/CrewVolunteerWire.kt ApiCrew.toVolunteerCrew() (confirmed members with firstNight <= day and !offTonight). CrewPicturesViewModel MemberNights.volunteer. CrewTesters CrewTester.volunteer; nightCounts, shortNights include volunteers. Tests.
- A3 VM: CrewViewModel (489) merge volunteering near :237; accept volunteer.changed, private.changed at :463. viewmodel/VolunteerViewModel.kt (spots, join with device fp + zone like SeatIn, leave).
- A4 Volunteer screens: spots list, join sheet (rules + GroupGate/GroupSheet), volunteer crew page (tonight's apps, progress, nights paid, tix, Leave confirm), out/left/released states. ui/VolunteerSpots.kt, VolunteerJoinSheet.kt, CrewVolunteerPage.kt + role branches in CrewPage/CrewPhases.
- A5 Members' view: Volunteer tag in CrewMembersPage, CrewMemberProfile, CrewTrackingGrid/CrewTesterTile (counted), chat people/bubbles/mentions. CrewChatSend.kt:145 sends crewId when seatless.
- A6 Night round: NightRound.kt:355-370 privateTestId -> Api.privateVisit; VisitPictures Waiting.privateTestId (kind private_visit); RoundStore Stop field; ServerUpload PRIVATE_VISIT. Private apps never in doneTonightIn or crew night.
- A7 Private screens: owner from seat page (exact email + Add, list with states, remove, results via TrackingGrid without Google 12 line); tester invite card (Accept/Decline + group check), "Testing for others", Open starts a round. viewmodel/PrivateTestsViewModel.kt. PushService.kt:79-91 route kind private.

Copy: Join "Open every app in this crew every night. Each full night earns 5 tix. Leave even one app unopened and you're out of this crew." Leave "Leave Crew 07? You can't come back to this crew. Nights you finished are already paid." Out push "You're out of Crew 07" / "You didn't open every app on night 6, so your spot is open again." Released "Your volunteer spot in Crew 07 closed" / "An app came back into the run. Nights you finished are paid." Invite "{Name} asked you to test {App}" / "Open TestTrack to accept or decline." Errors "No TestTrack account uses that email." "They declined, so you can't ask again in this run." "That person already tests in this crew."

## Order
B1 || P1 (0041 down = 0040). B2 after B1; B3, B4, B5 parallel after B2; B6 after B2. P2 then P3. U after B2 + P1. C after P1 + B2. A1 after B1/P1 schemas; A2 after A1; A3 || A5 after A2; A4 after A3; A6, A7 after A2 + P3. Shared files: router.py (B2, P2, P3 in sequence), uploads.py (B2 then U), crew_sweep.py (B3), sweeper.py (B3 then P2).

## Risks
- Tix: only money path; ONCE_PER_CAUSE + unique idempotency_key; ledger == balance test.
- Strict rule: one app a volunteer can't install takes them out night 1 with no appeal; say it plainly in the join sheet.
- Old builds: volunteers never in members/testers/mine; chat author_seat_id carries volunteer id; no min version needed.
- Privacy: volunteers see seat notes like members; private results checked against row owner and current seat owner; invite reveals account existence (C).
- Files near 500: visits.py 500, crew_chat.py 498, views.py 493, api/v1/crews.py 480, Api.kt 497, CrewViewModel.kt 489.
- Codex review: B2, B3, B4, P2, P3, U.
