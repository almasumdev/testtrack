import fs from 'node:fs'
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing'
import { doc, getDoc, setDoc, updateDoc, deleteDoc } from 'firebase/firestore'

const DAY = 86400000
const RULES = fs.readFileSync(process.argv[2] ?? new URL("../firestore.rules", import.meta.url), 'utf8')

const env = await initializeTestEnvironment({
  projectId: 'rules-test',
  firestore: { rules: RULES, host: '127.0.0.1', port: 8080 },
})

const ME = 'uid-me'
const TARGET = 'uid-target'
const OUTSIDER = 'uid-outsider'
const GROUP = 'g1'
const MY_APP = 'com.me.app'
const TARGET_APP = 'com.target.app'

// Day 3 of the run: days 0, 1 and 2 are finished, so days 1 and 2 are the pair a flag rests on.
const START = Date.now() - 3 * DAY - 3600_000
const PLACED = START - 3600_000

let pass = 0, fail = 0
async function check(name, fn) {
  try { await fn(); console.log(`  PASS  ${name}`); pass++ }
  catch (e) { console.log(`  FAIL  ${name}\n        ${String(e).split('\n')[0]}`); fail++ }
}

async function seed(proofs = [], group = {}) {
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'groups', GROUP), {
      name: 'Group A',
      memberUids: [ME, TARGET],
      appIds: [MY_APP, TARGET_APP],
      startDate: START,
      status: 'running',
      ...group,
    })
    await setDoc(doc(db, 'apps', MY_APP), {
      id: MY_APP, ownerUid: ME, ownerEmail: 'me@x.com', name: 'Mine',
      packageName: MY_APP, groupId: GROUP, status: 'assigned',
      submittedAt: PLACED, placedAt: PLACED,
    })
    await setDoc(doc(db, 'apps', TARGET_APP), {
      id: TARGET_APP, ownerUid: TARGET, ownerEmail: 't@x.com', name: 'Theirs',
      packageName: TARGET_APP, groupId: GROUP, status: 'assigned',
      submittedAt: PLACED, placedAt: PLACED,
    })
    for (const { app, uid, day } of proofs) {
      await setDoc(doc(db, 'proofs', `${app}__${uid}__${START}__${day}`), {
        appId: app, groupId: GROUP, ownerUid: ME, testerUid: uid, day, usageMs: 40000,
        runStartedAt: START,
      })
    }
  })
}

// A flag, exactly as Repo.flagAtRisk writes it.
function flagWrite(db, { byAppId = MY_APP, targetApp = TARGET_APP, extra = {} } = {}) {
  return updateDoc(doc(db, 'apps', targetApp), {
    flaggedAt: Date.now(), flaggedByAppId: byAppId, ...extra,
  })
}

const as = (uid) => env.authenticatedContext(uid).firestore()

console.log('\nTwo missed days, by the owner of the app that went unopened')
await seed([{ app: MY_APP, uid: TARGET, day: 0 }])
await check('a cohort member may flag another for missing their days', () =>
  assertSucceeds(flagWrite(as(ME))))

console.log('\nThe member actually turned up')
await seed([{ app: MY_APP, uid: TARGET, day: 2 }])
await check('one of the two days covered, refused', () => assertFails(flagWrite(as(ME))))

await seed([{ app: MY_APP, uid: TARGET, day: 1 }])
await check('the older day covered, still refused', () => assertFails(flagWrite(as(ME))))

console.log('\nWho is asking')
await seed()
await check('an outsider cannot flag anyone', () => assertFails(flagWrite(as(OUTSIDER))))
await check('cannot claim an app you don\'t own noticed', () =>
  assertFails(flagWrite(as(TARGET), { targetApp: MY_APP, byAppId: MY_APP })))

console.log('\nWhat else the write might let through')
await seed()
await check('cannot smuggle another field in alongside the flag', () =>
  assertFails(flagWrite(as(ME), { extra: { name: 'Renamed' } })))
await check('cannot place or unplace through it', () =>
  assertFails(flagWrite(as(ME), { extra: { groupId: '' } })))
await check('cannot undo a flag, only make one', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', TARGET_APP), { flaggedAt: 0, flaggedByAppId: MY_APP })))
await check('cannot claim somebody else did the checking', () =>
  assertFails(flagWrite(as(ME), { extra: { flaggedByAppId: TARGET_APP } })))

console.log('\nA member can no longer evict, hard or soft, both capabilities are gone')
// The two writes Repo.evict used to make in one transaction: dropping the member from the group,
// and sending their app back to the queue. Nothing writes either any more, and these are the
// regression guard that says so: the same two missed days that flag an app on the block above
// no longer clear either of these, even though every fact in the write is genuine.
await seed([{ app: MY_APP, uid: TARGET, day: 0 }])
await check('the group half of an old-style eviction is refused', () =>
  assertFails(updateDoc(doc(as(ME), 'groups', GROUP), {
    memberUids: [ME], appIds: [MY_APP],
    lastEviction: { uid: TARGET, appId: TARGET_APP, byUid: ME, byAppId: MY_APP, day: 3, at: Date.now() },
  })))
await check('the app half, sending it back to the queue, is refused too', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', TARGET_APP), {
    groupId: '', status: 'pending', placedAt: 0, unplacedByAppId: MY_APP, unplacedAt: Date.now(),
  })))

