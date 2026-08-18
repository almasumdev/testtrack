/**
 * What an account may write about itself.
 *
 * The `users` document is the one row in the database that its subject edits and that everybody
 * else reads, which is a combination worth a suite of its own. Two things are being defended.
 *
 * `blocked` is an admin's judgement and lives here, so an owner with a free hand over their own
 * document could lift their own ban. The key list is what stops that, and the risk in adding a
 * field to it is that the list is where the ban is actually enforced.
 *
 * `photo` is a base64 avatar on a document that a cohort screen reads thirty of at once. The cap
 * is not about the document limit, which is a hundred times larger and irrelevant; it is about
 * what twelve other people wait for.
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
const ADMIN = 'uid-admin'

// The app aims at 6000 and refuses to go over. The rule allows 8000, so both sides of that are
// worth pinning: a picture the app would produce, and one no client should get away with.
const SMALL = 'a'.repeat(6000)
const HUGE = 'a'.repeat(8001)

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
    await setDoc(doc(db, 'users', ME), {
      uid: ME, email: 'me@x.com', displayName: 'Me', updatedAt: 1,
    })
    await setDoc(doc(db, 'users', PEER), {
      uid: PEER, email: 'peer@x.com', displayName: 'Peer', updatedAt: 1,
    })
  })
}

console.log('\nthe profile an account keeps about itself\n')
await seed()

// ---- the name -------------------------------------------------------------------------------
await check('owner may change their own display name', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'users', ME), { displayName: 'Better Name' })))

await check('owner cannot rename somebody else', () =>
  assertFails(updateDoc(doc(as(ME), 'users', PEER), { displayName: 'Not yours' })))

// ---- the picture ----------------------------------------------------------------------------
await check('owner may set a photo within the cap', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'users', ME), { photo: SMALL })))

await check('owner may clear the photo', () =>
  assertSucceeds(updateDoc(doc(as(ME), 'users', ME), { photo: '' })))

await check('owner cannot set a photo past the cap', () =>
  assertFails(updateDoc(doc(as(ME), 'users', ME), { photo: HUGE })))

await check('owner cannot set a photo that is not a string', () =>
  assertFails(updateDoc(doc(as(ME), 'users', ME), { photo: 42 })))

await check('a fresh account may arrive with a photo already on it', () =>
  assertSucceeds(setDoc(doc(as('uid-fresh'), 'users', 'uid-fresh'), {
    uid: 'uid-fresh', email: 'fresh@x.com', displayName: 'Fresh',
    photo: SMALL, updatedAt: 1,
  })))

await check('a fresh account cannot arrive with an oversized photo', () =>
  assertFails(setDoc(doc(as('uid-big'), 'users', 'uid-big'), {
    uid: 'uid-big', email: 'big@x.com', displayName: 'Big', photo: HUGE, updatedAt: 1,
  })))

await check('owner cannot put a photo on somebody else', () =>
  assertFails(updateDoc(doc(as(ME), 'users', PEER), { photo: SMALL })))

// ---- what the key list is really protecting --------------------------------------------------
// The list gained a field. These are the tests that say it gained only the one.
await check('owner still cannot lift their own block', () =>
  assertFails(updateDoc(doc(as(ME), 'users', ME), { blocked: false })))

await check('owner cannot smuggle a block change in beside a photo', () =>
  assertFails(updateDoc(doc(as(ME), 'users', ME), { photo: SMALL, blocked: false })))

await check('owner cannot invent a new field on themselves', () =>
  assertFails(updateDoc(doc(as(ME), 'users', ME), { role: 'admin' })))

// ---- everybody else --------------------------------------------------------------------------
// Reading is open to any signed-in account on purpose: a grid has to turn a uid into a name, and
// now into a face as well.
await check('a signed-in account can read another profile', () =>
  assertSucceeds(getDoc(doc(as(PEER), 'users', ME))))

await check('an admin may write a profile', () =>
  assertSucceeds(updateDoc(doc(as(ADMIN), 'users', ME), { displayName: 'Set by admin' })))

await check('an admin may still block an account', () =>
  assertSucceeds(updateDoc(doc(as(ADMIN), 'users', ME), { blocked: true, blockedReason: 'spam' })))

console.log(`\n${pass} passed, ${fail} failed\n`)
await env.cleanup()
process.exit(fail === 0 ? 0 : 1)
