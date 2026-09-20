# App rules

The rules the Android app is built under. The backend has had rules since before its first line
and they held; the app predates that decision and never got them, so this is the app catching up.

Every rule here is enforced by `scripts/check_app.py` in CI, or it is written as a note and not as
a rule. A rule that depends on somebody remembering is not a rule.

## Layers

```
ui/            Compose. Draws what it is given.
  theme/       colour, type, the one accent
viewmodel/     state and intent. Calls domain and data, holds no Context.
domain/        pure rules: mapping, derived values, arithmetic. No Android imports.
data/          the only place I/O happens: network, device, storage, Firebase
net/           wire types and the API client
```

| Layer | May import | Must never import |
| --- | --- | --- |
| `ui/` | viewmodel, domain, theme | `data`, `net` |
| `viewmodel/` | domain, data, net | Compose |
| `domain/` | nothing of ours | `android.*`, `data`, `net`, Compose |
| `data/` | net, domain | ui, viewmodel |

The one that earns its keep is **`domain/` importing no `android.*`**. That is what makes a rule
testable in milliseconds on a desk rather than in two minutes on a handset, and the difference
decides whether the rules get tested at all. `ui/CrewWire.kt` and `ReviewStep` are already this
shape and already have the fastest, densest tests in the app.

The second is **`ui/` never importing `data` or `net`**. A screen that can reach the network is a
screen that cannot be put in front of a test without one.

## Files

**500 lines maximum**, the same figure the backend uses. Eight files exceed it today; see the
baseline below.

Compose files run long honestly — a screen is a lot of layout — so the limit is on the file and
the answer is usually another file, not shorter code. `CrewRows.kt`, `CrewTabs.kt` and
`CrewPhases.kt` are one screen split three ways and that is the intended shape.

## Screens

**Every screen has a stateless body.** `ReviewScreen` holds the view model and the lifecycle;
`ReviewBody` takes plain values and draws them. The screen is the wiring, the body is the drawing.

This is not tidiness. It is the only reason any screen in this app has a UI test: a body that
takes values can be put on a device in every state it has, including the ones that are hard to
reach — a crew mid-fortnight, a rejected seat, an account that has done nothing. Before the split
the review stepper shipped with every step showing as already done, because nothing could open it.

## Tests

Three kinds, and each catches what the others cannot:

| Kind | Runs | Catches |
| --- | --- | --- |
| `app/src/test/` | desk, milliseconds | what the app computes |
| `app/src/androidTest/…/ui/` | device | what is drawn |
| `app/src/androidTest/…/net/` | device + live API | the server and the app disagreeing |

The third needs a signed-in session, which a test cannot get from Google, so it is seeded out of
band by `scripts/e2e.sh`. Without the seed those tests skip rather than fail: a red suite that
only means "you did not seed" teaches people to ignore red suites.

**CI runs the first two on every push.** It did not until now, which meant 154 tests that only ran
when somebody remembered.

## The baseline

The app has violations today. They are listed in `scripts/app-baseline.txt` and the checker fails
only on anything *new*, so the rules start working immediately instead of after a rewrite.

The baseline may only shrink. Removing a line from it is the unit of this work, and the file is
the queue: it says exactly what is left and nothing else has to be remembered.

## Notes, not rules

Things worth doing that are not mechanically checkable, so they are written here honestly rather
than pretended to be enforced.

- A view model decides nothing the server decides. Which crew a seat goes into, whether a verdict
  counts, whose day it is: the phone asks and redraws the answer.
- Copy avoids em dashes, arrows and icons, and says what the product does for the reader.
- A number that both halves need lives in `core/rules.py` on the server and is mirrored once in
  the app, with a test that they agree.
