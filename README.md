# TestTrack

TestTrack helps a group of Android developers get each other's apps through Google Play's
closed-testing requirement, without anyone having to chase screenshots in a WhatsApp thread.

A new personal Play developer account needs 12 testers opted in for 14 continuous days before it
can apply for production access. Finding 12 people willing to keep an app installed for a
fortnight is hard alone, so developers form groups and test each other's apps. TestTrack is the
place that group keeps score: you open the apps, the app records that you did, and every owner can
see their own 14-day grid.

> **বাংলায়:** নতুন Play ডেভেলপার অ্যাকাউন্টে প্রোডাকশনে যাওয়ার আগে ১২ জন টেস্টারকে টানা ১৪ দিন
> অ্যাপটা রাখতে হয়। একা ১২ জন জোগাড় করা কঠিন, তাই ডেভেলপাররা দল বেঁধে একে অন্যের অ্যাপ টেস্ট করেন।
> TestTrack সেই হিসাবটাই রাখে, যাতে কাউকে হোয়াটসঅ্যাপে স্ক্রিনশট চেয়ে বেড়াতে না হয়।

---

# Before you install

You are about to give an app permission to see how long other apps stay on your screen, and to
take a screenshot while one of them is open. Those are serious permissions and you are right to
stop and ask what happens to them. This section answers that before anything else, in as much
detail as it takes.

Every claim below can be checked against the source in this repository. Where a file settles the
question, it is linked.

> **বাংলায়:** এই অ্যাপ আপনার ফোনে দুটো বড় অনুমতি চায়, কোন অ্যাপ কতক্ষণ স্ক্রিনে ছিল সেটা দেখার
> অনুমতি, আর টেস্ট চলার সময় স্ক্রিনশট নেওয়ার অনুমতি। এগুলো নিয়ে সন্দেহ হওয়া স্বাভাবিক, আর
> প্রশ্ন করাটাই ঠিক কাজ। নিচে প্রতিটার সোজা উত্তর দেওয়া আছে, আর প্রতিটা দাবি এই রিপোজিটরির কোড
> দেখে যাচাই করা যায়।

## The short version

| What it asks for | What it actually does | What it never does |
|---|---|---|
| Usage access | Reads how many seconds one named app spent on screen today | Read what you did inside any app |
| Screen capture | One screenshot per app, only during a round you started | Run in the background or record video |
| Google Drive | Writes your screenshots to a folder it creates | Touch anything else in your Drive |
| Google sign-in | Your email and name, so a grid can show who is who | Ask for or see your password |
| Notifications | A reminder from your own phone in the evening | Anything, if you decline. It is optional |

TestTrack does not request contacts, messages, photos, location, microphone, camera, or call log.
It declares seven permissions in total, all of them at the top of
[app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml), each with a comment saying
why it is there. Nothing can be added to that list without you seeing it on the next install.

There is no analytics SDK, no crash reporter, no advertising library, and no third-party tracker
anywhere in the build. Every library the app ships with is listed in
[app/build.gradle.kts](app/build.gradle.kts), and the only network services among them are Google
sign-in, Firebase, and Google Drive.

---

## Usage access, the one that worries people most

**Why it is needed.** Play does not count an app being launched. It counts an app being used. So a
screenshot is not enough on its own: a screenshot proves the app opened, and nothing more. The
daily record carries real foreground time, and there is no way to measure that from inside
TestTrack, because TestTrack can only time the visit it started itself. That is exactly the number
a person could fake by opening an app and walking away.

**What Android hands over.** Be clear about this, because a half-answer here is worse than none.
When TestTrack asks the system for today's activity, Android returns a stream covering every app
on the phone. That is how the API works, and no app can ask for a narrower slice.

**What TestTrack does with it.** It walks that stream and keeps one number: the seconds spent in
the single package it is checking, which is one of the apps in your group. Every other event is
skipped on the spot. Nothing about any other app is stored, remembered, or sent anywhere. The
whole function is about forty lines and you can read it in
[UsageRepo.kt](app/src/main/java/com/eazyverse/testtrack/data/UsageRepo.kt); the line that does the
discarding is `if (event.packageName != pkg) continue`.

**What leaves your phone.** One integer per app per day, the milliseconds that app was on screen.
That is the entire upload. Not app names you use, not a timeline, not a browsing history.

**It is not silent.** Usage access cannot be granted by a popup. You have to walk into Settings,
into Special access, and switch it on yourself, and you can switch it off in the same place at any
moment. TestTrack cannot turn it on, and cannot keep it on.

