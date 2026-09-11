# Feedback

Circle's paid half, redesigned. Decided September 2026. Not built.

## What it is for

Not a comment box. The point of Feedback is to get somebody a **real Play Store review** if their
app is live, or **written developer feedback** if it is still in closed testing. Those are the two
things that actually move an app: one is what Google counts, the other is what fixes it.

So a review is not typing a paragraph. It is a job with steps, and the app walks somebody through
it.

## The flow

When a reviewer takes a post, a page opens and steps them through it. Each step unlocks the next.

```
1  Be eligible      Signed in with the right Gmail, in the testers group
                    where the app is still in closed testing.

2  Install it       From Play, through our button. Verified on the phone:
                    the package is present and the installer is Play.

3  Use it           Thirty seconds minimum, no maximum. Counted by
                    UsageStatsManager, the same way a daily proof is.

                    Nothing drags them back. They stay as long as they
                    like; when they return, the time is checked and the
                    next step is open.

                    A floating button rides over the app the whole time,
                    for capturing a screenshot whenever they want one.

4  Leave it         Production: our button opens the Play listing at the
                    review sheet. Closed testing: they write it here.

5  Prove it         A screenshot. Taken after leaving the review, of the
                    review itself, which is what the floating button in
                    step 3 is for.
```

**The floating button is the part worth getting right.** Asking somebody to leave the app, find
the screenshot key, and come back is three chances to lose them. A button that is already there
while they are reading the Play page is one tap.

## Whether the app is live

Asked of the owner when they post, in their own words rather than as a dropdown of jargon:

> **My app is live on Play Store** — reviewers will be sent to leave a public review.

Unticked means closed testing, and reviewers write feedback in the app instead. It is asked rather
than detected because nothing on a stranger's phone can see which track an app is on, and the
owner is the only person who actually knows.

## Getting paid

The poster pays, the reviewer earns, as now. What changes is when.

```
reviewer submits  ->  owner has 2 days to approve or reject
                      approved      -> tix paid
                      no answer     -> auto approved after 2 days, tix paid
                      rejected      -> held, and it goes to dispute
```

**Auto-approval after two days is deliberate.** An owner who simply never looks would otherwise be
able to keep every review for nothing, and the reviewer has already done the work.

## Disputes

A rejection puts the tix on hold for both sides and sends it to three members who decide.

- **Three jurors, majority wins, final.** None of them shares a crew or a device with either side.
- **Jurors earn tix**, which is what makes anybody do it. It becomes a third thing to do in Circle
  alongside Feedback and Activity.
- **Only somebody who has finished a crew may judge.** Fourteen days is a bar a fresh account
  cannot clear, which is what keeps an owner's alts off their own case.

## The scam, and what stops it

The obvious worry is a faked screenshot. It is not the real one. The real one is an **owner who
rejects an honest review to avoid paying**, and **one person being both sides through a second
account**.

Four rules, and none of them relies on a person noticing:

**One review per app per device.** Not per account. A second account on the same phone cannot
review the same app twice. This is the one that kills the cheapest farming outright, and it works
because `data/Device.kt` already produces a stable salted hash of `ANDROID_ID`.

**The reviewer's device must not be one of the owner's.** If the fingerprint matches any device
that owner has ever signed in from, the review is refused before it starts rather than judged
afterwards.

**Install and use are verified on the phone.** The package must genuinely be there, installed by
Play, with real foreground time behind it. The screenshot stops being the only evidence and
becomes the last of three, which is what makes forging it pointless.

**Rejecting costs the owner a stake.** A rejection holds some of their tix. Upheld, they get it
back. Overturned, they lose it. An honest rejection is free and a dishonest one is expensive, which
is the only arrangement that makes the button safe to offer.

What none of this can do is read the Play Store. Nobody outside Google can see whether a review
exists, which is why this is a chain of cheap checks rather than one true one.

## What it needs from the phone

All of it already exists for something else:

| | |
| --- | --- |
| `Device.fingerprint()` | the device rules |
| `InstalledApps.info()` | present, and installed by Play |
| `UsageStatsManager` | the thirty seconds |
| the overlay permission | the floating screenshot button |
| `GroupGate` | testers-group membership |

## Open

- Where the screenshots are stored. Drive, as proofs are, or our own object storage.
- Whether a juror sees the reviewer's name, or only the evidence.
- How many disputes one person may judge in a day.
