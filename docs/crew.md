# Crews

> **This is the plan, not the state.** Written before any of it was built and kept as the record of what was decided and why. For what actually runs and how the app talks to it, see [architecture.md](architecture.md). Where the plan changed in October 2026, it says so below.

Replaces admin-assigned groups. Decided September 2026.

## What changed and why

Under the old model a developer submitted an app and waited for an admin to place it into a group.
The admin was the bottleneck, but the admin was also the only check that an app was real,
installable, and genuinely on a closed testing track before fifteen people committed a fortnight to
it. Removing the admin from placement means that check has to live somewhere else. It moves to the
other members, who are the only people who can actually try to install the thing.

## The word

"Crew", not "group", everywhere in the product. The exception is the Google Group testers join,
which is an external thing with that literal name: setup step 2 still says "Join the testers group"
and always will. Code identifiers and the Firestore `groups` collection are unchanged; this was a
copy decision, not a schema one.

## Forming

- **One open crew at a time, globally.** Not several. Fifteen people fill one lobby quickly; spread
  across three, none of them fill.
- **Submitting takes a seat** in the open crew. There is no approval step and no admin in the loop.
- **Fifteen members.** Nobody tests their own app, so every member opens **fourteen apps a night**
  for fourteen nights. Google requires twelve testers, so fifteen carries three dropouts of slack.
- **A crew starts when it reaches fifteen and not before.** There is no automatic start at a lower
  number. A crew that stalls at thirteen is an admin problem, handled from the console.
- **One app per crew, and a person may be in several crews at once.** Built: only a crew still
  *filling* holds your next seat, so one app waits at a time and any number run. A developer with three apps
  can have all three in flight. What that costs is nightly: each crew is fourteen apps, so two
  crews is twenty-eight a night and three is forty-two. The number of crews somebody is in is the
  single biggest predictor of whether they miss a day, so the count needs a cap and the app has to
  say the nightly total out loud before somebody takes a second seat.

## Verification, in place of the admin

> **Changed, October 2026: the server checks Play; members report instead of voting.** Taking a
> seat makes the server open the app's closed-testing listing in a browser signed in as an
> account on the testers group, which is exactly what a member will see. Visible: the app is in at
> once. Not visible: the seat is refused with how to fix it (usually the testers group missing from
> the track). Checker down: the seat waits, is retried by the sweep, and goes in unchecked after
> 12 hours. Members can **report** an app with a written reason; the owner is told at once without
> the reporter's name; two reports before the run starts send it back (a full crew goes back to
> filling); during the run a report only tells the owner and the team, and excuses the reporter
> from that app.
>
> **Voting is back as the fallback (October 2026).** When the checker cannot run, instead of the
> app going in unchecked after 12 hours, members decide it: those whose own app is already in the
> crew are asked to try it, two Yes put it in, two No (with a reason) send it back. Nothing is
> decided below three members. The sweep keeps trying Play, and whichever answers first decides.
> A waiting app is answered, not reported. The rest of this section is the original voting design
> this is drawn from.

A seat counts toward the fifteen only once other members confirm the app installs.

- **Two greens confirm. Two reds reject.**
- **A verdict only counts from someone who has already given a green to a different app in the same
  crew.** This is the load-bearing rule. Play returns "not found" for a closed test when *the
  tester* is not in the testers group, is signed in with the wrong Gmail, or the listing has not
  propagated yet, which can take hours. A brand-new member who has not finished setup will report
  not-found on everything they are shown. Requiring a green first proves the reporter's own setup
  works, which is exactly what makes their red trustworthy, and it costs no extra screen.
- **A single red does not reject anything.** It asks for a second opinion from somebody else.
- **Verification happens while the crew fills**, never after it starts, so a broken app can never
  begin a run.
- **On rejection** the owner is told what to fix, the seat is released, and they may resubmit to
  whichever crew is open, which may well be the same one.
- **Bootstrap:** the first two seats have nobody to verify them. Nothing is confirmed until the crew
  holds three; the earliest arrivals are verified by the next ones.

## Time

- The run begins at **12:01am**, at the first midnight after the crew fills. A crew that fills at
  3pm therefore has nine hours before day 1 counts, and that gap is the setup window: permissions
  and Google Group membership get done before anything is being judged.
- **Each member's day runs from their own local midnight**, so every member gets fourteen full
  local days and nobody's evening is split across two days. A single global clock would put the day
  boundary at 5pm local for a member at UTC-7 and generate misses they did not earn.
- This works because everything enforcement judges is already per-tester: "did T open app A on day
  D" is a fact about T, so D can be T's own calendar. `Proof` already carries `day` in its id.
- **The zone is captured when the seat is taken and the day is derived server-side from it.** The
  phone's clock never decides, so changing the device timezone buys nothing. Genuine relocation is
  an admin or cooldown action.
- **Crew-level day is approximate.** Members drift up to a day apart, so the last member finishes a
  day after the first. Circle says "Crew 06 is halfway"; the owner's dashboard shows per-tester
  days rather than one crew day.

