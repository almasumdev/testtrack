# Rules tests

The eviction rules in `../firestore.rules` are the only place a tester's device is allowed to
write to `groups` or to somebody else's `apps` document. Nothing in the Kotlin can protect that
opening — a patched client simply does not run the Kotlin — so the rules re-derive the whole
verdict from the proof documents themselves, and these tests are what say they do.

```
cd firestore-tests
npm install
npm test
```

Needs the Firebase CLI and a JDK on `PATH` for the emulator. Nothing touches a real project: the
emulator runs locally against a throwaway project id.

## What the parity is

`Compliance` in the tester app and `missedTwo`/`firstJudged` in the rules compute the same sums,
in two languages, and both have to agree. If they drift, evictions start coming back
`PERMISSION_DENIED` with nothing visibly wrong. `ComplianceTest` covers the Kotlin half; these
cover the rules half, including the cases where a removal must be refused:

- a member who turned up on either of the two days
- anyone who is not the owner of the app that went unopened
- a run with fewer than two finished days behind it
- a member placed into the group part way through
- an app with no recorded placement date, where late arrival and truancy are indistinguishable
- renaming the group, restarting its clock, or smuggling a member in alongside a removal