> **বাংলায়:** Play শুধু অ্যাপ খোলা গোনে না, আসলে ব্যবহার করা হয়েছে কিনা সেটা গোনে। তাই স্ক্রিনশটই
> যথেষ্ট নয়, কত সেকেন্ড অ্যাপটা স্ক্রিনে ছিল সেটাও লাগে।
>
> সৎ কথাটা হলো, Android যখন আজকের হিসাব দেয়, তখন সে ফোনের সব অ্যাপের হিসাব একসাথেই দেয়। এটা
> সিস্টেমের নিয়ম, কোনো অ্যাপ এর চেয়ে ছোট অংশ চাইতে পারে না। TestTrack সেই তালিকা থেকে শুধু আপনার
> গ্রুপের যে অ্যাপটা যাচাই করছে সেটার সেকেন্ড রাখে, বাকি সব সেখানেই বাদ দিয়ে দেয়। অন্য কোনো অ্যাপের
> কথা কোথাও জমা হয় না, কোথাও পাঠানো হয় না।
>
> সার্ভারে যায় শুধু একটা সংখ্যা, অ্যাপটা কত মিলিসেকেন্ড স্ক্রিনে ছিল। আপনি কোন অ্যাপে কী করেছেন,
> কী দেখেছেন, কিছুই না।
>
> আর এই অনুমতি চুপিচুপি নেওয়া যায় না। আপনাকে নিজে Settings এ গিয়ে Special access থেকে চালু করতে
> হয়, আর যেকোনো সময় ওখান থেকেই বন্ধ করে দিতে পারেন। TestTrack নিজে থেকে এটা চালু বা চালু রাখতে
> পারে না।

## The screenshot

**When it happens.** Only during a round that you start by pressing the button yourself. Android
asks you to confirm screen sharing, and while the round runs it shows its own indicator that no
app can hide. When the round ends, the capture stops.

**Why the moment is unannounced.** The shot lands at a random point inside each visit rather than
at a fixed second. If it were predictable, opening an app and waiting for the flash would be
enough to pass, which would make the whole record worth nothing to the group depending on it.

**What is in the frame, and this one deserves care.** Screen capture takes the screen, not the
app. Whatever is on display at that instant is in the picture, including a notification banner
that happens to slide down at the wrong moment. Turn on Do Not Disturb before a round if that
matters to you. It is a good habit regardless.

**Where it goes.** Into your own Google Drive, in a folder called `TestTrack Proofs` that the app
creates. The file is yours. It is in your Drive, it counts against your storage, and you can open
that folder and delete anything in it whenever you like.

**Read this part twice.** So the developer whose app you tested can actually see the proof, the
file is then set to "anyone with the link can view". It is not listed anywhere, not searchable,
and not indexed, but it is not private either: anybody who obtains that link can open the picture.
In practice the link is written into one database record readable by you, that app's owner, and an
administrator. Still, treat every proof screenshot as something that could be seen, and keep
private things off your screen while a round runs.

> **বাংলায়:** স্ক্রিনশট শুধু তখনই নেওয়া হয় যখন আপনি নিজে রাউন্ড শুরু করেন। Android আপনাকে একবার
> নিশ্চিত করতে বলে, আর রাউন্ড চলার পুরো সময় স্ক্রিনে সিস্টেমের নিজের চিহ্ন দেখা যায়, যেটা কোনো
> অ্যাপ লুকাতে পারে না।
>
> ছবিটা ঠিক কোন মুহূর্তে উঠবে সেটা আগে থেকে বলা হয় না, নাহলে অ্যাপ খুলে ঝলক দেখে বেরিয়ে গেলেই কাজ
> হয়ে যেত, আর তাতে গ্রুপের কাছে এই প্রমাণের কোনো দাম থাকত না।
>
> **একটা কথা মন দিয়ে পড়ুন:** স্ক্রিন ক্যাপচার পুরো স্ক্রিনের ছবি নেয়, শুধু অ্যাপটার না। ঠিক ওই
> মুহূর্তে স্ক্রিনে যা আছে সবই ছবিতে আসে, হঠাৎ নেমে আসা কোনো নোটিফিকেশনসহ। রাউন্ড শুরুর আগে Do Not
> Disturb চালু করে নিলে এই ঝামেলা থাকে না।
>
> ছবিগুলো যায় আপনার নিজের Google Drive এ, `TestTrack Proofs` নামের ফোল্ডারে। ফাইলগুলো আপনারই,
> যখন খুশি মুছে ফেলতে পারেন।
>
> **আর এই অংশটা গুরুত্বপূর্ণ:** যার অ্যাপ আপনি টেস্ট করলেন সে যাতে প্রমাণটা দেখতে পারে, সেজন্য
> ফাইলটাকে "যার কাছে লিংক আছে সে দেখতে পাবে" করে দেওয়া হয়। এটা কোথাও তালিকাভুক্ত হয় না, সার্চেও
> আসে না, কিন্তু একেবারে গোপনও না, লিংক পেলে যে কেউ ছবিটা খুলতে পারবে। তাই রাউন্ড চলার সময়
> ব্যক্তিগত কিছু স্ক্রিনে না রাখাই ভালো।