// The write Repo.softRemove used to make, on its own, in one document. This is the one an
// account still running yesterday's build would actually attempt: the rules are what stop it,
// not whatever the client thinks it's doing, so this has to be proved against the write itself
// rather than assumed from deleting the Kotlin that used to send it.
await check('an old build\'s own self-removal write is refused too', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', TARGET_APP), {
    removed: true, removedAt: Date.now(), removedReason: '', removedByAppId: MY_APP,
  })))

console.log('\nA second device reaching the same verdict has nothing left to do')
await seed()
await flagWrite(as(ME))
await check('a second flag on the same app is refused', () => assertFails(flagWrite(as(ME))))

console.log('\nRemoving, flagging and reinstating are all admin-only to undo or fake')
{
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'apps', MY_APP), {
      id: MY_APP, ownerUid: ME, ownerEmail: 'me@x.com', name: 'Mine',
      packageName: MY_APP, groupId: GROUP, status: 'assigned',
      submittedAt: PLACED, placedAt: PLACED,
      removed: true, removedAt: PLACED, removedReason: '',
      flaggedAt: PLACED, flaggedByAppId: TARGET_APP,
    })
  })

  // The whole attack, and from every other angle it is an ordinary update: the owner is the
  // owner, the status is unchanged, the group is unchanged, and one boolean undoes a decision
  // that was never theirs to take.
  await check('an owner cannot lift their own removal', () =>
    assertFails(updateDoc(doc(as(ME), 'apps', MY_APP), { removed: false })))
  await check('nor smuggle it in beside a legitimate field', () =>
    assertFails(updateDoc(doc(as(ME), 'apps', MY_APP), { name: 'Renamed', removed: false })))
  await check('nor rewrite when it happened', () =>
    assertFails(updateDoc(doc(as(ME), 'apps', MY_APP), { removedAt: 0 })))
  await check('nor the reason it gives', () =>
    assertFails(updateDoc(doc(as(ME), 'apps', MY_APP), { removedReason: 'nothing to see here' })))
  await check('an owner cannot clear their own flag', () =>
    assertFails(updateDoc(doc(as(ME), 'apps', MY_APP), { flaggedAt: 0, flaggedByAppId: '' })))
  await check('nor backdate their own reinstatement', () =>
    assertFails(updateDoc(doc(as(ME), 'apps', MY_APP), { reinstatedAt: Date.now() })))

  // Six keys are refused, not every write. An owner still owns the rest of their document, and
  // closing this by taking that away would be the worse trade.
  await check('but still owns the rest of the document', () =>
    assertSucceeds(updateDoc(doc(as(ME), 'apps', MY_APP), { name: 'Renamed' })))
}

{
  await env.clearFirestore()
  await seed([])
  // Flagging your own app cannot help you, nobody would be testing it either way, but it
  // would tell an admin something false about who noticed.
  await check('nobody may flag their own app', () =>
    assertFails(flagWrite(as(TARGET), { targetApp: TARGET_APP, byAppId: TARGET_APP })))
}

{
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'groups', GROUP), {
      name: 'Group A', memberUids: [ME, TARGET], appIds: [MY_APP, TARGET_APP],
      startDate: START, status: 'running',
    })
    await setDoc(doc(db, 'apps', MY_APP), {
      id: MY_APP, ownerUid: ME, ownerEmail: 'me@x.com', name: 'Mine',
      packageName: MY_APP, groupId: GROUP, status: 'assigned',
      submittedAt: PLACED, placedAt: PLACED,
    })
    // Already taken out of testing before this sweep ever ran.
    await setDoc(doc(db, 'apps', TARGET_APP), {
      id: TARGET_APP, ownerUid: TARGET, ownerEmail: 't@x.com', name: 'Theirs',
      packageName: TARGET_APP, groupId: GROUP, status: 'assigned',
      submittedAt: PLACED, placedAt: PLACED,
      removed: true, removedAt: PLACED, removedReason: '',
    })
  })
  // Without the removed guard this would succeed: the two missed days are genuine, and nothing
  // else here would refuse it, which is exactly how a removed app used to get re-flagged, and
  // re-notified about, on every single sweep after the one that excused it.
  await check('an already-removed app cannot be flagged again', () => assertFails(flagWrite(as(ME))))
}

