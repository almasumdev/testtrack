# Crew chat go-live plan (approved by the owner 2026-11-24)

Owner decisions: pictures ON at 3 a day (as today); auto-hold a message after 3 distinct reporters; report reasons = spam ("Spam or ads"), abuse ("Rude, bullying or hateful"), sexual ("Sexual or violent content"), private ("Shares someone's private details"), scam ("A scam or a trick to get tix"), other ("Something else", note 10-280 chars required); rules agreed once before the first message (no sign-in line); who can chat unchanged; closed reports kept 180 days.

Repos: backend H:\_group_projects\tt_wt_review branch feat/dev-next (dev = 7560c32, schema 0046; next migration 0047). App H:\android\onGoing\test_track main. Panel + site H:\next\eazyverse_release main.

Today: chat exists, gated server-side by CHAT_ENABLED (core/config.py:143; crew_chat.require_on :56, chat_limits :92, chat_reads.badges/unread_total :28/:46, sweeper :171). App reads chat_state (net/CrewsWire.kt:265), hides Chat when OFF/GONE (ui/CrewChatEntry.kt:70). Production 2.6.0 has no chat code. 2.7.x builds have chat code and would show it -> new feature token `chat` gates it. Push tokens store chat flag at me.py:141. Team tools exist: admin_chat.py:126-273 (read incl. deleted, post as bot, delete with reason, pause in one crew, audit rows), panel crewChat.tsx/chatActs.tsx/reasons.ts.

## B0 Backend foundations (first)
- docs/crew-chat.md: replace line 46 ("No report or block for now") with Report, Block, Rules, Ban, Feature-gate sections holding the exact API below. docs/admin-console.md: "Chat reports" section + audit keys chat_report_dismissed, chat_warned, chat_banned, chat_unbanned.
- Migration alembic/versions/0047_chat_safety.py (revises 0046_review_update), working downgrade:
  - user_blocks: blocker_id, blocked_id (FK users, composite PK), created_at, index blocked_id.
  - chat_reports: UUIDv7 id; crew_id FK crews SET NULL; message_id FK crew_chat_messages SET NULL; target ("message"|"person"); reported_user_id, reporter_id FK users; reason String(24); note String(300); text_snapshot String(3000); crew_name; state ("open"|"closed"); outcome String(16); handled_by, handled_at; created_at. Unique (reporter_id, message_id) WHERE message_id IS NOT NULL; unique (reporter_id, reported_user_id, crew_id) WHERE target='person'. Partial index on open; index every FK.
  - users: chat_rules_version int default 0 not null; chat_banned_at timestamptz null; chat_banned_by FK null.
  - crew_chat_messages: held_at timestamptz null.
- Models: db/models/crew_chat.py held_at; new db/models/chat_safety.py (UserBlock, ChatReport); user.py columns; exports in db/models/__init__.py.
- core/config.py: chat_rules_version: int = 1; chat_reports_to_hold: int = 3 (ge=0). Add to .env.example.
- core/errors.py: ChatRulesNeeded(NotAllowed) code chat_rules_needed.
- schemas/chat.py: ChatMessageOut blocked: bool = False, held: bool = False; ChatPageOut rules_needed: bool = False, rules_version: int = 0.
- Empty routers api/v1/blocks.py, api/v1/chat_reports.py, api/v1/admin_chat_reports.py registered in api/v1/router.py now.
- Reasons as fixed codes in core/rules.py: spam, abuse, sexual, private, scam, other.
- Tests: migration up/down. Checks: ruff check, ruff format --check, mypy --strict, scripts/check_routes.py, lint-imports.

## B1 Feature gate + push tokens (after B0)
- core/deps.py next to MULTI_CREW (:71): CHAT = "chat"; dependency require_chat_client(features: Features) raising ChatOff when CHAT not in features.
- api/v1/crew_chat.py:27 APIRouter(..., dependencies=[Depends(require_chat_client)]).
- api/v1/uploads.py keep: kind == "chat" requires CHAT before reading body.
- services/chat_reads.py badges(..., client_chat: bool), unread_total(..., client_chat: bool); False = same as chat off. Pass CHAT in features from crews.py:82,90,138 and volunteers.py:30 (add features: Features where missing). services/actions.py count gets client_chat, passed from me.py:49-56.
- me.py:141 store chat = body.chat and CHAT in features.
- scripts/reset_chat_push_tokens.py: push_tokens.chat = false for all rows, logs count, idempotent.
- Admin chat routes + sweeper stay gated only by CHAT_ENABLED.
- Tests tests/api/test_chat_feature_gate.py: no header -> chat_state "off" with CHAT_ENABLED=true, 404 chat_off on GET/POST/chat upload, crew_chat 0; with header all works; push-token PUT without header stores false.