## Google Drive access

TestTrack asks for `drive.file`, which is the narrowest Drive permission Google publishes. It
grants access to files this app itself created, and to nothing else. Not your documents, not your
photos, not your backups, not a file some other app put there.

This is not a promise the app is making. It is a boundary Google enforces on its own servers. Even
if TestTrack asked for one of your other files, the request would be refused. The scope is one
line, in [Config.kt](app/src/main/java/com/eazyverse/testtrack/Config.kt), and the comment beside
it says not to widen it.

You can withdraw the access at any time from your Google account's security page, without
uninstalling anything.

> **বাংলায়:** TestTrack Drive এর সবচেয়ে সীমিত অনুমতিটা চায়, `drive.file`। এতে শুধু এই অ্যাপ নিজে
> যে ফাইলগুলো বানিয়েছে সেগুলোতেই হাত দেওয়া যায়, আর কিছুতে না। আপনার ডকুমেন্ট, ছবি, ব্যাকআপ, অন্য
> অ্যাপের রাখা ফাইল, কোনোটাই না।
>
> এটা অ্যাপের দেওয়া কথা নয়, এটা Google নিজে তার সার্ভারে আটকে রাখে। TestTrack চাইলেও আপনার অন্য
> ফাইল পাবে না, অনুরোধটাই বাতিল হয়ে যাবে। চাইলে Google অ্যাকাউন্টের সিকিউরিটি পেজ থেকে যেকোনো সময়
> এই অনুমতি তুলে নিতে পারেন, অ্যাপ আনইনস্টল না করেই।

## Seeing the apps in your group

TestTrack needs to know whether you have installed the apps you are meant to be testing, and it
needs their icons and names to show you a list worth reading.

It does this by asking Android about one specific package name at a time, the ones in your cohort.
It never asks the system for a list of what you have installed, and it holds no permission that
would let it. What it learns about each of those named packages is: whether it is installed, when
it was first installed, which store installed it, its name, and its icon. See
[InstalledApps.kt](app/src/main/java/com/eazyverse/testtrack/data/InstalledApps.kt).

The manifest deliberately does not hold `QUERY_ALL_PACKAGES`, the permission that would reveal
everything on the phone. Dropping it is also what keeps TestTrack eligible for Play.

> **বাংলায়:** আপনার গ্রুপের অ্যাপগুলো ইনস্টল আছে কিনা, আর তালিকায় দেখানোর জন্য সেগুলোর নাম ও আইকন,
> এইটুকু TestTrack জানে। সে একটা একটা করে নির্দিষ্ট প্যাকেজের নাম ধরে জিজ্ঞেস করে, আপনার ফোনে কী কী
> অ্যাপ আছে তার তালিকা কখনো চায় না, আর চাওয়ার মতো অনুমতিও তার নেই। যে অনুমতি দিয়ে পুরো তালিকা দেখা
> যেত, `QUERY_ALL_PACKAGES`, সেটা ইচ্ছে করেই বাদ রাখা হয়েছে।

## Notifications

The only optional permission. TestTrack posts a reminder in the evening if you still owe the group
apps for the day, and raises a notice if something happened to your app while you were away.

These are raised by your own phone, from data it already has, not sent from a server. Decline the
permission and every other part of the app works exactly the same; you simply have to remember on
your own. Setup finishes either way, though it will ask once.

> **বাংলায়:** এটাই একমাত্র ঐচ্ছিক অনুমতি। সন্ধ্যায় আজকের বাকি কাজের কথা মনে করিয়ে দেওয়া, আর আপনার
> অ্যাপের কিছু হলে জানানো, এইটুকুর জন্য। মনে রাখবেন, এই নোটিফিকেশনগুলো আপনার ফোন নিজেই তৈরি করে,
> সার্ভার থেকে পাঠানো হয় না। না দিলেও অ্যাপের বাকি সব ঠিকঠাক চলবে, শুধু নিজে মনে রাখতে হবে।

---

## Who can see what about you

| Thing | Who can read it |
|---|---|
| Your screenshot and your daily time | You, the owner of the app you tested, and an administrator |
| Your name and email | Any signed-in TestTrack account |
| Which groups you are in, how each run ended | You and an administrator |
| Your Drive folder | You, plus anyone holding a proof link |