{
  await env.clearFirestore()
  // TARGET opening MY_APP is what proves they turned up. The other direction is me testing
  // them, which says nothing about whether they did their days.
  await seed([{ app: MY_APP, uid: TARGET, day: 1 }, { app: MY_APP, uid: TARGET, day: 2 }])
  await check('somebody who has been reporting cannot be flagged', () =>
    assertFails(flagWrite(as(ME))))
}


console.log('\nOpening days an admin excused are owed by nobody')
{
  // START is three days back, so today is day 3 and days 2 and 1 would ordinarily be judged.
  await seed([], { graceDays: 2 })
  await check('a flag inside the excused days is refused', () => assertFails(flagWrite(as(ME))))

  // Same group, same absences, nothing excused. Without this the test above proves only that
  // something refused it, not that the excusing did.
  await seed([], { graceDays: 0 })
  await check('and allowed once nothing is excused', () => assertSucceeds(flagWrite(as(ME))))

  // Groups written before any of this have no graceDays at all, and have to carry on behaving as
  // they always did rather than erroring their way into a refusal.
  await seed([])
  await check('a group with no graceDays field behaves as it always did', () =>
    assertSucceeds(flagWrite(as(ME))))
}


console.log('\nToo early in the run to judge anyone')
{
  const runStart = Date.now() - 1 * DAY - 3600_000  // day 1: only day 0 is finished
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'groups', GROUP), {
      name: 'Group A', memberUids: [ME, TARGET], appIds: [MY_APP, TARGET_APP],
      startDate: runStart, status: 'running',
    })
    await setDoc(doc(db, 'apps', MY_APP), {
      id: MY_APP, ownerUid: ME, ownerEmail: 'me@x.com', name: 'Mine', packageName: MY_APP,
      groupId: GROUP, status: 'assigned', submittedAt: runStart - 1000, placedAt: runStart - 1000,
    })
    await setDoc(doc(db, 'apps', TARGET_APP), {
      id: TARGET_APP, ownerUid: TARGET, ownerEmail: 't@x.com', name: 'Theirs', packageName: TARGET_APP,
      groupId: GROUP, status: 'assigned', submittedAt: runStart - 1000, placedAt: runStart - 1000,
    })
  })
  await check('day 1, no two finished days yet, refused', () => assertFails(flagWrite(as(ME))))
}

console.log('\nPlaced mid-run, so the earlier days were never theirs to miss')
{
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'groups', GROUP), {
      name: 'Group A', memberUids: [ME, TARGET], appIds: [MY_APP, TARGET_APP],
      startDate: START, status: 'running',
    })
    await setDoc(doc(db, 'apps', MY_APP), {
      id: MY_APP, ownerUid: ME, ownerEmail: 'me@x.com', name: 'Mine', packageName: MY_APP,
      groupId: GROUP, status: 'assigned', submittedAt: PLACED, placedAt: PLACED,
    })
    // Arrived part way through day 2, so day 3 is the first they can be held to.
    await setDoc(doc(db, 'apps', TARGET_APP), {
      id: TARGET_APP, ownerUid: TARGET, ownerEmail: 't@x.com', name: 'Theirs', packageName: TARGET_APP,
      groupId: GROUP, status: 'assigned', submittedAt: PLACED, placedAt: START + 2 * DAY + 1000,
    })
  })
  await check('a member who joined on day 2 is safe on day 3', () => assertFails(flagWrite(as(ME))))
}

console.log('\nNo placement date recorded, so nothing can be proved')
{
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'groups', GROUP), {
      name: 'Group A', memberUids: [ME, TARGET], appIds: [MY_APP, TARGET_APP],
      startDate: START, status: 'running',
    })
    await setDoc(doc(db, 'apps', MY_APP), {
      id: MY_APP, ownerUid: ME, ownerEmail: 'me@x.com', name: 'Mine', packageName: MY_APP,
      groupId: GROUP, status: 'assigned', submittedAt: PLACED, placedAt: PLACED,
    })
    await setDoc(doc(db, 'apps', TARGET_APP), {
      id: TARGET_APP, ownerUid: TARGET, ownerEmail: 't@x.com', name: 'Theirs', packageName: TARGET_APP,
      groupId: GROUP, status: 'assigned', submittedAt: PLACED,
    })
  })
  await check('legacy app with no placedAt is refused', () => assertFails(flagWrite(as(ME))))
}

console.log('\nRun length is an admin decision')
await seed([{ app: MY_APP, uid: TARGET, day: 0 }])
await env.withSecurityRulesDisabled((ctx) =>
  updateDoc(doc(ctx.firestore(), 'groups', GROUP), { runDays: 21 }))