## B2 Block (after B0)
- repositories/blocks.py (add, remove, list, blocked_by(user), blockers_among(users, author)); services/blocks.py; api/v1/blocks.py behind require_chat_client: PUT /me/blocks/{user_id} 204 idempotent limits.spend, self 422, unknown/erased 404; DELETE /me/blocks/{user_id} 204; GET /me/blocks -> [{user_id, name, photo, blocked_at}].
- services/chat_views.py: messages_out loads viewer's block set once; message_out (:175-220): blocked author -> stub blocked: true, no text/mentions/reply/picture (same shape as deleted); held message -> stub held: true to everyone except its author. Reply quotes of a blocked author read "Hidden".
- repositories/chat_reads.py:13-39 exclude blocked authors and held messages from unread.
- services/chat_push.py:88-134 drop blocked authors per recipient, don't count their mentions.
- One-way, silent. Tests tests/api/test_chat_blocks.py, tests/services/test_chat_push_blocks.py.

## B3 Report, rules, ban, member side (after B0)
- services/chat_reports.py; routes in api/v1/chat_reports.py:
  - POST /crews/{id}/chat/messages/{message_id}/report {reason, note}: 201 new, 200 repeat by same reporter; 422 own message or bot line; snapshot text; at CHAT_REPORTS_TO_HOLD distinct reporters set held_at + rev bump under chat_repo.locked; Telegram via services/telegram.py limited once per 5 min with limits.once.
  - POST /crews/{id}/chat/people/{user_id}/report {reason, note}.
  - POST /me/chat-rules {version} sets users.chat_rules_version.
- services/crew_chat.py post (:319-321): refuse ChatRulesNeeded when user.chat_rules_version < settings.chat_rules_version; banned -> ChatPaused("The team has stopped your messages in crew chats."). page (:258-273) adds rules_needed, rules_version; why_not "paused" for banned.
- services/chat_limits.py:92 same ban + rules checks for picture uploads.
- chat cleanup expiring query (repositories) excludes messages with open reports in SQL.
- services/admin_member_delete.py:253: erasing a member deletes their block rows both ways and clears reporter_id.
- Tests test_chat_reports.py (double report, own message, held at 3, rules gate, ban gate post + upload), test_chat_rules.py.

## B4 Admin endpoints (after B3)
- services/admin_chat_reports.py, schemas/admin_chat_reports.py, routes in api/v1/admin_chat_reports.py:
  - GET /admin/chat/reports?state=open|closed&cursor= (maintainer).
  - GET /admin/chat/reports/{id} (maintainer) with 5 messages before/after via admin_chat _items.
  - POST /admin/chat/reports/{id}/resolve {action: dismiss|warn|delete|pause|ban, reason} (admin): act + close every open report on that message (or person), one transaction. dismiss clears held_at (rev bump); warn notify.tell title "A note about crew chat" body "One of your messages in {crew} broke the chat rules. Please keep to them, or your messages may be stopped."; delete reuses admin_chat.delete; pause reuses admin_chat.pause; audit rows.
  - POST /admin/members/{user_id}/chat-ban {banned, reason} (admin).
- admin_chat.py:193 direct delete closes open reports on that message outcome "deleted".
- admin_overview.py attention item chat_reports_waiting (next to case_waiting :227). admin_members.py chat_banned in detail.
- Tests test_admin_chat_reports.py.