The second row is the one to be straight about. Your display name and email address are readable
by any signed-in account. That is deliberate: a grid has to turn an account id into a human name,
and there is no way to do that without the name being readable. What is exposed is an address and
a name, nothing else. Your proofs, your times, and your history are not part of it.

Your proofs are narrower than people usually assume. A proof document can be read by the person
who made it, the developer who owns the app it is about, and an administrator. Not by the rest of
the group. The rules that enforce this are in [firestore.rules](firestore.rules) and every refusal
in them is covered by a test in [firestore-tests/](firestore-tests/), so they are checked rather
than asserted.

> **বাংলায়:** আপনার স্ক্রিনশট আর সময়ের হিসাব দেখতে পায় তিনজন, আপনি নিজে, যার অ্যাপ আপনি টেস্ট
> করেছেন, আর অ্যাডমিন। গ্রুপের বাকিরা না।
>
> তবে একটা কথা সোজাসুজি বলে রাখা দরকার, আপনার নাম আর ইমেইল যেকোনো সাইন ইন করা অ্যাকাউন্ট দেখতে
> পারে। এটা ইচ্ছাকৃত, কারণ তালিকায় আইডির বদলে মানুষের নাম দেখাতে হলে নামটা পড়া যেতেই হবে। এর বেশি
> কিছু না, আপনার প্রমাণ, সময় বা ইতিহাস এর মধ্যে পড়ে না।

## Your record stays

Nothing in TestTrack is deleted on a timer. Every run you finish, every removal, and every
submission that was turned down stays on your record permanently.

This is a deliberate choice and it cuts both ways, so it is worth saying plainly. A group is about
to trust a stranger with fourteen days of their own deadline, and the only thing that makes that
reasonable is being able to see how that person has behaved before. The same record that shows a
removal also shows every run you completed, and a long clean history is the thing that gets you
into good groups quickly.

When an administrator opens your profile they see: the cohorts you have been in, how each one
ended, the share of days you actually covered, and any app of yours that was turned down along
with the reason. They can pause an account, and if they do, you are told and you are told why.

> **বাংলায়:** এখানে কিছুই নির্দিষ্ট সময় পরে মুছে যায় না। আপনার শেষ করা প্রতিটা রান, প্রতিটা বাদ
> পড়া, আর ফিরিয়ে দেওয়া প্রতিটা সাবমিশন আপনার রেকর্ডে থেকে যায়।
>
> এটা ইচ্ছে করেই করা, আর দুই দিকেই কাজ করে। একটা গ্রুপ অচেনা একজনকে নিজেদের চৌদ্দ দিনের ভরসা দিচ্ছে,
> আগের আচরণ দেখতে না পারলে সেই ভরসাটা অন্ধ ভরসা হয়ে যায়। আবার যে রেকর্ডে বাদ পড়াটা দেখা যায়, সেই
> একই রেকর্ডে আপনার শেষ করা সব রানও দেখা যায়, আর পরিষ্কার একটা ইতিহাসই আপনাকে দ্রুত ভালো গ্রুপে
> ঢুকিয়ে দেয়।
>
> অ্যাডমিন আপনার প্রোফাইলে দেখেন, কোন কোন গ্রুপে ছিলেন, প্রতিটা কীভাবে শেষ হয়েছে, কত দিন আপনি
> সত্যিই কভার করেছেন, আর কোনো অ্যাপ ফিরিয়ে দেওয়া হয়ে থাকলে সেটা কেন। অ্যাডমিন চাইলে অ্যাকাউন্ট
> সাময়িক বন্ধ করতে পারেন, আর করলে আপনাকে কারণসহ জানানো হয়।

## Why you install it with adb, and why that is not a trick

The install instructions say to run `adb install` instead of tapping the APK. That is an unusual
thing to be asked, and being suspicious of an unusual instruction is the correct instinct. Here is
the actual reason.

Since Android 15, a feature called Enhanced Confirmation Mode greys out usage access for any app
that a browser or a file manager installed. The toggle is visibly there and simply will not move.
Usage access is what this app's proof is made of, so an app installed by tapping a downloaded file
cannot do its job at all.

Installs from `adb`, and installs from Play, are both exempt. So there are exactly two ways to end
up with a working copy, and until the Play track is open, `adb` is the one available.

You can also fix it by hand after a browser install: App info, then the three-dot menu, then
*Allow restricted settings*, which only appears after you have tapped the blocked toggle once. It
is fiddly, which is why the instructions do not lead with it.