// An extended run is still a run, so flags carry on inside it rather than stopping at day 14.
await check('an extended run still flags', () => assertSucceeds(flagWrite(as(ME))))

// And past the end of one, nothing is owed, so nothing can be missed.
await env.withSecurityRulesDisabled((ctx) =>
  updateDoc(doc(ctx.firestore(), 'groups', GROUP), { runDays: 2 }))
await check('a finished run flags nobody', () => assertFails(flagWrite(as(ME))))

console.log('\nReinstating gives a genuine fresh start, not an invitation to re-flag on the spot')
{
  // Same two missed days that would ordinarily flag TARGET: see the very first block above.
  await seed([])
  await env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'apps', TARGET_APP), { reinstatedAt: Date.now() }))
  await check('the same two days do not flag again once reinstated', () =>
    assertFails(flagWrite(as(ME))))

  // An app with no reinstatedAt at all has to behave exactly as it did before this existed.
  await seed([])
  await check('an app never reinstated behaves as it always did', () =>
    assertSucceeds(flagWrite(as(ME))))

  // A custom timeline: today is day 5, TARGET was reinstated partway through day 2, and has
  // missed the two days since: proving reinstatement only forgives the days before it, not
  // every day forever after.
  await env.clearFirestore()
  const runStart = Date.now() - 5 * DAY - 3600_000
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'groups', GROUP), {
      name: 'Group A', memberUids: [ME, TARGET], appIds: [MY_APP, TARGET_APP],
      startDate: runStart, status: 'running',
    })
    await setDoc(doc(db, 'apps', MY_APP), {
      id: MY_APP, ownerUid: ME, ownerEmail: 'me@x.com', name: 'Mine', packageName: MY_APP,
      groupId: GROUP, status: 'assigned', submittedAt: runStart - 1000, placedAt: runStart - 1000,
    })
    await setDoc(doc(db, 'apps', TARGET_APP), {
      id: TARGET_APP, ownerUid: TARGET, ownerEmail: 't@x.com', name: 'Theirs', packageName: TARGET_APP,
      groupId: GROUP, status: 'assigned', submittedAt: runStart - 1000, placedAt: runStart - 1000,
      reinstatedAt: runStart + 2 * DAY + 1000,
    })
  })
  await check('two fresh misses after reinstatement still flag', () => assertSucceeds(flagWrite(as(ME))))
}

console.log('\nReporting an app that would not open, or broke, before an admin hears any other way')
{
  // Exactly as Repo.reportProblem writes it.
  const report = (db, appId, over = {}) => updateDoc(doc(db, 'apps', appId), {
    reportedAt: Date.now(), reportedBy: ME, reportedReason: 'Install fails at the sign-in step.',
    ...over,
  })

  await seed()
  await check('a cohort member may report an app they are testing', () =>
    assertSucceeds(report(as(ME), TARGET_APP)))

  await seed()
  await check('the owner may not report their own app', () =>
    assertFails(report(as(TARGET), TARGET_APP, { reportedBy: TARGET })))

  await seed()
  await check('an outsider may not report an app outside their cohort', () =>
    assertFails(report(as(OUTSIDER), TARGET_APP, { reportedBy: OUTSIDER })))

  await seed()
  await check('cannot claim somebody else filed the report', () =>
    assertFails(report(as(ME), TARGET_APP, { reportedBy: TARGET })))

  await seed()
  await check('cannot smuggle another field in alongside a report', () =>
    assertFails(report(as(ME), TARGET_APP, { name: 'Renamed' })))

  await seed()
  await check('a reason past the cap is refused', () =>
    assertFails(report(as(ME), TARGET_APP, { reportedReason: 'x'.repeat(501) })))

  // Already reported once: set directly, the way a prior report would have landed.
  const reseedReport = () => env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'apps', TARGET_APP), {
      reportedAt: Date.now() - DAY, reportedBy: ME, reportedReason: 'Already noted.',
    }))
  await reseedReport()
  await check('a second report cannot overwrite the first', () =>
    assertFails(report(as(ME), TARGET_APP)))

  // Exactly as Repo.clearMyReport writes it.
  const clearReport = (db, appId, over = {}) => updateDoc(doc(db, 'apps', appId), {
    reportedAt: 0, reportedBy: '', reportedReason: '', ...over,
  })

  await check('an outsider cannot clear somebody else\'s report', () =>
    assertFails(clearReport(as(OUTSIDER), TARGET_APP)))
  await check('the owner may not clear a report filed against their own app', () =>
    assertFails(clearReport(as(TARGET), TARGET_APP)))
  await check('a partial clear that leaves reportedBy set is refused', () =>
    assertFails(updateDoc(doc(as(ME), 'apps', TARGET_APP), { reportedAt: 0 })))
  await check('the reporter may take their own report back', () =>
    assertSucceeds(clearReport(as(ME), TARGET_APP)))

  await reseedReport()
  await check('cannot clear a report while smuggling another field in', () =>
    assertFails(clearReport(as(ME), TARGET_APP, { name: 'Renamed' })))
  await check('cannot "clear" while actually leaving a reason behind', () =>
    assertFails(clearReport(as(ME), TARGET_APP, { reportedReason: 'still here' })))
}

