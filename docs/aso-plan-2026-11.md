# TestTrack ASO plan, 2026-11-24 to 2027-01-24

The goal: top 3 in Play search for the phrases developers type when they need testers. This plan covers 8 weeks from the day the new listing goes live (2026-11-24), with targets that rise in stages.

Made 2026-11-23 from the rank scan of 11-23, the research of 11-22 and the Play numbers to 11-17. The rank and research files are in [console/aso/](../console/aso/). Rules followed: `mkit guide aso` and `mkit guide review`.

## Where we stand

| Period | Store visitors per day | Conversion |
|---|---|---|
| 08-24 to 10-18, old listing | 1.5 | |
| 10-19 to 11-17, search listing | 10.2 | 28.7% |

- Totals 08-24 to 11-17: 392 visitors, 171 acquisitions (43.6%), 194 installs, 133 uninstalls, about 142 active devices, 3 ratings (5.0).
- Bangladesh brings 125 of 171 acquisitions (73%). The US brings 28.
- Rank scan 11-23 (US, top 30): "testtrack" #1, "12 testers 14 days" #11 (was #10), "testers for app" #22. The other 24 tracked phrases are not in the top 30.
- Installs come at about 2 to 3 a day. The apps above us on page one have 1K to 50K installs. That gap, not the text, is what keeps most phrases out of reach for now.

## The phrases

From `research-20261122.json`. "Weakest top 5" is the smallest app on page one: a small one means the page can be taken.