## One device, two accounts

`Device.fingerprint()` already exists in `data/Device.kt`: a salted SHA-256 of `ANDROID_ID`, stable
across reinstalls, useless for following anyone between apps. It was built as a flag an admin reads.
To make it a door:

- **Record it at proof time, not only at submission.** Two accounts submitting from one phone is
  suspicious; two accounts filing proofs from one phone in the same crew is the actual cheat.
- **Block the seat, not the account.** Refuse a second seat in one crew from one device. Blocking
  the person outright punishes shared phones, which are common, and the mistakes would be invisible.
- **Keep an appeal path.** The identifier has known holes in both directions, documented in
  `Device.kt` itself: a factory reset issues a new id, a work profile or second Android user gives
  one phone two ids, and anyone determined gets around it. This catches the ordinary case, which is
  somebody helping a friend out without knowing it does not work, and should not pretend to more.

## The four screens

Only the third exists today.

| Phase | What the screen is |
| --- | --- |
| **Open** | No app in. The crew accepting seats: how many are left, who is in it, what happens at fifteen, and one way in. |
| **Lobby** | Seat taken, crew filling. Your app's verification state, the apps waiting on your verdict, the fill, and the countdown to 12:01. |
| **Run** | The day list. Fourteen rows. This is the app's actual value and is not being redesigned. |
| **Record** | Finished. What the crew did, what you did, and what it earned. |

The lobby is the new screen and the interesting one. It is the only place in the product where
members do something together before the run, and it is what makes a crew feel like a crew rather
than a queue.

## Joining part-way

Decided October 2026. The team can put an app into any crew that has not finished and has a free
place, and move a seat between crews at any time, from the admin panel
(eazyverse.com/testtrack/admin). Members still cannot: taking a seat is unchanged.

- A seat placed or moved into a crew whose run has begun carries `joined_at`. Its **first night**
  is the first local midnight after that moment, the same rule that starts a crew; the rest of that
  day is its setup window. Every other seat has `first_night` 1.
- Day numbers stay on the crew's clock, and the crew still ends on its own last day, so a seat that
  joins on day 6 has days 6 to 14: nine days, not fourteen. The panel says so before the team
  confirms, because Google's fourteen days will not come from that run alone.
- Nothing before a seat's first night counts: not as a missed day, not in reminders, not in what
  anybody owes. Other members are owed the newcomer's app from its first night in their own
  calendars, and the newcomer owes the others from the same night.
- A seat moved out of a running crew keeps its visits there as history and starts over where it
  lands. The visits key is per crew for that reason.
- The app counts a late joiner's run from its own first night ("Day 4 of 9"), greys the days before
  it on the meter, and leaves a newcomer's app out of tonight's list until its first night.

## Missed days

Decided October 2026, from the rule the app had always shown: miss a day (anything owed left
unopened at your own midnight) and you get a warning; miss two in a row and your app leaves the
run, nobody opens it any more and you owe nothing more there. An app you reported is not counted
against you. Every member gets an evening reminder at 20:00 their time while anything is left.

## Taken out: appeal and put back

An app taken out of a run (two missed days in a row, or the team) is not the end of it while the run is going.

- **The owner asks first.** Their crew page offers **Ask to get it back**, not a new crew. They write what happened (up to 500 characters) and send it, once per app.
- **While it waits, no new seat.** The owner can't put any app in a new crew until the team answers. The Take a seat screen says why and its button is off.
- **Accepted:** the team puts it back in the run. Its owner's missed nights and the nights it was out are let off, so the warning is gone and one more miss does not take it straight out again. The app is let off for every tester on the nights it was out, so nobody turns short for an app they could not open. The owner is told.
- **Refused:** the owner sees the team's reason, and only then **Put it in a new crew**.
- **The run ends first:** the appeal closes, and the owner can take a new seat.
- **The team can also put an app back without an appeal**, for example after the owner wrote in.

Everybody else keeps a taken-out app on the crew page, under **Left the run** ("You don't need to open these"). It is faded, nobody owes it, and Open all never reaches it. The first day it only says "Out of the run. Nothing to open.", since it may come back. From a day after it left it says "You can uninstall it now." with **Uninstall**, which opens Android's own prompt for that one app. Once it is off the phone the row goes. If the team puts it back, it moves back to Tonight.

## A clean run pays the seat back

Decided October 2026. When a crew closes, every member still in it who opened everything they
owed on every one of their nights gets back what their seat cost (100 tix), once, and the
finished notification and the Record say so. One short day, even one only warned, keeps the tix.
A seat the team put in part-way is held only to its own nights; a seat placed free gets nothing
back because nothing was charged. Showing up every day is what makes a crew work, so the member
who never let it down rides for free. The server's crew_sweep.close_finished decides it.

## A night's proof: time and a picture

Decided 27 October 2026, and built. Proof is two things, as it was in the August app, and a night
counts only with both:

- **Time.** Each app is open for 20 seconds in a round. It counts with at least 10 of them really
  in front, by the phone's own usage record, which the server holds to its floor.
