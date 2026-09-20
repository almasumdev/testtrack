# Crews

> **This is the plan, not the state.** Written before any of it was built and kept as the record of what was decided and why. For what actually runs and how the app talks to it, see [architecture.md](architecture.md).

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
- **One app per crew, and a person may be in several crews at once.** A developer with three apps
  can have all three in flight. What that costs is nightly: each crew is fourteen apps, so two
  crews is twenty-eight a night and three is forty-two. The number of crews somebody is in is the
  single biggest predictor of whether they miss a day, so the count needs a cap and the app has to
  say the nightly total out loud before somebody takes a second seat.

## Verification, in place of the admin

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

## Still open

- The admin console needs a force-start action for stalled crews, and still says "group" throughout
  its own UI.
- Nothing here is wired to Firestore. The UI is being built against in-memory state first, the same
  way Circle was.
