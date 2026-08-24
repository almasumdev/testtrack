#!/usr/bin/env node
/**
 * Catches the two repos' `Models.kt` silently drifting apart.
 *
 * `test_track` and `test_track_admin` are two separate git repos on purpose — sharing a module
 * would couple two apps that ship on different days to one another's release schedule, for a
 * handful of data classes. The trade is that `TestApp`/`TestGroup` are hand-copied into both, and
 * hand-copied things drift: this session alone found `placedAt` written by the admin app but never
 * read back into its own model, and `TestApp.placed` computed two different ways on either side.
 *
 * This does not catch every kind of drift — a field with the same name but a different meaning
 * would sail through. It catches the specific, common failure: a field one side has and the other
 * has quietly stopped carrying, which is exactly what makes a screen read a value that is always
 * null or a default it never meant.
 *
 * Run from either repo: `node ../test_track/scripts/check-model-drift.mjs` (or from here directly).
 * Exits non-zero on any mismatch, so it can be wired into a workflow step if that's ever wanted —
 * it isn't wired into either repo's CI today, on purpose: doing that well means checking out both
 * repos in the same job, which is a bigger, separate change from the check itself.
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))

const TESTER_MODELS = path.resolve(
  HERE, '../app/src/main/java/com/eazyverse/testtrack/data/Models.kt'
)
const ADMIN_MODELS = path.resolve(
  HERE, '../../test_track_admin/app/src/main/java/com/eazyverse/testtrack_admin/data/Models.kt'
)

/** Every `val`/`var` name inside the constructor of `data class <name>(...)`, paren-balanced. */
function fieldsOf(source, className) {
  const marker = `data class ${className}(`
  const start = source.indexOf(marker)
  if (start === -1) return null

  let depth = 1
  let i = start + marker.length
  const bodyStart = i
  for (; i < source.length && depth > 0; i++) {
    if (source[i] === '(') depth++
    else if (source[i] === ')') depth--
  }
  const body = source.slice(bodyStart, i - 1)

  const fields = new Set()
  for (const m of body.matchAll(/\b(?:val|var)\s+(\w+)\s*:/g)) fields.add(m[1])
  return fields
}

function diff(a, b) {
  const onlyInA = [...a].filter((f) => !b.has(f)).sort()
  const onlyInB = [...b].filter((f) => !a.has(f)).sort()
  return { onlyInA, onlyInB }
}

const testerSource = fs.readFileSync(TESTER_MODELS, 'utf8')
const adminSource = fs.readFileSync(ADMIN_MODELS, 'utf8')

let mismatched = false

for (const className of ['TestApp', 'TestGroup']) {
  const testerFields = fieldsOf(testerSource, className)
  const adminFields = fieldsOf(adminSource, className)

  if (!testerFields) {
    console.log(`  SKIP  ${className}: not found in the tester app's Models.kt`)
    continue
  }
  if (!adminFields) {
    console.log(`  SKIP  ${className}: not found in the admin app's Models.kt`)
    continue
  }

  const { onlyInA, onlyInB } = diff(testerFields, adminFields)
  if (onlyInA.length === 0 && onlyInB.length === 0) {
    console.log(`  MATCH ${className}: ${testerFields.size} fields, both sides agree`)
    continue
  }

  mismatched = true
  console.log(`  DRIFT ${className}:`)
  if (onlyInA.length) console.log(`        tester app has, admin app doesn't: ${onlyInA.join(', ')}`)
  if (onlyInB.length) console.log(`        admin app has, tester app doesn't: ${onlyInB.join(', ')}`)
}

console.log()
if (mismatched) {
  console.log('Drift found. If it is intentional, this script is not the source of truth — the')
  console.log('tester app\'s Models.kt is (see its own comment on that). Otherwise, copy the')
  console.log('missing field(s) across by hand.')
  process.exit(1)
} else {
  console.log('No drift. Both Models.kt files agree on every TestApp/TestGroup field.')
  process.exit(0)
}
