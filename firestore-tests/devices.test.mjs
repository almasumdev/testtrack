/**
 * Who may write and read a device fingerprint.
 *
 * The point of the `devices` block is that it is NOT the user document. `users/{uid}` is readable
 * by every signed-in account, on purpose, so a grid can turn a uid into a name. A fingerprint kept
 * there would let any tester in a cohort work out which of their thirteen colleagues share a
 * phone. So the claim worth testing is the narrow one: an owner can write and read their own row,
 * an admin can read the lot, and a tester can do neither to anybody else, including the part that
 * is easy to get wrong, which is that an owner's read permission must not let them enumerate.
 */
import fs from 'node:fs'
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing'
import { collection, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc } from 'firebase/firestore'

const RULES = fs.readFileSync(
  process.argv[2] ?? new URL('../firestore.rules', import.meta.url), 'utf8')

const env = await initializeTestEnvironment({
  projectId: 'rules-test',
  firestore: { rules: RULES, host: '127.0.0.1', port: 8080 },
})

const ME = 'uid-me'
const PEER = 'uid-peer'
const ADMIN = 'uid-admin'
const PHONE = 'a'.repeat(64)

let pass = 0, fail = 0
async function check(name, fn) {
  try { await fn(); console.log(`  PASS  ${name}`); pass++ }
  catch (e) { console.log(`  FAIL  ${name}\n        ${String(e).split('\n')[0]}`); fail++ }
}

const as = (uid) => env.authenticatedContext(uid).firestore()
const anon = () => env.unauthenticatedContext().firestore()

const claim = (uid, deviceId) => ({ uid, deviceId, updatedAt: 1 })

await env.clearFirestore()
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore()
  await setDoc(doc(db, 'admins', ADMIN), {})
  // Two accounts, one handset. The case the whole feature exists to surface.
  await setDoc(doc(db, 'devices', PEER), claim(PEER, PHONE))
})

console.log('\ndevice fingerprints\n')

// ---- the owner ----------------------------------------------------------------------------
await check('owner may claim their own row', () =>
  assertSucceeds(setDoc(doc(as(ME), 'devices', ME), claim(ME, PHONE))))

await check('owner may re-claim after a factory reset', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'devices', ME), { deviceId: 'b'.repeat(64), updatedAt: 2 })))

await check('owner may read their own row', () =>
  assertSucceeds(getDoc(doc(as(ME), 'devices', ME))))

await check('owner cannot smuggle another field in', () =>
  assertFails(setDoc(doc(as(ME), 'devices', ME), { ...claim(ME, PHONE), blocked: false })))

await check('owner cannot claim a row under somebody else uid', () =>
  assertFails(setDoc(doc(as(ME), 'devices', PEER), claim(PEER, PHONE))))

// Pinning `uid` to the document id is what stops a planted fingerprint framing somebody as a
// duplicate of a phone they have never held.
await check('owner cannot write a row whose uid is not theirs', () =>
  assertFails(setDoc(doc(as(ME), 'devices', ME), claim(PEER, PHONE))))

await check('owner cannot delete their own claim', () =>
  assertFails(deleteDoc(doc(as(ME), 'devices', ME))))

// ---- everybody else -----------------------------------------------------------------------
await check('a tester cannot read another tester row', () =>
  assertFails(getDoc(doc(as(PEER), 'devices', ME))))

// The one that matters. An owner has read on their own document, and a careless `allow read`
// would have handed the whole collection to every signed-in account.
await check('a tester cannot enumerate the collection', () =>
  assertFails(getDocs(collection(as(PEER), 'devices'))))

await check('a signed-out visitor cannot read a row', () =>
  assertFails(getDoc(doc(anon(), 'devices', ME))))

await check('a signed-out visitor cannot write a row', () =>
  assertFails(setDoc(doc(anon(), 'devices', ME), claim(ME, PHONE))))

// ---- admins -------------------------------------------------------------------------------
await check('an admin may read one row', () =>
  assertSucceeds(getDoc(doc(as(ADMIN), 'devices', ME))))

await check('an admin may enumerate, which is how duplicates are found', () =>
  assertSucceeds(getDocs(collection(as(ADMIN), 'devices'))))

await check('an admin may clear a claim left by a phone that changed hands', () =>
  assertSucceeds(deleteDoc(doc(as(ADMIN), 'devices', PEER))))

// ---- and the user document is unchanged ----------------------------------------------------
await check('a fingerprint still cannot be written onto the user document', () =>
  assertFails(setDoc(doc(as(ME), 'users', ME), {
    uid: ME, email: 'me@x.com', displayName: 'Me', updatedAt: 1, deviceId: PHONE,
  })))

console.log(`\n${pass} passed, ${fail} failed\n`)
await env.cleanup()
process.exit(fail === 0 ? 0 : 1)
