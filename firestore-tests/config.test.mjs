/**
 * The one document that can lock somebody out.
 *
 * `config/app` carries the update policy every install reads on the way in, and one of its fields
 * is a floor below which the app refuses to work. That makes it the highest-value write in the
 * database from an attacker's point of view and the least interesting read: raise the floor and a
 * whole cohort is walled out of a run they are being held to, lower it and a client keeps itself
 * on a build everyone else has moved past.
 *
 * So the shape being defended is narrow. Any signed-in account may read the one document. Nobody
 * outside the console may write anything, including the account that would benefit most.
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
const ADMIN = 'uid-admin'

let pass = 0, fail = 0
async function check(name, fn) {
  try { await fn(); console.log(`  PASS  ${name}`); pass++ }
  catch (e) { console.log(`  FAIL  ${name}\n        ${String(e).split('\n')[0]}`); fail++ }
}

const as = (uid) => env.authenticatedContext(uid).firestore()
const anon = () => env.unauthenticatedContext().firestore()

async function seed() {
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'admins', ADMIN), {})
    await setDoc(doc(db, 'config', 'app'), {
      latestBuild: 51,
      latestVersionName: '1.4.1',
      minSupportedBuild: 0,
      nudgeAfterDays: 3,
    })
  })
}

console.log('\nthe update policy\n')
await seed()

// ---- reading ---------------------------------------------------------------------------------
await check('a signed-in install can read the policy', () =>
  assertSucceeds(getDoc(doc(as(ME), 'config', 'app'))))

// Not a security boundary so much as a statement of when the gate applies. An install that has
// not signed in has no cohort to be out of step with, and a missing answer already means "block
// nobody", so refusing here costs nothing.
await check('an install that has not signed in cannot', () =>
  assertFails(getDoc(doc(anon(), 'config', 'app'))))

// One document, nothing to enumerate. Listing is closed so the collection cannot become a place
// things are discovered.
await check('nobody can list the collection', () =>
  assertFails(getDocs(collection(as(ME), 'config'))))

// ---- writing ---------------------------------------------------------------------------------
await check('a tester cannot raise the floor', () =>
  assertFails(updateDoc(doc(as(ME), 'config', 'app'), { minSupportedBuild: 99 })))

await check('a tester cannot lower the floor to keep an old build alive', () =>
  assertFails(updateDoc(doc(as(ME), 'config', 'app'), { minSupportedBuild: 0 })))

await check('a tester cannot claim a newer release exists', () =>
  assertFails(updateDoc(doc(as(ME), 'config', 'app'), { latestVersionName: '9.0.0' })))

await check('a tester cannot delete the policy', () =>
  assertFails(deleteDoc(doc(as(ME), 'config', 'app'))))

await check('a tester cannot write a second config document', () =>
  assertFails(setDoc(doc(as(ME), 'config', 'other'), { minSupportedBuild: 99 })))

// The console writes with an admin credential, not through these rules, so even an admin uid is
// refused here. Nothing in either app has any business changing this document.
await check('not even an admin account may write it from a client', () =>
  assertFails(updateDoc(doc(as(ADMIN), 'config', 'app'), { latestBuild: 52 })))

console.log(`\n${pass} passed, ${fail} failed\n`)
await env.cleanup()
process.exit(fail === 0 ? 0 : 1)