> **বাংলায়:** ইনস্টলের নিয়মে বলা আছে APK তে ট্যাপ না করে `adb install` দিয়ে ইনস্টল করতে। এমন
> অস্বাভাবিক নির্দেশ দেখে সন্দেহ হওয়াটাই স্বাভাবিক, তাই আসল কারণটা বলে রাখি।
>
> Android 15 থেকে Enhanced Confirmation Mode নামে একটা ব্যবস্থা আছে, যেটা ব্রাউজার বা ফাইল
> ম্যানেজার দিয়ে ইনস্টল করা অ্যাপের usage access বন্ধ করে রাখে। টগলটা চোখে দেখা যায়, কিন্তু নড়ে না।
> আর এই অ্যাপের পুরো প্রমাণটাই ওই usage access দিয়ে তৈরি, তাই ডাউনলোড করা ফাইলে ট্যাপ করে ইনস্টল
> করলে অ্যাপটা কাজই করতে পারবে না।
>
> `adb` দিয়ে ইনস্টল আর Play থেকে ইনস্টল, এই দুটোতে ওই বাধা নেই। Play এর ট্র্যাক খোলা না হওয়া পর্যন্ত
> `adb` ই একমাত্র উপায়। চাইলে হাতেও ঠিক করা যায়, App info তে গিয়ে তিন ডটের মেনু থেকে *Allow
> restricted settings*, তবে সেটা ঝামেলার, তাই ওটা আগে বলা হয় না।

```bash
adb install -r TestTrack.apk
```

## What TestTrack never does

- It never reads your messages, contacts, photos, location, or call history. It does not hold the
  permissions that would allow any of it.
- It never takes a screenshot outside a round you started, and Android shows its own indicator the
  whole time one is running.
- It never asks for your Google password. Sign-in happens on Google's own screen and the app only
  receives a token afterwards.
- It never asks for money, shows an advert, or carries an analytics or tracking library.
- It never uploads anything about apps outside your group.

---

# How it works day to day

## Groups

Testing happens in **cohorts of 14**, one app per member.

Twelve is the number Google asks for, but it does not count an app's own developer, so a cohort of
twelve leaves every app one tester short. Fourteen members gives each app twelve or thirteen
testers with a slot of margin, and the run only starts once **thirteen** have been placed, because
below that Play Console is not counting either.

The run belongs to the group, not to the app. Every app in a cohort is on the same day, which is
what lets it be read as a cohort at all. Submitting an app registers it; an **admin approves it by
placing it in a group**, and those are the same act. There is no approved-but-unassigned state.

If a member drops out mid-run the day count **keeps going**. Play Console will have reset its own,
so the group screen says so outright rather than letting a grid that reads "day nine" imply
otherwise.

## Setting up, once

Five steps, and the last one is optional:

1. Sign in with Google
2. Join the testers group
3. Connect Google Drive
4. Allow usage access
5. Turn on reminders

## A day

1. Open TestTrack and press **Start testing**.
2. Confirm screen sharing once. The round covers the whole group under that single confirmation.
3. Each app opens in turn, is held for 36 seconds, is screenshotted at an unannounced moment, and
   hands straight over to the next one. You do not press anything in between.
4. You land back in TestTrack, and every proof uploads to your Drive.

A single **Open** on any row runs a round of one, so both paths behave identically.

**A day needs 30 seconds of use, not a launch.** The visit is held for 36 seconds to clear a
30-second bar, because the clock starts when the service schedules the visit while usage only
accrues once the app has actually reached the foreground a second or two later. Measured at
exactly thirty, honest full-length visits came back at 28.7s and 29.4s and failed the very rule
they had satisfied.

Foreground time covers the whole day, not just the visit TestTrack started, so opening an app on
your own earlier still counts.

Owners get a dashboard per app: how many of the thirteen reported today with their screenshots and
times, the 14-day grid, who is still to report, and a per-tester breakdown.

## The rules, and who enforces them

Miss one completed day and you get a warning. Miss two completed days in a row and your app leaves
the group.

Four things keep that fair, and each one matters:

- **Only completed days are judged.** Today never counts against you, because the apps are still
  openable and you have not missed anything yet.
- **Days before you joined never count.** An app placed into a group on day nine is judged from
  day nine.
- **You are told which apps, by name.** "You missed three" only invites a guess about which three.
- **A warning comes before a removal, always.** Nobody is removed without having been told first,
  on the day in between.

Fourteen days is the default rather than a ceiling. An admin can extend a run when a group needs
longer, and after day fourteen nothing happens on its own: the run carries on the same way, and
updates become optional rather than required unless the admin extends.

