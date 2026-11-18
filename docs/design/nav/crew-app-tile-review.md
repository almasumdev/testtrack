# Crew app tile review

In the dev app, sign in as `nam.bd88@gmail.com`, refresh Crew, and open **Crew 66**.
The isolated fixture is on day 8. My Apps still contains the same three apps.

| App | Tile state |
| --- | --- |
| Cherish | Ready, private feedback due by night 9 |
| Nept Track | Opened tonight, feedback sent mark on artwork |
| Goldmines Picture Movies | Reported, Test again |
| Crazy Car | Not installed |
| IT Job BD | Update required |
| Quran Sharif | Update available, current version can still open |
| SipBalance | Screenshot needed, capture or use a saved screenshot |
| Habit Craft | Feedback overdue, Write, anchored info popup |
| Smart English | Optional tonight |
| Tap Away Arrows | Left recently; uninstall is not offered yet |
| Money Manager | Left more than 24 hours ago; Uninstall |
| ScanCam | Reported, needs installation before testing again |
| EditMe | Reported, needs an update before testing again |

Pianica is also in the fixture as an app that left and is already uninstalled. Its tile is hidden.

Names and artwork come from real Circle listings. These are seeded member packages, not installed
copies of those real apps. The existing debug/dev-only installation preview supplies version 1
for member packages 100–113, allowing both update states to be reviewed. Real apps keep their
actual installation state. A testing round cannot launch these display fixtures.

Uploading, failed upload, delayed upload, and a copy installed outside Play depend on facts held
on the phone. They cannot be created through a database change. Upload behavior and screenshot
recovery are covered by the state and Compose tests; the browser catalogue includes those states.

The dev-only backend script is `scripts/seed_run_app_states.py`. It reuses Crew 66 on reruns,
validates the actual API view before committing, and changes only its own fixture records.
