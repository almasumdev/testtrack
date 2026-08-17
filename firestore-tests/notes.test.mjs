/**
 * Who may write and read the notes a developer attaches to their app.
 *
 * No rule was added for this field. The claim being tested is that the rules already in place
 * cover it: the owner-update rule pins `status`, `groupId` and the three `removed*` keys and says
 * nothing about anything else, and the read rule already admits the owner, the cohort and admins.
 * A claim like that is worth a test rather than a paragraph, because the thing it protects is a
 * field people are being told to put a password into.
 */
import fs from 'node:fs'
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing'
import { doc, getDoc, setDoc, updateDoc } from 'firebase/firestore'

const RULES = fs.readFileSync(
  process.argv[2] ?? new URL('../firestore.rules', import.meta.url), 'utf8')

const env = await initializeTestEnvironment({
  projectId: 'rules-test',
  firestore: { rules: RULES, host: '127.0.0.1', port: 8080 },
})

const ME = 'uid-me'
const PEER = 'uid-peer'
const OUTSIDER = 'uid-outsider'
const ADMIN = 'uid-admin'
const GROUP = 'g1'
const MY_APP = 'com.me.app'
const UNPLACED = 'com.me.unplaced'

let pass = 0, fail = 0
async function check(name, fn) {
  try { await fn(); console.log(`  PASS  ${name}`); pass++ }
  catch (e) { console.log(`  FAIL  ${name}\n        ${String(e).split('\n')[0]}`); fail++ }
}

const as = (uid) => env.authenticatedContext(uid).firestore()

async function seed() {
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'admins', ADMIN), {})
    await setDoc(doc(db, 'groups', GROUP), {
      name: 'Group A',
      memberUids: [ME, PEER],
      appIds: [MY_APP],
      startDate: Date.now() - 86400000,
      status: 'running',
    })
    // Placed and running, which is the case the owner-update rule has to keep allowing.
    await setDoc(doc(db, 'apps', MY_APP), {
      id: MY_APP, ownerUid: ME, ownerEmail: 'me@x.com', name: 'Mine',
      packageName: MY_APP, groupId: GROUP, status: 'assigned',
      submittedAt: 1, placedAt: 1, notes: 'demo@x.com / hunter2',
    })
    // Submitted and waiting, which is the other case.
    await setDoc(doc(db, 'apps', UNPLACED), {
      id: UNPLACED, ownerUid: ME, ownerEmail: 'me@x.com', name: 'Waiting',
      packageName: UNPLACED, groupId: '', status: 'pending', submittedAt: 1,
    })
  })
}

console.log('\nnotes on an app document\n')
await seed()

// ---- the owner ---------------------------------------------------------------------------
await check('owner may add notes while the app is waiting for a group', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'apps', UNPLACED), { notes: 'try demo@x.com' })))

await check('owner may change notes while the app is in a running group', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'apps', MY_APP), { notes: 'new password is pass1234' })))

await check('owner may clear the notes', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'apps', MY_APP), { notes: '' })))

await check('owner may submit with notes already on it', () =>
  assertSucceeds(setDoc(doc(as(ME), 'apps', 'com.me.fresh'), {
    id: 'com.me.fresh', ownerUid: ME, ownerEmail: 'me@x.com', name: 'Fresh',
    packageName: 'com.me.fresh', groupId: '', status: 'pending', submittedAt: 1,
    notes: 'sign in with the test account',
  })))

// The field must not become a way round the parts of the document that are not the owner's.
await check('owner cannot undo a removal while editing notes', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', MY_APP), { notes: 'x', removed: false })))

await check('owner cannot place their own app while editing notes', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', UNPLACED), { notes: 'x', groupId: GROUP })))

// ---- everybody else ----------------------------------------------------------------------
await check('a member of the cohort can read the notes', () =>
  assertSucceeds(getDoc(doc(as(PEER), 'apps', MY_APP))))

await check('an admin can read the notes', () =>
  assertSucceeds(getDoc(doc(as(ADMIN), 'apps', MY_APP))))

await check('a member of the cohort cannot write the notes', () =>
  assertFails(updateDoc(doc(as(PEER), 'apps', MY_APP), { notes: 'not yours to write' })))

await check('somebody outside the cohort cannot read the notes', () =>
  assertFails(getDoc(doc(as(OUTSIDER), 'apps', MY_APP))))

await check('nobody outside the cohort can write the notes', () =>
  assertFails(updateDoc(doc(as(OUTSIDER), 'apps', MY_APP), { notes: 'x' })))

console.log(`\n${pass} passed, ${fail} failed\n`)
await env.cleanup()
process.exit(fail === 0 ? 0 : 1)