**Who does the removing.** There is no server watching. Each member's phone checks the attendance
of the app that member owns, using proof records it is already allowed to read, and acts on what
it finds. Thirteen apps means thirteen phones each watching their own, and between them nobody
goes unwatched.

This sounds like it could be abused, so here is why it cannot. Every removal is re-checked by the
database itself before it is accepted. The security rules recount the missed days from the actual
proof documents, and a modified copy of the app that tried to remove somebody who was present
would simply be refused. The enforcement is in
[Enforcement.kt](app/src/main/java/com/eazyverse/testtrack/data/Enforcement.kt), the check that
overrules it is in [firestore.rules](firestore.rules), and the tests that hold it to that are in
[firestore-tests/](firestore-tests/).

The honest cost of having no server is timing: nothing happens while every phone in a cohort is
shut. That is the harmless direction for the failure to go, since a cohort where nobody opens the
app has already stopped working for other reasons.

> **বাংলায়:** একটা শেষ হওয়া দিন মিস করলে সতর্কবার্তা, পরপর দুই দিন মিস করলে আপনার অ্যাপ গ্রুপ থেকে
> বাদ পড়ে।
>
> চারটা জিনিস এটাকে ন্যায্য রাখে। আজকের দিনটা কখনো আপনার বিপক্ষে গোনা হয় না, কারণ দিন শেষ হওয়ার
> আগে কিছু মিস হয়নি। আপনি গ্রুপে ঢোকার আগের দিনগুলোও গোনা হয় না। কোন কোন অ্যাপ বাকি আছে, নাম ধরে
> বলা হয়। আর বাদ পড়ার আগে সবসময় একদিন আগে সতর্কবার্তা যায়, না জানিয়ে কাউকে বাদ দেওয়া হয় না।
>
> **কে বাদ দেয়:** কোনো সার্ভার বসে পাহারা দিচ্ছে না। প্রত্যেক সদস্যের ফোন শুধু তার নিজের অ্যাপের
> হাজিরাটা দেখে, যেটা দেখার অনুমতি তার আগে থেকেই আছে। তেরোটা অ্যাপ মানে তেরোটা ফোন, প্রত্যেকে
> নিজেরটা দেখছে, ফলে কেউ নজরের বাইরে থাকে না।
>
> এতে কারচুপির ভয় হতে পারে, তাই বলে রাখি কেন সেটা সম্ভব না। প্রতিটা বাদ দেওয়ার হিসাব ডেটাবেজ নিজে
> আবার মিলিয়ে দেখে, আসল প্রমাণের রেকর্ড থেকে গুনে। কেউ অ্যাপ বদলে হাজির থাকা কাউকে বাদ দিতে চাইলে
> ডেটাবেজ সেটা সরাসরি বাতিল করে দেয়।

---

# Under the hood

The rest of this file is for anyone reading or building the source.

## Setup

```bash
git clone https://github.com/almasumdev/testtrack.git
cd testtrack
cp local.properties.example local.properties   # then fill it in
./gradlew assembleDebug
```

**Nothing deployment-specific is committed.** `local.properties` is gitignored, and every value in
it can equally be supplied as an environment variable of the same name, which is what CI does. The
build reads them and exposes them through `BuildConfig`; a missing key logs a warning at configure
time and the app says plainly that it is unconfigured rather than failing obscurely.

| Key | What it is |
|---|---|
| `TESTTRACK_WEB_CLIENT_ID` | OAuth 2.0 **Web** client ID. Not the Android one |
| `TESTTRACK_GATE_URL` | Deployed Apps Script web app that answers the membership check |
| `TESTTRACK_GROUP_URL` | Public URL of the testers Google Group |
| `TESTTRACK_KEYSTORE` and friends | Optional. Release signing; debug builds work without them |

The Web client ID must be **byte-identical** to the one the membership service checks the ID
token's `aud` claim against. A mismatch is rejected as `wrong_audience`.

### Something to look at

Groups and placements are admin-only in the rules, so a fresh install with an empty database has
nothing to show. [tools/seed.sh](tools/seed.sh) writes a cohort you can walk the whole app against:
a healthy mid-run group, one still forming, one a member short so the at-risk banner shows, a
fortnight of proof against your own app, and a submission awaiting placement.

```bash
export TESTTRACK_UID=<your firebase uid>      # from the users collection after signing in once
export TESTTRACK_PKGS="com.some.app com.other.app"   # installed on the test phone: real icons, working Open
tools/seed.sh up
tools/seed.sh down
```

It writes with a `gcloud` owner credential, bypassing the rules exactly as the admin app does.
Point it at an emulator or a scratch project, never at one carrying real accounts.