console.log('\nA tester leaving day-one feedback on an app that otherwise went fine')
{
  const DAY_INDEX = 3
  const feedbackId = (appId, uid) => `${appId}__${uid}__${START}__${DAY_INDEX}`
  // Exactly as Repo.submitFeedback writes it.
  const giveFeedback = (db, appId, uid, ownerUid, over = {}) => setDoc(doc(db, 'feedback', feedbackId(appId, uid)), {
    appId, groupId: GROUP, ownerUid, testerUid: uid, day: DAY_INDEX, runStartedAt: START,
    text: 'The settings icon is hard to find.', createdAt: Date.now(),
    ...over,
  })

  await seed()
  await check('a cohort member may leave feedback on an app they are testing', () =>
    assertSucceeds(giveFeedback(as(ME), TARGET_APP, ME, TARGET)))

  await seed()
  await check('the owner may not leave feedback on their own app', () =>
    assertFails(giveFeedback(as(TARGET), TARGET_APP, TARGET, TARGET)))

  await seed()
  await check('an outsider may not leave feedback outside their cohort', () =>
    assertFails(giveFeedback(as(OUTSIDER), TARGET_APP, OUTSIDER, TARGET)))

  await seed()
  await check('cannot claim somebody else left the feedback', () =>
    assertFails(giveFeedback(as(ME), TARGET_APP, TARGET, TARGET)))

  await seed()
  await check('cannot put the wrong owner on the note', () =>
    assertFails(giveFeedback(as(ME), TARGET_APP, ME, ME)))

  await seed()
  await check('empty feedback is refused', () =>
    assertFails(giveFeedback(as(ME), TARGET_APP, ME, TARGET, { text: '' })))

  await seed()
  await check('feedback past the cap is refused', () =>
    assertFails(giveFeedback(as(ME), TARGET_APP, ME, TARGET, { text: 'x'.repeat(1001) })))

  await seed()
  await check('a second note the same day overwrites rather than piling up', async () => {
    await giveFeedback(as(ME), TARGET_APP, ME, TARGET)
    await assertSucceeds(giveFeedback(as(ME), TARGET_APP, ME, TARGET, { text: 'Actually, never mind.' }))
  })

  await seed()
  await giveFeedback(as(ME), TARGET_APP, ME, TARGET)
  await check('the tester who wrote it may read it back', () =>
    assertSucceeds(getDoc(doc(as(ME), 'feedback', feedbackId(TARGET_APP, ME)))))
  await check('the app owner may read it', () =>
    assertSucceeds(getDoc(doc(as(TARGET), 'feedback', feedbackId(TARGET_APP, ME)))))
  await check('an outsider may not read it', () =>
    assertFails(getDoc(doc(as(OUTSIDER), 'feedback', feedbackId(TARGET_APP, ME)))))
  await check('the tester cannot delete their own note', () =>
    assertFails(deleteDoc(doc(as(ME), 'feedback', feedbackId(TARGET_APP, ME)))))
  await check('the owner cannot delete a note about their app', () =>
    assertFails(deleteDoc(doc(as(TARGET), 'feedback', feedbackId(TARGET_APP, ME)))))
}

console.log('\nA reported app is judged by nobody until the report is cleared')
{
  // TARGET has missed both judged days on MY_APP, which would ordinarily allow ME to flag them:
  // see the very first block in this file. The only difference here is that MY_APP itself carries
  // an open report.
  await seed([])
  await env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'apps', MY_APP), {
      reportedAt: Date.now(), reportedBy: TARGET, reportedReason: 'Crashes on launch.',
    }))
  await check('the flag this would otherwise allow is refused', () =>
    assertFails(flagWrite(as(ME))))

  // Clearing the report: an admin-only write, done directly here the way the console does it:
  // puts the ordinary flag back in reach without anything else about the day changing.
  await env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'apps', MY_APP), { reportedAt: 0, reportedBy: '', reportedReason: '' }))
  await check('and clearing it restores the ordinary flag', () =>
    assertSucceeds(flagWrite(as(ME))))
}

