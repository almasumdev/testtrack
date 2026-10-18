# Firestore rules tests

TestTrack's first version kept its groups, apps and proof in Firestore. Records today live in the
TestTrack API, and the app reads only the update gate's `config/app` document from Firestore. The
rules in `../firestore.rules` still guard the retained legacy records, and these tests check them:
the eviction rules re-derive their whole verdict from the proof documents themselves, because a
patched client does not run any of the app's own checks.

```
cd firestore-tests
npm install
npm test
```

Needs the Firebase CLI and a JDK on `PATH` for the emulator. Nothing touches a real project: the
emulator runs locally against a throwaway project id.

The cases covered include those where a removal must be refused:

- a member who turned up on either of the two days
- anyone who is not the owner of the app that went unopened
- a run with fewer than two finished days behind it
- a member placed into the group part way through
- an app with no recorded placement date, where late arrival and truancy are indistinguishable
- renaming the group, restarting its clock, or smuggling a member in alongside a removal