| Phrase | Demand | Weakest top 5 | Score | Where it lives in our listing |
|---|---|---|---|---|
| 12 testers 14 days | 0.29 | 1K | 6.9 | title |
| get 12 testers | 0.71 | 100 | 16.9 | short description |
| closed testing community | 0.57 | 1K | 15.4 | short and full description |
| testers community app | 0.57 | 50 | 19.0 | full description |
| beta testing | 0.71 | 500 | 18.0 | full description |
| testers for app | 0 | 5K | 0 | full description (already #22) |
| closed testing | 0.57 | 1K | 11.7 | short and full description |
| 12 testers | 0.86 | 1K | 9.3 | title, short description |
| app tester | 0.71 | 5K | 15.1 | full description |

"12 testers" plus a brand word ("12 testers community", "12 testers - testers community", "12 testers hub") are searches for the competitor "12 Testers - Testers Community" (50K+ installs, 4.7, paid plans) and its lookalikes. We never use their name, in the listing, in replies or in ads. The plain phrases "12 testers" and "12 testers 14 days" describe Google's rule and are fair to use.

## Honest targets

Top 3 in 8 weeks is realistic for two phrases: our name and "12 testers 14 days" (the exact title match, where the #2 app has 1K installs). "get 12 testers" can reach the top 5 because one page-one app has only 100 installs. The short head terms ("12 testers", "closed testing", "app tester") are held by apps with 5K to 50K installs; we can enter their top 20 to 30 in this plan, and top 3 there is a goal for the next plan, once we have 1K+ installs and 50+ ratings.

US rank, from `mkit aso rank`:

| Phrase | Now (11-23) | Week 2 (12-08) | Week 4 (12-22) | Week 8 (01-19) |
|---|---|---|---|---|
| testtrack | 1 | 1 | 1 | 1 |
| 12 testers 14 days | 11 | top 10 | top 5 | top 3 |
| get 12 testers | none | top 30 | top 15 | top 5 |
| testers for app | 22 | top 20 | top 15 | top 10 |
| closed testing community | none | top 30 | top 20 | top 10 |
| testers community app | none | top 30 | top 20 | top 10 |
| 12 testers | none | | top 20 | top 10 |
| beta testing | none | | top 30 | top 20 |
| closed testing | none | | top 30 | top 20 |
| app tester | none | | | top 30 |

Blank means no target yet at that checkpoint. Play only returns 20 results for "12 testers" and "testers community", so for those "top 20" means showing at all.

The numbers behind rank:

| Measure | Now | Week 4 | Week 8 |
|---|---|---|---|
| Store visitors per day | 10.2 | 13 | 18 |
| Conversion (acquisitions / visitors) | 28.7% | 28% or better | 30% |
| Installs per day | about 2.3 | 3.5 | 5 |
| Ratings | 3 | 12 | 30 (stretch 50) |
| Average rating | 5.0 | 4.7+ | 4.7+ |
| Active devices | about 142 | 170 | 220 |

50 ratings at 4.7+ is the owner's target. At our install rate, 30 by week 8 is the honest number and 50 is the stretch; 50 should come within about 12 weeks if the review ask below works.

## How we climb

1. Text only makes the app eligible for a search. Rank above that comes from installs, people keeping the app, ratings and Android vitals.
2. So the plan has two halves: the listing (text and art, changed rarely and one thing at a time) and the levers that bring and keep users (ratings, invites, communities, retention), which run alongside.
3. One listing change per round, per market. Play reports lag 3 to 7 days, so each round is judged over 2 weeks at least.

## Weekly schedule

"Agent" means a Claude session the owner starts; it drafts and builds, the owner approves. Nothing is published, posted or sent without the owner's word.

### Week 1: 11-24 to 11-30, the new listing goes live

Owner:
1. Apply the new listing on 11-24: title "12 Testers 14 Days: TestTrack", the new short and full description (store/listing/en.md), the new screenshots and feature graphic.
2. Ship app 2.7.x to production the same week. The listing describes volunteers and private testers, so it must not stay live long on a production build without them.
3. In Play Console, write down the baseline: Store performance, Statistics (installs, uninstalls, active devices, 30-day retention if shown), Android vitals.

Agent:
1. Run `mkit aso rank` on 11-24 as the day-0 scan.
2. Add BD, IN and GB markets to console/aso/keywords.toml (same English phrases), so we can see the countries that actually bring users. Run `mkit aso rank` again for their baseline.
3. Review ask, first change: count a full tix refund and an accepted Circle review as value moments, next to the finished crew (ReviewMoment.kt). Planner first, then coder. Ship it in the 2.7.x production release if it is ready, otherwise the next one.
4. Remote Config: `review_min_value_events` from 2 to 1. A finished crew takes 14 nights and is never an accident; with 2, almost nobody reaches the ask. Leave the other thresholds as they are. Draft the change; the owner applies it.

### Week 2: 12-01 to 12-07, leave the listing alone

Owner:
1. No listing changes.
2. Reddit and Discord: read the threads mkit leads sends to Telegram and post 3 to 5 helpful replies by hand. Always say it is your app. Answer the question first; link only when it fits.

Agent:
1. `mkit aso rank` on Monday 12-01.
2. Plan the invite link with the planner: "Invite a developer" share in the app, a tix reward for both people when the new person's first crew starts (not on install), one reward per new phone (the same-phone marker already exists), a monthly cap. When the app opens the Play listing itself, the intent names com.android.vending. The shared link is the play.google.com URL with a referrer so the Install Referrer API can read it.
3. Draft a custom store listing for links we share ourselves (Reddit, Discord, the invite link), so Play Console shows those visitors apart. Same text as en; owner creates it in Play Console.

### Checkpoint 1: Monday 12-08 (week 2)

Commands, run in H:\android\onGoing\test_track:

```
mkit aso rank
mkit aso impact --since 2026-11-24
mkit stats
mkit reviews list
```

Owner: open Store performance > Google Play search and note the top search terms and their visitors (it may still say "Other" because of our low numbers). Do this every 2 weeks from now on.

This is a first read only: Play data covers about a week after the change. Write the numbers in the Log. Act only on the stop rules below.

### Week 3: 12-08 to 12-14

Owner:
1. No listing changes.
2. Reddit and Discord replies as in week 2, using the shared-link custom listing if it exists.

Agent:
1. `mkit aso rank` on 12-08 (done at the checkpoint).
2. Build the invite link (coder, app and backend in separate pieces; backend on dev first). Codex reviews it, since it touches tix.
3. Draft the next text round so it is ready for week 5: en-IN and en-GB listings and the Bangladesh custom listing (see "Next text round").

### Week 4: 12-15 to 12-21

Owner:
1. No listing changes.
2. Reddit and Discord replies.

Agent:
1. `mkit aso rank` on 12-15.
2. Run `mkit aso research` again to see whether new phrases appeared since 11-22.
3. Draft the store listing experiment for the first screenshot (see "Experiment").

### Checkpoint 2: Monday 12-22 (week 4), the real verdict on the 11-24 listing

```
mkit aso rank
mkit aso impact --since 2026-11-24
mkit stats
mkit reviews list
```

Owner: Google Play search terms report, and Statistics for uninstalls and active devices. Compare with the targets table, then apply the decision rules.

### Week 5: 12-22 to 12-28, the second text round

Owner:
1. Apply the next text round if checkpoint 2 says the default listing is working (decision rules). Only the markets named there; the US default stays the same, so it is the comparison.
2. Start the first-screenshot experiment on the default English listing.
3. Expect low traffic over the holidays. Do not read this week against November.

Agent:
1. `mkit aso rank` on 12-22 (done at the checkpoint).
2. Invite link ready for the next release, if the review is clean.

### Week 6: 12-29 to 01-04

Owner:
1. No new listing changes. The experiment keeps running.
2. Reddit and Discord replies.

Agent:
1. `mkit aso rank` on 12-29.
2. Retention: after a crew finishes, the app points to the next thing to do (volunteer in a crew, or give feedback in the Circle for tix), so people have a reason to stay after their 14 days. Designer, then coder.

### Week 7: 01-05 to 01-11

Owner:
1. Ship the release with the invite link and the after-crew step, on your word.
2. Reddit and Discord replies.

Agent:
1. `mkit aso rank` on 01-05, and `mkit aso impact --since <date of the week 5 change>` for the markets changed then.

### Week 8: 01-12 to 01-18

Owner: Reddit and Discord replies. No changes.

Agent: `mkit aso rank` on 01-12.

### Checkpoint 3: Monday 01-19 (week 8)

```
mkit aso rank
mkit aso impact --since 2026-11-24
mkit aso impact --since <date of the week 5 change>
mkit stats
mkit reviews list
```

Owner: Google Play search terms report, Statistics, the experiment result.

### 01-19 to 01-24: wrap up

1. Fill in the Log with the week 8 numbers against the targets.
2. Decide what rolls out (the winning text to more markets, the experiment winner).
3. Write the next plan: the head terms, and other languages if English worked (see "Later").

## Next text round (week 5)

Only if checkpoint 2 passes. One change per market:

| Listing | Title | Short description | Why |
|---|---|---|---|
| en-US default | keep | keep | the comparison |
| new en-IN and en-GB | Get 12 Testers: TestTrack (25 chars) | keep | tests the "get 12 testers" title against the US title |
| Bangladesh custom listing | keep | Closed testing community for developers. Get 12 testers who open your app daily. (80 chars) | Bangladesh is 73% of acquisitions; only the short description changes, so a bad result costs little |

The full description stays the same everywhere this round.

## Experiment (from week 5)

1. Store listing experiment on the default English listing: the first screenshot only, 50/50.
2. Variant: a portrait first image that shows the crew page with the daily opens (the proof testers really open the app), against the current landscape first image.
3. At about 10 to 15 visitors a day, telling a real difference apart takes 4 weeks or more, and it may end with "needs more data". In that case keep the current image and end the test. Never pick a winner on a hunch.

## Ratings

1. The card only comes at a value moment, with nothing asked before it, as `mkit guide review` says. That is already built; week 1 adds the two extra value moments and lowers `review_min_value_events` to 1.
2. Never offer tix, seats or anything else for a rating. Never ask crew members to rate in chat, and never set up rating swaps between members: Play removes those reviews and can act against the app.
3. Read the result with `mkit stats` and `mkit reviews list`, and the Analytics events `review_flow_requested`, `review_flow_launched`, `review_flow_unavailable`.
4. Answer every review, good or bad, within 2 days.

## Decision rules

1. "12 testers 14 days" is not in the top 5 by week 4: check the search terms report. If it shows impressions, the text is working and installs are the gap: do not touch the title, push ratings and invites. If it drops below #15, check whether a competitor took the same title words.
2. A target phrase is not in the top 30 by week 4: it is too hard for now or too few people search it. Take it off the targets, pick the next phrase from a fresh `mkit aso research`, and work it into the full description in the week 5 round, two or three times in natural sentences.
3. Conversion falls below 20% for 2 weeks after 11-24: the new art is the likely cause. Put the old first screenshot back (store/backups) and start the experiment early.
4. Visitors per day do not pass 10.2 by week 4: impressions are not growing. Do not change the title; move the week 5 round to the description and short description, and lean on Reddit and invites.
5. Checkpoint 2 fails (rank and conversion both worse than 11-23): skip the week 5 round, roll back the change that hurt most, and judge again 2 weeks later.
6. Fewer than 10 ratings by week 4: check `review_flow_launched` in Analytics. If the card is rarely launched, check that 2.7.x is on production and the new value moments fire, then lower `review_min_sessions` from 5 to 3. One threshold at a time.
7. Bangladesh acquisitions per day fall by more than a third for 2 weeks after the Bangladesh custom listing: delete it so Bangladesh gets the default again.
8. Uninstalls stay above two thirds of installs by week 8: retention comes first in the next plan, before any more text work.
9. Never change the title before checkpoint 2. A title change resets what the old one built.

## Later, not in these 8 weeks

1. Other languages: only after English meets the week 8 targets. Check the Play countries report first, then `mkit aso research --market <country>` for the top two non-English markets.
2. A small paid campaign: only after retention improves, with a fixed budget, judged on people who finish a crew.
3. Head terms ("12 testers", "closed testing", "app tester") as top 3 targets: next plan, with 1K+ installs and 50+ ratings behind them.

## Don't

- Promise production access. Finishing a closed test helps meet the rule; Google decides.
- Talk about testers as installs, or offer installs.
- Show or describe a feature the production build does not have.
- Use the competitor's name, or "12 Testers" with their brand words, anywhere.
- Stuff keywords, or put "free", "best" or "#1" in the title.
- Change several things in one market at once, or every market at once.
- Auto-post on Reddit or Discord. A person writes every post.
- Reward ratings, or reward an invite on install alone.

## Risks

| Risk | What we do |
|---|---|
| Listing live before 2.7.x is on production | ship 2.7.x in week 1 |
| Play or the competitor reads our title as using their name | fallback title "Get 12 Testers: TestTrack" is ready (25 chars) |
| Low numbers (2 to 3 installs a day) make every result noisy | judge over 2 weeks or more, weekly scans, no reading of single days |
| Holidays (12-22 to 01-04) cut developer traffic | compare that period with its own weeks, not with November |
| Bangladesh users may not come from search, so US rank gains may not show in acquisitions | BD and IN scans from week 1, search terms report every 2 weeks |
| People uninstall after their 14 days by design, and uninstalls weigh on rank | the after-crew step, volunteers and the Circle |
| The invite reward is gamed with fake phones | reward on first crew start, one per phone, monthly cap |
| Reddit or Discord removes posts or bans the account for self-promotion | read each community's rules, help first, say it is your app |
| Google changes or drops the 12 testers rule | watch Play Console help and dev news; if it changes, rewrite the listing first |
| The experiment never reaches a result at our traffic | end it after 4 to 6 weeks and keep the current art |

## Log

<!-- Append one dated entry per change or check: what changed, the numbers, what is next. -->