## How membership verification works

Google publishes **no API** for reading members of a consumer `@googlegroups.com` group. The Admin
SDK Directory API and the Cloud Identity Groups API are both Workspace-only, and Play Console
accepts only `@googlegroups.com` groups. Those two facts have no overlap.

What does work is **Apps Script's `GroupsApp`**, which authorises on *permission*, being a group
owner or manager, rather than on account edition:

```
Android app                    Apps Script (runs as group owner)         Google Groups
     .  Google ID token  ......  verify via tokeninfo
     .                           check aud == our client id
     .                           GroupsApp.getUsers()  ................  member list
     .  ... { email, isMember }  compare
```

Two properties make this worth the awkwardness:

- **Unforgeable.** The verdict is computed on Google's servers from a token the device cannot
  fake. The phone never asserts its own membership.
- **No roster leak.** The endpoint returns only `{email, isMember}` about the caller, so a
  decompiled APK reveals nothing about anyone else.

Gmail addresses are normalised on both sides, lowercased, dots stripped, `+suffix` removed, because
the group and the ID token can hold different strings for the same account.

## Proof of testing

Three signals:

- **Package visibility.** A `<queries>` filter for launcher activities, which every app in a
  cohort has. Gives installed yes/no, the installing package, and `firstInstallTime`, which is the
  continuous-install streak, retroactively, in one call. It survives app updates and resets on
  uninstall, which is exactly the semantics a 14-day streak needs. `QUERY_ALL_PACKAGES` would read
  more, but it is restricted on Play and TestTrack matches none of the approved categories.
- **MediaProjection.** One consent per round, held open while each app is opened, screenshotted
  and closed.
- **`PACKAGE_USAGE_STATS`.** Real foreground time per app per day. The only thing here that costs
  the tester a trip to Settings, and it is mandatory: a screenshot proves the app opened, and
  nothing else proves anyone stayed.

Usage is summed from **raw `UsageEvents`, not `queryUsageStats`**. The daily buckets that method
returns are written periodically, so a visit that ended seconds ago still reads as zero, which is
precisely when the reading is taken. Events are exact and include the session in progress, which
matters because the figure is read on the way out of a visit while the app under test is still on
screen. The aggregate is consulted as well and the larger of the two wins, since some manufacturers
trim the event stream.

An accessibility service for auto-scrolling and auto-tapping was built and removed. It worked, but
it costs each tester a *"full control of your device"* grant that Android revokes on every
reinstall, for one extra page of proof.

## Data

Flat collections. Rules are in [firestore.rules](firestore.rules): a proof is readable by the
tester who wrote it, the owner of the app it concerns, and an admin; every write is locked to the
owning account; and anything deciding *who tests what* is locked to an admin.

```
users/{uid}                                     email, displayName, and an admin-set
                                                blocked flag with its reason
users/{uid}/tokens/{installId}                  token, platform, updatedAt. One row per device
admins/{uid}                                    exists means admin. An empty document is the
                                                whole mechanism
groups/{groupId}                                memberUids, appIds, startDate, runDays, status
apps/{packageName}                              ownerUid, name, groupId, status, placedAt
proofs/{appId}__{testerUid}__{run}__{day}       fileId, imageUrl, capturedAt, usageMs
events/{eventId}                                uid, type, title, body, actorUid. A decision,
                                                read and raised by the device it names
```

Tokens sit in a subcollection rather than on the user document because rules do not cascade into
subcollections, and the user document is deliberately readable by every signed-in account so that
grids can turn a uid into a name. A push address should not inherit that.

The composite proof id is what keeps this simple: posting twice overwrites instead of duplicating,
and a whole grid is one query rather than 13 by 14 reads. The run's start time is part of the id,
so a restarted cohort begins with a genuinely empty record instead of inheriting the last run's
attendance. Keying apps by package name gives the same property one level up: re-submitting
corrects the record rather than creating a second one, and a correction merges, so fixing a typo on
day nine cannot eject the app from its group. An owner may withdraw their own app; its proofs are
left in place, because letting clients delete those would mean write access to other testers' rows.

Nothing expires. There is no TTL and no cleanup job anywhere in the codebase, by design, because a
developer's record is only worth consulting if it is complete.

**The admin app is a separate build**, [`../test_track_admin`](../test_track_admin), and this one
holds no privileged code at all. `groups` is admin-only, and an owner's write to their own app must
leave `groupId` and `status` exactly as they were, so approval cannot be self-granted. Placing the
thirteenth app is what takes a group to threshold, so the same admin write sets `startDate`. No
client here and no Cloud Function is involved.

