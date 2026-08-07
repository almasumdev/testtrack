import fs from 'node:fs'
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing'
import { doc, getDoc, setDoc, updateDoc, writeBatch, runTransaction } from 'firebase/firestore'

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

// Day 3 of the run: days 0, 1 and 2 are finished, so days 1 and 2 are the pair a removal rests on.
const START = Date.now() - 3 * DAY - 3600_000
const PLACED = START - 3600_000

let pass = 0, fail = 0
async function check(name, fn) {
  try { await fn(); console.log(`  PASS  ${name}`); pass++ }
  catch (e) { console.log(`  FAIL  ${name}\n        ${String(e).split('\n')[0]}`); fail++ }
}

async function seed(proofs = []) {
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'groups', GROUP), {
      name: 'Group A',
      memberUids: [ME, TARGET],
      appIds: [MY_APP, TARGET_APP],
      startDate: START,
      status: 'running',
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
      await setDoc(doc(db, 'proofs', `${app}__${uid}__${day}`), {
        appId: app, groupId: GROUP, ownerUid: ME, testerUid: uid, day, usageMs: 40000,
      })
    }
  })
}

// The two halves of an eviction, exactly as Repo.evict writes them.
function evictGroupWrite(db, { byUid = ME, byAppId = MY_APP, target = TARGET, targetApp = TARGET_APP } = {}) {
  return updateDoc(doc(db, 'groups', GROUP), {
    memberUids: [ME, TARGET].filter((u) => u !== target),
    appIds: [MY_APP, TARGET_APP].filter((a) => a !== targetApp),
    lastEviction: { uid: target, appId: targetApp, byUid, byAppId, day: 3, at: Date.now() },
  })
}

function evictAppWrite(db, { byAppId = MY_APP, targetApp = TARGET_APP } = {}) {
  return updateDoc(doc(db, 'apps', targetApp), {
    groupId: '', status: 'pending', placedAt: 0,
    unplacedByAppId: byAppId, unplacedAt: Date.now(),
  })
}

const as = (uid) => env.authenticatedContext(uid).firestore()

console.log('\nTwo missed days, by the owner of the app that went unopened')
await seed([{ app: MY_APP, uid: TARGET, day: 0 }])
await check('group half is allowed', () => assertSucceeds(evictGroupWrite(as(ME))))
await check('app half is allowed', () => assertSucceeds(evictAppWrite(as(ME))))

console.log('\nBoth halves at once, the way Repo.evict actually writes them')
await seed([{ app: MY_APP, uid: TARGET, day: 0 }])
await check('the batch is allowed', () => assertSucceeds((async () => {
  const db = as(ME)
  const batch = writeBatch(db)
  batch.update(doc(db, 'groups', GROUP), {
    memberUids: [ME], appIds: [MY_APP],
    lastEviction: { uid: TARGET, appId: TARGET_APP, byUid: ME, byAppId: MY_APP, day: 3, at: Date.now() },
  })
  batch.update(doc(db, 'apps', TARGET_APP), {
    groupId: '', status: 'pending', placedAt: 0, unplacedByAppId: MY_APP, unplacedAt: Date.now(),
  })
  await batch.commit()
})()))

// A batch is all or nothing, so an unjustified half has to take the justified one down with it.
await seed([{ app: MY_APP, uid: TARGET, day: 2 }])
await check('a batch with one bad half is refused whole', () => assertFails((async () => {
  const db = as(ME)
  const batch = writeBatch(db)
  batch.update(doc(db, 'groups', GROUP), {
    memberUids: [ME], appIds: [MY_APP],
    lastEviction: { uid: TARGET, appId: TARGET_APP, byUid: ME, byAppId: MY_APP, day: 3, at: Date.now() },
  })
  batch.update(doc(db, 'apps', TARGET_APP), {
    groupId: '', status: 'pending', placedAt: 0, unplacedByAppId: MY_APP, unplacedAt: Date.now(),
  })
  await batch.commit()
})()))

console.log('\nThe member actually turned up')
await seed([{ app: MY_APP, uid: TARGET, day: 2 }])
await check('one of the two days covered, group half refused', () => assertFails(evictGroupWrite(as(ME))))
await check('one of the two days covered, app half refused', () => assertFails(evictAppWrite(as(ME))))

