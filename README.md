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

## Where to get it, and what happens on an update

Every build is published as a signed APK on the
[releases page](https://github.com/almasumdev/testtrack/releases). Take the newest one.

Updates install straight over the top. You do not uninstall first, you do not lose your setup, and
you do not sign in again. Every release is signed with the same key, which is what allows that.
The same fact has a consequence worth knowing: a TestTrack APK from anywhere other than that page
will refuse to install over yours. That refusal is the check doing its job, not a fault to work
around.

> **বাংলায়:** প্রতিটা বিল্ড সাইন করা APK হিসেবে
> [রিলিজ পেজে](https://github.com/almasumdev/testtrack/releases) দেওয়া থাকে, সেখান থেকে সবচেয়ে
> নতুনটা নিন।
>
> আপডেট সরাসরি আগেরটার উপরেই বসে যায়। আনইনস্টল করতে হয় না, সেটআপ হারায় না, আবার সাইন ইনও করতে হয়
> না, কারণ প্রতিটা রিলিজ একই কী দিয়ে সাইন করা।
>
> এর একটা দিক জেনে রাখা ভালো, ওই পেজ ছাড়া অন্য কোথাও থেকে পাওয়া TestTrack এর APK আপনার ইনস্টল করা
> কপির উপরে বসবে না। এটা ভুল নয়, এটাই নিরাপত্তার কাজ করছে।

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

# Checking any of this for yourself

Nothing above asks to be taken on trust. Every claim is answerable from the source, and these are
the files that answer the ones worth asking.

| Question | Where it is settled |
|---|---|
| What permissions does it hold? | [AndroidManifest.xml](app/src/main/AndroidManifest.xml), seven of them, each with its reason |
| What does it do with usage access? | [UsageRepo.kt](app/src/main/java/com/eazyverse/testtrack/data/UsageRepo.kt) |
| Does it list my installed apps? | [InstalledApps.kt](app/src/main/java/com/eazyverse/testtrack/data/InstalledApps.kt), one named package at a time |
| How much of my Drive can it reach? | [Config.kt](app/src/main/java/com/eazyverse/testtrack/Config.kt), one line |
| Who can read my proofs? | [firestore.rules](firestore.rules) |
| Are those limits actually enforced? | [firestore-tests/](firestore-tests/) |
| What libraries ship in the app? | [app/build.gradle.kts](app/build.gradle.kts) |

The rules are the part that is easy to assert and hard to verify, so the refusals are tested rather
than promised. A tester cannot create a group, cannot start their own clock, cannot place their own
app, cannot read a stranger's proof, cannot read another device's push token, cannot unblock
themselves, and cannot remove a member who actually turned up.

```bash
cd firestore-tests && npm install && npm test
```

You can also build the app yourself instead of trusting the published APK. It needs Android Studio,
and `local.properties.example` lists the four values to fill in.

```bash
git clone https://github.com/almasumdev/testtrack.git
cd testtrack
cp local.properties.example local.properties
./gradlew assembleDebug
```

## Two things it deliberately does not ask for

These are worth saying because they cost the app something, and the cheaper choice was available.

**Full control of your device.** An accessibility service that scrolled and tapped inside each app
was built and then removed. It worked. It also cost every tester a *"full control of your device"*
grant, which Android revokes on every reinstall, and it bought one extra page of proof. That is
not a fair trade to ask thirteen people to make.

**Access to your Play Console.** Checking a testing track automatically would need developer-level
authorization from every app owner: a service account inside their Play Console, a linked Cloud
project, or a sensitive scope requiring Google verification. All three cost more than an
administrator looking at the submission, and deciding which cohort an app belongs in is a judgement
call anyway.

> **বাংলায়:** এই দুটো জিনিস ইচ্ছে করেই চাওয়া হয়নি, যদিও চাইলে সহজ হতো।
>
> **আপনার ডিভাইসের পূর্ণ নিয়ন্ত্রণ।** প্রতিটা অ্যাপের ভেতরে নিজে থেকে স্ক্রল আর ট্যাপ করার একটা
> সুবিধা বানানো হয়েছিল, পরে বাদ দেওয়া হয়। কাজ করত ঠিকই, কিন্তু তার জন্য প্রত্যেক টেস্টারকে
> "ডিভাইসের পূর্ণ নিয়ন্ত্রণ" দিতে হতো, আর বিনিময়ে পাওয়া যেত মাত্র এক পাতা বাড়তি প্রমাণ। তেরোজন
> মানুষকে এই শর্ত দেওয়া ন্যায্য মনে হয়নি।
>
> **আপনার Play Console এ ঢোকার অনুমতি।** টেস্টিং ট্র্যাক নিজে থেকে যাচাই করতে হলে প্রত্যেক অ্যাপ
> মালিকের Play Console এ ডেভেলপার পর্যায়ের অনুমতি লাগত। একজন অ্যাডমিনের চোখে দেখে নেওয়ার চেয়ে
> সেটার দাম অনেক বেশি, আর কোন অ্যাপ কোন দলে যাবে সেটা এমনিতেও বিচারের ব্যাপার।

## Licence

MIT, see [LICENSE](LICENSE).
