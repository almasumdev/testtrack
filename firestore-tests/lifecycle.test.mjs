/**
 * What may happen to an app document now that nothing deletes one.
 *
 * The claim under test is that a package name, once submitted, is claimed forever and only an
 * admin can put it back in play. Two rules carry that between them and neither says it out loud:
 * the owner-update rule pins `status`, so a developer cannot move their own app anywhere, and the
 * new withdraw rule opens exactly one transition, `pending` to `withdrawn`, and nothing else.
 *
 * The failure worth guarding against is a widened withdraw rule. It admits a status change for an
 * owner for the first time, and every extra transition it accidentally allows is a developer
 * reviving their own rejected app, or walking out of a cohort twelve other people are depending
 * on.
 */
import fs from 'node:fs'
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing'
import { doc, getDoc, setDoc, updateDoc, deleteDoc } from 'firebase/firestore'

const RULES = fs.readFileSync(
  process.argv[2] ?? new URL('../firestore.rules', import.meta.url), 'utf8')

const env = await initializeTestEnvironment({
  projectId: 'rules-test',
  firestore: { rules: RULES, host: '127.0.0.1', port: 8080 },
})

const ME = 'uid-me'
const PEER = 'uid-peer'
const ADMIN = 'uid-admin'
const GROUP = 'g1'

let pass = 0, fail = 0
async function check(name, fn) {
  try { await fn(); console.log(`  PASS  ${name}`); pass++ }
  catch (e) { console.log(`  FAIL  ${name}\n        ${String(e).split('\n')[0]}`); fail++ }
}

const as = (uid) => env.authenticatedContext(uid).firestore()

const app = (id, extra = {}) => ({
  id, ownerUid: ME, ownerEmail: 'me@x.com', name: 'Mine', packageName: id,
  groupId: '', status: 'pending', submittedAt: 1, ...extra,
})

async function seed() {
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'admins', ADMIN), {})
    await setDoc(doc(db, 'groups', GROUP), {
      name: 'Batch 01', memberUids: [ME, PEER], appIds: ['com.me.placed'],
      startDate: Date.now() - 86400000, status: 'running',
    })
    await setDoc(doc(db, 'apps', 'com.me.waiting'), app('com.me.waiting'))
    await setDoc(doc(db, 'apps', 'com.me.placed'),
      app('com.me.placed', { groupId: GROUP, status: 'assigned', placedAt: 1 }))
    await setDoc(doc(db, 'apps', 'com.me.rejected'),
      app('com.me.rejected', { status: 'rejected', rejectedAt: 1, rejectedReason: 'no track' }))
    await setDoc(doc(db, 'apps', 'com.me.withdrawn'),
      app('com.me.withdrawn', { status: 'withdrawn', withdrawnAt: 1 }))
    await setDoc(doc(db, 'apps', 'com.me.done'), app('com.me.done', { status: 'done' }))
  })
}

console.log('\nan app document is never deleted\n')
await seed()

// ---- withdrawing, which is the one transition an owner gets ---------------------------------
await check('owner may withdraw an app that is waiting', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'apps', 'com.me.waiting'),
    { status: 'withdrawn', withdrawnAt: 2 })))

await check('owner cannot withdraw an app that is in a cohort', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', 'com.me.placed'),
    { status: 'withdrawn', withdrawnAt: 2 })))

// Re-seeded, because the check above left this app withdrawn. Editing the name of an app that is
// already withdrawn is allowed and should be: the generic owner rule covers it, `status` is
// unchanged, and it is the same correction anybody may make to a waiting app. What must not be
// allowed is riding a second field in on the transition itself, which is what this asks.
await seed()
await check('owner cannot smuggle another field through the withdrawal', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', 'com.me.waiting'),
    { status: 'withdrawn', withdrawnAt: 2, name: 'Renamed' })))

await check('a stranger cannot withdraw somebody else app', () =>
  assertFails(updateDoc(doc(as(PEER), 'apps', 'com.me.waiting'),
    { status: 'withdrawn', withdrawnAt: 2 })))

// ---- the claim: an owner can never put their own app back --------------------------------
await seed()