console.log('\nAn owner broadcasting "update available" to one member of their cohort')
{
  const broadcastEvent = (over = {}) => ({
    uid: TARGET, type: 'owner_broadcast', groupId: GROUP, appId: MY_APP,
    actorUid: ME, title: 'Mine has an update', body: 'Please update and keep testing.',
    createdAt: Date.now(), ...over,
  })
  const bumpBroadcast = (db, appId = MY_APP, at = Date.now()) =>
    updateDoc(doc(db, 'apps', appId), { lastBroadcastAt: at })

  await seed()
  await check('the owner may tell a cohort member', () =>
    assertSucceeds(setDoc(doc(as(ME), 'events', 'bc1'), broadcastEvent())))
  await check('and may advance their own app\'s cooldown', () =>
    assertSucceeds(bumpBroadcast(as(ME))))

  await seed()
  await check('nobody may broadcast about somebody else\'s app', () =>
    assertFails(setDoc(doc(as(TARGET), 'events', 'bc2'), broadcastEvent({ actorUid: TARGET }))))
  await check('an outsider may not broadcast into the group at all', () =>
    assertFails(setDoc(doc(as(OUTSIDER), 'events', 'bc3'),
      broadcastEvent({ actorUid: OUTSIDER }))))
  await check('cannot address a broadcast to somebody outside the cohort', () =>
    assertFails(setDoc(doc(as(ME), 'events', 'bc4'), broadcastEvent({ uid: OUTSIDER }))))
  await check('cannot broadcast to yourself', () =>
    assertFails(setDoc(doc(as(ME), 'events', 'bc5'), broadcastEvent({ uid: ME }))))
  await check('no other event type reaches this rule', () =>
    assertFails(setDoc(doc(as(ME), 'events', 'bc6'), broadcastEvent({ type: 'assigned' }))))
  await check('the app-side bump is refused for somebody else\'s app', () =>
    assertFails(bumpBroadcast(as(TARGET), MY_APP)))
  await check('cannot smuggle another field in alongside the bump', () =>
    assertFails(updateDoc(doc(as(ME), 'apps', MY_APP), { lastBroadcastAt: Date.now(), name: 'Renamed' })))

  console.log('\n...and cannot repeat within the cooldown')
  await seed()
  await env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'apps', MY_APP), { lastBroadcastAt: Date.now() - DAY }))
  await check('a second broadcast inside two days is refused', () =>
    assertFails(setDoc(doc(as(ME), 'events', 'bc7'), broadcastEvent())))
  await check('and so is bumping the timestamp again on its own', () =>
    assertFails(bumpBroadcast(as(ME))))

  await env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'apps', MY_APP), { lastBroadcastAt: Date.now() - 3 * DAY }))
  await check('but a broadcast past two days is allowed again', () =>
    assertSucceeds(setDoc(doc(as(ME), 'events', 'bc8'), broadcastEvent())))

  await seed()
  await check('a group with no prior broadcast behaves as day zero, not as blocked', () =>
    assertSucceeds(setDoc(doc(as(ME), 'events', 'bc9'), broadcastEvent())))
}

console.log('\nAn owner asking one member of their cohort for feedback')
{
  const askEvent = (over = {}) => ({
    uid: TARGET, type: 'feedback_ask', groupId: GROUP, appId: MY_APP,
    actorUid: ME, title: 'Mine would like your feedback', body: 'Leave a note when you can.',
    createdAt: Date.now(), ...over,
  })
  const bumpAsk = (db, appId = MY_APP, at = Date.now()) =>
    updateDoc(doc(db, 'apps', appId), { lastFeedbackAskAt: at })

  await seed()
  await check('the owner may ask a cohort member', () =>
    assertSucceeds(setDoc(doc(as(ME), 'events', 'fa1'), askEvent())))
  await check('and may advance their own app\'s cooldown', () =>
    assertSucceeds(bumpAsk(as(ME))))

  await seed()
  await check('nobody may ask on behalf of somebody else\'s app', () =>
    assertFails(setDoc(doc(as(TARGET), 'events', 'fa2'), askEvent({ actorUid: TARGET }))))
  await check('an outsider may not ask into the group at all', () =>
    assertFails(setDoc(doc(as(OUTSIDER), 'events', 'fa3'),
      askEvent({ actorUid: OUTSIDER }))))
  await check('cannot address an ask to somebody outside the cohort', () =>
    assertFails(setDoc(doc(as(ME), 'events', 'fa4'), askEvent({ uid: OUTSIDER }))))
  await check('cannot ask yourself for feedback', () =>
    assertFails(setDoc(doc(as(ME), 'events', 'fa5'), askEvent({ uid: ME }))))
  await check('no other event type reaches this rule', () =>
    assertFails(setDoc(doc(as(ME), 'events', 'fa6'), askEvent({ type: 'assigned' }))))
  await check('the app-side bump is refused for somebody else\'s app', () =>
    assertFails(bumpAsk(as(TARGET), MY_APP)))
  await check('cannot smuggle another field in alongside the bump', () =>
    assertFails(updateDoc(doc(as(ME), 'apps', MY_APP), { lastFeedbackAskAt: Date.now(), name: 'Renamed' })))

  console.log('\n...and cannot repeat within the cooldown')
  await seed()
  await env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'apps', MY_APP), { lastFeedbackAskAt: Date.now() - DAY }))
  await check('a second ask inside two days is refused', () =>
    assertFails(setDoc(doc(as(ME), 'events', 'fa7'), askEvent())))
  await check('and so is bumping the timestamp again on its own', () =>
    assertFails(bumpAsk(as(ME))))

  await env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'apps', MY_APP), { lastFeedbackAskAt: Date.now() - 3 * DAY }))
  await check('but an ask past two days is allowed again', () =>
    assertSucceeds(setDoc(doc(as(ME), 'events', 'fa8'), askEvent())))

  await seed()
  await check('a group with no prior ask behaves as day zero, not as blocked', () =>
    assertSucceeds(setDoc(doc(as(ME), 'events', 'fa9'), askEvent())))

  console.log('\n...and the two cooldowns are independent')
  await seed()
  await env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'apps', MY_APP), { lastBroadcastAt: Date.now() }))
  await check('a fresh broadcast cooldown does not block an ask', () =>
    assertSucceeds(setDoc(doc(as(ME), 'events', 'fa10'), askEvent())))
}