## A1 App data layer (after B0 contract)
- net/ApiFeatures.kt:18 listOf("multi-crew", "chat").
- net/ChatWire.kt blocked, held, rules_needed, rules_version. net/ChatApi.kt reportChat, reportPerson, block, unblock, blocks, acceptChatRules.
- domain/ChatThread.kt:92 ChatLine blocked, held. domain/ChatSelect.kt canReport(line) = !mine && user kind && !deleted && !blocked && sent; ChatPicks.report.
- viewmodel/CrewChatViewModel.kt + CrewChatSend.kt: report/block/unblock then full reload (since=0); on chat_rules_needed or rules_needed ask rules then send; load /me/blocks on open.
- Tests ChatSelect report rules; wire defaults.

## A2 Chat UI (after A1)
- ui/CrewChatSheets.kt:119 PickBar Flag "Report".
- ui/ChatReportDialog.kt on Ask, radio reasons like InstallReportDialog, AskCheck "Block {name} too". Title "Report this message"; body "The team reads every report, usually within a day. The member isn't told who reported them."; confirm "Report"; snack "Thanks. The team will look at it."
- ui/CrewChatBubble.kt stubs "You blocked this member." / "Held for the team to check."
- ui/ChatRulesDialog.kt on Ask with DialogSteps. Title "Chat rules"; 1 Be kind. No insults, bullying or hate. 2 Keep it about testing. No spam or ads. 3 No sexual or violent content. 4 Don't share anyone's private details. 5 Report anything that breaks these rules. Note "The team can remove messages and stop members from chatting." Link "Read the full rules" (https://eazyverse.com/apps/testtrack/rules). Buttons "I agree" / "Not now".
- ui/CrewMemberProfile.kt:46 optional onReport, onBlock/onUnblock, quiet actions under facts, passed only from CrewChatPage.kt:202 for others. Block confirm "Block {name}?" body "You won't see their messages in any crew chat, and their mentions won't reach you. They aren't told. You can unblock them in Settings."
- ui/CrewChatComposerPicture.kt hide picture button when picturesPerDay == 0.

## A3 Blocked members in Settings (after A1)
- ui/MeSettings.kt row "Blocked members"; ui/BlockedMembersPage.kt + viewmodel/BlockedMembersViewModel.kt list with Unblock; empty "You haven't blocked anyone."

## P1 Admin panel (after B4 contract; mock first)
- lib/testtrack-admin/types.ts new types + audit keys; client.ts; mock-chat.ts; pages/ChatReports.tsx + routes app/(testtrack-admin)/testtrack/admin/chat-reports/page.tsx and [id]/page.tsx (model on Cases.tsx and cases/[id]); sections.ts group 6 Comms with red alert count; reasons.ts warnChat, banChat, dismissReport; labels.ts four labels; crewChat.tsx "Reported" mark; member page ban state + Ban/Unban. List: open/closed filter; when, reason, crew, author, reporter, snippet, author's 30-day report count. Detail: message (even deleted) with picture if held, note, 5 before/after, paused/banned state; actions Delete, Warn, Pause here, Ban everywhere, Dismiss, each with reason + one-tap reasons.
- Checks npx tsc --noEmit, npm run lint, mock click-through.

## S1 Site text
- content/apps/testtrack/privacy.md: "Crew chat" section (text + pictures, who sees, team reads everything incl. deleted, reports + blocks, retention: messages deleted 3 days after the crew ends where pruning is on, pictures 24 h, report snapshots kept 180 days); fix line ~131 to "your phone's messages".
- rules.md page: apps/[slug]/rules route built only for apps with the file; loader next to getAppPrivacy (lib/app-content.ts:48).

## S2 Data safety files
- mkit.toml:65 other_in_app_messages shared = ["app_functionality"], optional = true. store/data-safety.md crew chat row. mkit datasafety build + check. NEVER apply.

Owner 11-24: "production ready, do whatever is needed": at go-live set PRUNE_ENABLED=true on live (live has it unset = False today) so chat really is deleted 3 days after a crew ends, as the privacy text says. Check what else prune_enabled prunes before flipping and report it.
NOTE for app coders: the snackbar clean-up (pieces A-F) is editing viewmodel/CrewChatViewModel.kt and viewmodel/CrewChatSend.kt right now; A1/A2 must start after that commit lands.

## Go-live (owner's word only)
dev deploy + reset script on dev; panel test; app build with chat to internal; site live; datasafety apply + content rating + category check; "promote backend" -> live CHAT_ENABLED=true, reset script, restart; production rollout later; no minSupportedBuild raise.