- **A picture.** TestTrack takes one picture of the app at a moment picked at random between 10
  and 20 seconds: past the app's opening logo, and not a moment anybody can time a screen for. The
  testing control is in the picture on purpose, so it shows the app's name and its place in the
  round.

How it runs:

- **Google Drive is required.** It is a Get ready step, and the Take a seat page, which every way
  to a seat leads to, connects it first. Pictures go to the member's own Drive, shared by link;
  only the file's id reaches our server, which checks the file is theirs.
- **Android asks to share the screen every night**, once for the whole round, after one line from
  us saying why. Not now opens nothing and the night is still owed.
- **The visit is sent first and its picture follows**, so an upload never holds up the next app.
  A picture that cannot send stays on the phone and keeps trying by itself, with TestTrack closed
  too, and never makes a second copy. It counts for its night whenever it lands, even after
  midnight.
- **A picture that fails** (a blank screen, a camera that stopped) is asked for on the Crew page:
  open the app again for a fresh one, or pick one from the phone, which its owner sees as picked
  by hand.
- **The owner sees** tonight's picture on each member's row and every member's picture night by
  night under Every night; any picture opens full screen. The team sees the same on the admin
  panel's seat page.
- **Older builds** count as before, without a picture. A member who has sent one visit with a
  picture needs one on every later visit in that crew.

Why this is written down: the move to our own server in September 2026 kept the time and dropped
the picture without anybody deciding to, and the capture code went with the Firestore code in
October. Proof the owner can see is the point of a crew, and this section is what keeps it.

## Telling the crew about an update

Decided 27 October 2026, built 28 October. **Build on this section, not on the August app**,
whose version (commit da72874, removed in 65cac3c) was only a message.

- **The owner's Update button.** On their own crew page. TestTrack reads the owner's own app on the
  owner's phone: its version, and whether Google Play installed it.
  - **Tappable** only when that copy came from Play and its version is newer than the one the
    server has for the seat.
  - **Otherwise it looks disabled**, and a tap says what to do: install the new version from Google
    Play on this phone first; a copy from Android Studio or a file does not count.
- **Only the owner's tap tells the server.** Nothing is detected from the other members' phones.
- **Members get 24 hours.** For the first 24 hours after the tap, opening the old version still
  counts, and the app's row says "Update out" with a button to Play. After 24 hours, opening the old
  version does not count: the row says "Update needed" and tonight is not done until they update
  and open it again. The 24 hours are Play's time to reach everybody.
- **From Play only, for everyone.** Each visit sends the installed version and who installed it. A
  member's copy that did not come from Play does not count; the row says to install it from Play.
- **The team sees** each seat's newest version, when the owner tapped Update, and who is still on
  an old one, on the admin panel's seat page.

## Private feedback on Play

Decided 27 October 2026, built 28 October. **Build on this section, not on the August app**,
whose crew notes (commit 9776af2, removed in 65cac3c) are a different thing.

- **Once per app.** Each member leaves private feedback on Google Play for every other app in the
  run, once for the whole run.
- **With a screenshot.** They take it with TestTrack's camera button over Play, as a Circle review
  does, and submit it. It goes to their own Drive, shared by link.
- **By the end of day 7.** From day 8, a member's opens of an app they have not sent feedback for
  do not count, until they send it. For a seat that joined part-way, the 7 days count from that
  member's own first night with it.
- **A reported app is excused.** A member who reported an app as not installing owes it no
  feedback either.
- **Nobody verifies it in the app.** The screenshot is seen only by the team, on the admin panel's
  seat page, who may act on a false one. The owner reads the feedback itself in Play Console.
- **No reward.** No tix either way.

## Nights let off

Decided and built 28 October 2026. Reporting an app excuses it for the member who reported it; two
more ways to let a night off, so nobody is marked down for something they could not do:

- **The owner marks their app down tonight**, from their crew page, twice a run
  (`rules.DOWN_NIGHTS`). Nobody owes it that night. It costs the owner that night towards Google's
  14, which is why it is theirs to spend. The crew is told.
- **The team lets one app off for everybody, or one member off everything, for one night**, from
  the admin panel, with a reason in the log. The member is told.

A night let off is owed by nobody it covers, so it is never a miss and never counts against the
seat's refund.

## Nights waiting on pictures

Decided and built 28 October 2026. A night whose opens are only waiting on their pictures is not
held against anybody by the take-out rule: a member offline for two nights is not put out of the
run for it. It still counts for nothing until the pictures land.

## Tracking the run

Built 28 October 2026, in the spirit of the August app's dashboard. The owner's Every night page
opens with a grid like a contribution graph: one square per tester per night (picture in, picked
by hand, waiting, missed, let off or reported, tonight, to come), each night's count under it, and
a line that says "On track for Google" or names the nights short of 12. Each member's pictures
follow. The team sees the same grid on the seat page.

## Still open

- The admin panel (eazyverse.com/testtrack/admin) starts crews, takes apps out, places apps and
  moves seats, each logged with who and why.