console.log('\nA tester telling an app\'s owner that feedback is waiting for them')
{
  const feedbackEvent = (over = {}) => ({
    uid: ME, type: 'feedback', groupId: GROUP, appId: MY_APP,
    actorUid: TARGET, title: 'New feedback on Mine', body: 'The settings icon is hard to find.',
    createdAt: Date.now(), ...over,
  })

  await seed()
  await check('a tester may tell the owner feedback is waiting', () =>
    assertSucceeds(setDoc(doc(as(TARGET), 'events', 'fb1'), feedbackEvent())))
  await check('an owner cannot notify themselves about their own app', () =>
    assertFails(setDoc(doc(as(ME), 'events', 'fb2'), feedbackEvent({ actorUid: ME }))))
  await check('an outsider may not notify into the group at all', () =>
    assertFails(setDoc(doc(as(OUTSIDER), 'events', 'fb3'),
      feedbackEvent({ actorUid: OUTSIDER }))))
  await check('cannot claim somebody else sent it', () =>
    assertFails(setDoc(doc(as(TARGET), 'events', 'fb4'), feedbackEvent({ actorUid: ME }))))
  await check('cannot address it to somebody who doesn\'t own the app', () =>
    assertFails(setDoc(doc(as(TARGET), 'events', 'fb5'), feedbackEvent({ uid: TARGET }))))
  await check('no other event type reaches this rule', () =>
    assertFails(setDoc(doc(as(TARGET), 'events', 'fb6'), feedbackEvent({ type: 'assigned' }))))
}

console.log('\nA member skipping one app on their own, once an admin has allowed it')
{
  const skipDoc = (appId = TARGET_APP, uid = ME) => ({ appId, uid, groupId: GROUP, skippedAt: Date.now() })
  const skip = (db, appId = TARGET_APP, uid = ME) =>
    setDoc(doc(db, 'skips', `${appId}__${uid}`), skipDoc(appId, uid))

  await seed()
  await check('not skippable by default: a member may not skip', () => assertFails(skip(as(ME))))

  await env.withSecurityRulesDisabled((ctx) =>
    updateDoc(doc(ctx.firestore(), 'apps', TARGET_APP), { skippable: true }))
  await check('but may once the app is marked skippable', () => assertSucceeds(skip(as(ME))))
  await check('and may un-skip it again', () =>
    assertSucceeds(deleteDoc(doc(as(ME), 'skips', `${TARGET_APP}__${ME}`))))

  await check('the owner may not skip their own app', () =>
    assertFails(skip(as(TARGET), TARGET_APP, TARGET)))
  await check('an outsider may not skip an app outside their cohort', () =>
    assertFails(skip(as(OUTSIDER), TARGET_APP, OUTSIDER)))
  await check('cannot claim to be somebody else while skipping', () =>
    assertFails(setDoc(doc(as(ME), 'skips', `${TARGET_APP}__${TARGET}`), skipDoc(TARGET_APP, TARGET))))
  await check('the doc id must match app and uid, not just the fields inside it', () =>
    assertFails(setDoc(doc(as(ME), 'skips', 'not-the-right-id'), skipDoc())))

  await skip(as(ME))
  await check('an outsider may not un-skip somebody else\'s row', () =>
    assertFails(deleteDoc(doc(as(OUTSIDER), 'skips', `${TARGET_APP}__${ME}`))))
  await check('and neither may another cohort member', () =>
    assertFails(deleteDoc(doc(as(TARGET), 'skips', `${TARGET_APP}__${ME}`))))

  console.log('\n...and a skipped app judges nobody who skipped it')
  // TARGET has missed both judged days on MY_APP, same setup as the very first block in this file
  //: the only difference is TARGET has skipped it.
  await seed([])
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await updateDoc(doc(db, 'apps', MY_APP), { skippable: true })
    await setDoc(doc(db, 'skips', `${MY_APP}__${TARGET}`), skipDoc(MY_APP, TARGET))
  })
  await check('the flag this would otherwise allow is refused', () =>
    assertFails(flagWrite(as(ME))))

  // A skip only ever excuses the member who made it: it must not read as a blanket exemption.
  await seed([])
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await updateDoc(doc(db, 'apps', MY_APP), { skippable: true })
    await setDoc(doc(db, 'skips', `${MY_APP}__${OUTSIDER}`), skipDoc(MY_APP, OUTSIDER))
  })
  await check('but someone else skipping the same app does not excuse this member', () =>
    assertSucceeds(flagWrite(as(ME))))
}