await check('owner cannot revive their rejected app', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', 'com.me.rejected'), { status: 'pending' })))

await check('owner cannot revive their withdrawn app', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', 'com.me.withdrawn'), { status: 'pending' })))

await check('owner cannot revive an app that finished its run', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', 'com.me.done'), { status: 'pending' })))

// The route a developer would actually take: submitting the same package again. `set` with merge
// is an update, so it meets the same pinned `status`.
await check('owner cannot resubmit over a rejected app', () =>
  assertFails(setDoc(doc(as(ME), 'apps', 'com.me.rejected'),
    { ...app('com.me.rejected'), status: 'pending' })))

await check('owner cannot place their own app', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', 'com.me.waiting'),
    { status: 'assigned', groupId: GROUP })))

// ---- corrections still work ----------------------------------------------------------------
await check('owner may still fix the name of a waiting app', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'apps', 'com.me.waiting'), { name: 'Better name' })))

await check('owner may still edit notes on a placed app', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'apps', 'com.me.placed'), { notes: 'demo / hunter2' })))

// ---- the admin holds every way back --------------------------------------------------------
await check('an admin may revive a rejected app', () =>
  assertSucceeds(updateDoc(doc(as(ADMIN), 'apps', 'com.me.rejected'),
    { status: 'pending', groupId: '', placedAt: 0 })))

await check('an admin may revive an app that finished its run', () =>
  assertSucceeds(updateDoc(doc(as(ADMIN), 'apps', 'com.me.done'), { status: 'pending' })))

await check('an admin may turn a submission down without deleting it', () =>
  assertSucceeds(updateDoc(doc(as(ADMIN), 'apps', 'com.me.waiting'),
    { status: 'rejected', rejectedAt: 2, rejectedReason: 'no closed track' })))

// ---- and the record stays readable ---------------------------------------------------------
await check('owner can still read their closed app', () =>
  assertSucceeds(getDoc(doc(as(ME), 'apps', 'com.me.withdrawn'))))

await check('an admin can read a closed app', () =>
  assertSucceeds(getDoc(doc(as(ADMIN), 'apps', 'com.me.withdrawn'))))

// ---- nothing gets deleted, by anyone -------------------------------------------------------
// Every other route out of the queue keeps the document: withdrawn, rejected, done. Delete was
// the one that did not, and it is closed rather than merely unused, because the whole point of
// the statuses above is that a submission stays where the person who made it can still see it.
await check('an owner cannot delete their own withdrawn app', () =>
  assertFails(deleteDoc(doc(as(ME), 'apps', 'com.me.withdrawn'))))

await check('an owner cannot delete an app waiting in the queue', () =>
  assertFails(deleteDoc(doc(as(ME), 'apps', 'com.me.waiting'))))

await check('an admin cannot delete a rejected app either', () =>
  assertFails(deleteDoc(doc(as(ADMIN), 'apps', 'com.me.rejected'))))

// ---- the identity of an app is fixed once it is claimed --------------------------------------
// The document id stays the package that was claimed, so repointing this field breaks nothing an
// admin would notice and sends twelve people to open something else for a fortnight.
await check('owner cannot repoint a placed app at another package', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', 'com.me.placed'),
    { packageName: 'com.someone.else' })))

await check('owner cannot repoint a waiting app either', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', 'com.me.waiting'),
    { packageName: 'com.someone.else' })))

// ---- notes are bounded where they are written, not only where they are typed -----------------
await check('owner cannot write notes past the cap', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', 'com.me.placed'), { notes: 'x'.repeat(501) })))

await check('owner may write notes right up to the cap', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'apps', 'com.me.placed'), { notes: 'x'.repeat(500) })))

await check('a submission cannot arrive with oversized notes', () =>
  assertFails(setDoc(doc(as(ME), 'apps', 'com.me.wordy'),
    { ...app('com.me.wordy'), status: 'pending', groupId: '', notes: 'x'.repeat(501) })))

console.log(`\n${pass} passed, ${fail} failed\n`)
await env.cleanup()
process.exit(fail === 0 ? 0 : 1)