await seed([{ app: MY_APP, uid: TARGET, day: 1 }])
await check('the older day covered, group half refused', () => assertFails(evictGroupWrite(as(ME))))

console.log('\nWho is asking')
await seed()
await check('an outsider cannot evict', () => assertFails(evictGroupWrite(as(OUTSIDER))))
await check('the target cannot evict themselves out of trouble', () =>
  assertFails(evictGroupWrite(as(TARGET), { byUid: TARGET, byAppId: MY_APP })))
await check('cannot claim someone else\'s app as the missed one', () =>
  assertFails(evictGroupWrite(as(TARGET), { byUid: TARGET, byAppId: MY_APP, target: ME, targetApp: MY_APP })))

console.log('\nWhat else the opening might let through')
await seed()
await check('cannot rename the group while evicting', () =>
  assertFails(updateDoc(doc(as(ME), 'groups', GROUP), {
    name: 'Hijacked', memberUids: [ME], appIds: [MY_APP],
    lastEviction: { uid: TARGET, appId: TARGET_APP, byUid: ME, byAppId: MY_APP, day: 3, at: Date.now() },
  })))
await check('cannot restart the clock while evicting', () =>
  assertFails(updateDoc(doc(as(ME), 'groups', GROUP), {
    startDate: Date.now(), memberUids: [ME], appIds: [MY_APP],
    lastEviction: { uid: TARGET, appId: TARGET_APP, byUid: ME, byAppId: MY_APP, day: 3, at: Date.now() },
  })))
await check('cannot add a member while evicting', () =>
  assertFails(updateDoc(doc(as(ME), 'groups', GROUP), {
    memberUids: [ME, 'uid-smuggled'], appIds: [MY_APP],
    lastEviction: { uid: TARGET, appId: TARGET_APP, byUid: ME, byAppId: MY_APP, day: 3, at: Date.now() },
  })))
await check('a plain member write with no eviction is still refused', () =>
  assertFails(updateDoc(doc(as(ME), 'groups', GROUP), { name: 'Renamed' })))
await check('the app half cannot place an app', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', TARGET_APP), {
    groupId: 'other-group', status: 'assigned', placedAt: Date.now(), unplacedByAppId: MY_APP,
  })))
await check('the app half cannot change hands', () =>
  assertFails(updateDoc(doc(as(ME), 'apps', TARGET_APP), {
    groupId: '', status: 'pending', placedAt: 0, ownerUid: ME, unplacedByAppId: MY_APP,
  })))

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
  await check('day 1, no two finished days yet, refused', () => assertFails(evictGroupWrite(as(ME))))
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
  await check('a member who joined on day 2 is safe on day 3', () => assertFails(evictGroupWrite(as(ME))))
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
  await check('legacy app with no placedAt is refused', () => assertFails(evictGroupWrite(as(ME))))
}

console.log('\nThe audit record an automatic removal writes for itself')
await seed([{ app: MY_APP, uid: TARGET, day: 0 }])

const removalEvent = (over = {}) => ({
  uid: TARGET, type: 'removed_missed', groupId: GROUP, appId: TARGET_APP,
  byAppId: MY_APP, actorUid: ME, day: 3, createdAt: Date.now(),
  title: 'Your app was removed from its group', body: 'Two days went by.', ...over,
})

await check('the evicting owner may record it', () =>
  assertSucceeds(setDoc(doc(as(ME), 'events', 'ev1'), removalEvent())))

await check('nobody may record a removal for themselves', () =>
  assertFails(setDoc(doc(as(TARGET), 'events', 'ev2'),
    removalEvent({ uid: TARGET, actorUid: TARGET }))))

await check('an outsider may not record one', () =>
  assertFails(setDoc(doc(as(OUTSIDER), 'events', 'ev3'),
    removalEvent({ actorUid: OUTSIDER }))))

await check('no other event type is admitted from a tester', () =>
  assertFails(setDoc(doc(as(ME), 'events', 'ev4'),
    removalEvent({ type: 'assigned' }))))

await seed([{ app: MY_APP, uid: TARGET, day: 2 }])
await check('a removal that did not happen cannot be recorded', () =>
  assertFails(setDoc(doc(as(ME), 'events', 'ev5'), removalEvent())))

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

console.log(`\n${pass} passed, ${fail} failed\n`)
await env.cleanup()
process.exit(fail === 0 ? 0 : 1)