console.log('\nA tester can no longer write an automatic-removal record: that capability is gone')
// This is what Repo.evict used to write for itself, back when a device could evict somebody on
// the spot. Nothing writes it any more, and this is the regression guard that says so: not an
// absence nobody notices, a write that is now refused even though every fact in it is genuine.
await seed([{ app: MY_APP, uid: TARGET, day: 0 }])
await check('the removal event a device used to write for itself is refused', () =>
  assertFails(setDoc(doc(as(ME), 'events', 'ev1'), {
    uid: TARGET, type: 'removed_missed', groupId: GROUP, appId: TARGET_APP,
    byAppId: MY_APP, actorUid: ME, day: 3, createdAt: Date.now(),
    title: 'Your app was removed from its group', body: 'Two days went by.',
  })))

console.log('\nBlocking, and the ban nobody may lift for themselves')
async function seedUser(blocked) {
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'users', ME), {
      uid: ME, email: 'me@x.com', displayName: 'Me',
      ...(blocked ? { blocked: true, blockedReason: 'kept missing days' } : {}),
    })
  })
}

const newApp = (db) => setDoc(doc(db, 'apps', 'com.new.app'), {
  id: 'com.new.app', ownerUid: ME, ownerEmail: 'me@x.com', name: 'New',
  packageName: 'com.new.app', groupId: '', status: 'pending', submittedAt: Date.now(),
})

await seedUser(false)
await check('an account in good standing may submit', () => assertSucceeds(newApp(as(ME))))

await seedUser(true)
await check('a blocked account may not submit', () => assertFails(newApp(as(ME))))

await check('a blocked account cannot clear its own block', () =>
  assertFails(updateDoc(doc(as(ME), 'users', ME), { blocked: false })))

await check('nor smuggle it out alongside a name change', () =>
  assertFails(updateDoc(doc(as(ME), 'users', ME), { displayName: 'Me2', blocked: false })))

await check('but may still correct its own name', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'users', ME), { displayName: 'Me Again' })))

await check('and cannot block somebody else', () =>
  assertFails(updateDoc(doc(as(ME), 'users', TARGET), { blocked: true })))

console.log('\nThe read submitApp makes before it writes')

// submitApp reads apps/{package} before writing and treats PERMISSION_DENIED as "somebody else
// holds this name". That makes the absent case load-bearing: `resource` is null for a document
// that does not exist, and a rule that dereferences it errors, which denies. Without a branch for
// null the very first submission of any package is reported to its own author as already taken.
await seed()

await check('a package nobody has registered reads as absent, not refused', () =>
  assertSucceeds(getDoc(doc(as(OUTSIDER), 'apps', 'com.nobody.has.this'))))

await check('an owner may read their own app', () =>
  assertSucceeds(getDoc(doc(as(ME), 'apps', MY_APP))))

await check('a cohort member may read an app they are testing', () =>
  assertSucceeds(getDoc(doc(as(ME), 'apps', TARGET_APP))))

// The refusal that still carries meaning. A name held by a stranger stays unreadable, which is
// exactly what submitApp turns into "already registered by another member", without ever
// revealing whose it is.
await check('a stranger’s app stays refused, which is what marks the name taken', () =>
  assertFails(getDoc(doc(as(OUTSIDER), 'apps', MY_APP))))

console.log(`\n${pass} passed, ${fail} failed\n`)
await env.cleanup()
process.exit(fail === 0 ? 0 : 1)