Being an admin is one document at `admins/{uid}`, written from the console. An account may read
**its own** row and nobody else's, which is enough for the admin app to say "this account is not an
administrator" instead of failing with an unexplained refusal, and not enough to enumerate anyone.

The `blocked` flag lives on the user document but is an admin's judgement, so the rule that lets an
account write its own row allowlists the four fields that are its own business. Nobody can lift
their own ban.

Verifying a Play track automatically would need developer-level authorization from every owner: a
service account in their Play Console, a linked Cloud project, or a sensitive scope requiring
Google verification. All three cost more than an admin's glance, and placement into a cohort is a
judgement call anyway.

The refusals are the half that is easy to believe and hard to check, so
[firestore-tests/](firestore-tests/) asserts them against the local rules before they are deployed.
A tester cannot create a group, cannot start their own clock, cannot place their own app, cannot
read a stranger's proof, cannot read another device's push token, cannot unblock themselves, and
cannot remove a member who actually turned up.

```bash
cd firestore-tests && npm install && npm test
```

## Reminders

A run is fourteen consecutive days, and one tester forgetting costs the other thirteen their clock.
Reminders are how that gets caught in time, and they are **raised on the phone, not sent to it**.

[`ReminderWorker`](app/src/main/java/com/eazyverse/testtrack/data/ReminderWorker.kt) reads the same
data the home screen reads, counts what is still owed today, and posts a local notification if
anything is. First run is at 7pm, because a nudge at eight in the morning is about a day that has
barely started. Nothing is scheduled server-side, so there is no cron to keep alive, no fleet of
device tokens to keep in step with who is still in which cohort, and no cost to a tester being
unreachable for a week.

**Admin decisions travel the same road.** When an app is placed, turned down, or pulled back out,
the console writes an `events` document addressed to that developer, and their device raises it on
the next pass, or within seconds if the app is open. Written rather than pushed because the console
is a phone app: FCM's v1 API authenticates with a service account, and a service account key inside
an APK is a service account key in everybody's hands. The cost is latency, which is the right trade
for news measured in days. The document doubles as the audit trail, since `actorUid` records which
admin acted.

Because a notification can be swiped away on a lock screen, silenced by a Do Not Disturb nobody
remembered was on, or never shown at all if the permission was declined, anything as consequential
as "your app was removed from its group" is also parked on the home screen until it is dismissed.

That is the one query in the app needing a composite index; see
[firestore.indexes.json](firestore.indexes.json) for why.

FCM is still wired up, a token per device at `users/{uid}/tokens/{installId}` and a topic per
group, because it is the only path to instant delivery if a sender ever exists, and
[`tools/push.sh`](tools/push.sh) can drive it by hand today:

```sh
tools/push.sh group seed-group-a "5 apps still waiting" "Day 4 closes tonight."
```

## Releases

A signed APK on the releases page is the current distribution channel.
[`.github/workflows/release.yml`](.github/workflows/release.yml) builds one on every push to `main`,
publishes it, and prunes so only the newest three survive. The signing key is fixed and its SHA-1 is
registered in Firebase, so updates install over the top and Google sign-in keeps working.

Play-installed packages are exempt from Enhanced Confirmation Mode too, which is why the tester app
no longer holds `QUERY_ALL_PACKAGES`. Dropping it is what makes a closed-testing track on Play
possible, and that track is the only distribution where a tester meets no restricted setting at all.

## Design

The interface guide is [docs/ui.md](docs/ui.md): flat surfaces and hairline dividers, insets and
status-bar appearance handled centrally, one primary action per screen, and how the grid encodes
state by shape as well as colour.

## Roadmap

- [x] Onboarding, Google sign-in, setup checklist
- [x] Server-verified group membership
- [x] Drive-hosted proof on the narrow `drive.file` scope
- [x] Firestore: users, groups, apps, proofs, with admin-gated rules
- [x] Daily capture: open, unannounced screenshot, auto-return
- [x] Real foreground time from `UsageStatsManager`
- [x] Cohorts of 14 with a shared 14-day run
- [x] Owner dashboard: today's reporters, the grid, who is behind
- [x] Push plumbing: per-device tokens, per-group topics, deep-linked taps
- [x] The admin app: review submissions, form groups, place apps, see the proof
- [x] On-device reminders and admin decisions, no server and no scheduler
- [x] Device-side enforcement, re-checked by the database before it is accepted
- [x] Developer records: run history, removals, rejections, and an account pause
- [x] Runs that an admin can extend past fourteen days
- [x] Signed releases from CI, newest three kept

## Licence

MIT, see [LICENSE](LICENSE).
