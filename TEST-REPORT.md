# Test report, 2026-09-04: every user path, automated

Operator's order: "do automated testing for each and every thing that a
user may do or see. document any issues. resolve any bugs. optimize
performance." This is the record of that arc. The driver is
`tools/screens/everything_walk.py`; the merged results of its runs live in
`tools/screens/out/everything/REPORT.md` (one row per step, latest run
wins) with a screenshot per step beside it.

## 1. Method

- Device: the API 26 low-end AVD (720 x 1520, 320 dpi, 2 cores, 2 GB,
  software rendering), one emulator at a time, restarted before the
  performance numbers were read (section 5).
- Build: the debug APK for behaviour (the fake engine's intent extras
  force reads and states; a release build ignores every `fake_` extra by
  design), the release APK for cold start and frame times.
- Driver: uiautomator dumps by text and description, a screenshot per
  step, a crash and ANR watch on logcat after every step (the app's pid
  only), an unlabeled-control and 48 dp tap-target scan on every dump,
  `dumpsys gfxinfo` frame statistics per phase and `meminfo` at the phase
  boundaries.
- Phases: A camera controls, B the fifty forced result-sheet variants
  and their actions, C batch, D history, E the ten editors and the card,
  F every Settings row and dialog, G about and terms, H support, I share,
  J the six thank-you moments, K the intents (`app.scanner.action.SCAN`,
  `CARD`, a still image through the core by the debug path and by
  `ACTION_SEND`, the zxing scan request, the two widgets, the tile), L the
  camera-permission screen through the system dialog, M process death,
  N large text (font scale 1.3), T the dark theme on eleven screens, Q a
  Spanish round trip, O a 1,500-event monkey, P cold start, frame
  statistics and memory; plus the existing walks for the vehicle order,
  the identity actions and the ten-language sweep.
- Unit tests: 125 of 125 (JVM), including the three new pins below.

## 2. Results

| Group | Steps | Outcome |
|---|---|---|
| Walk steps, merged (latest run of each step) | 185 | 178 pass, 7 informational, 0 fail, 0 crash |
| Full walk of every phase on the final build, cleared data, one emulator session (22:16 to 23:19) | 185 | 176 pass, 6 informational, 3 fail (D05 to D07, one driver defect, see below), 0 crash; the retry after the driver fix: 8 of 8 history steps pass (23:44, the same session) |
| Monkey, 1,500 events | 1 | no crash, no ANR |
| Language sweep, debug build, 10 languages x 3 screens | 30 | clean (43 to 44 strings per language, no English leaks) |
| Identity actions walk | 6 checks | 6 of 6, the clipboard holds the bare number |
| Vehicle walk, 2015 Civic and the sample Accord, 10 timed runs each | 2 x 4 checks | fan-out P50 538 ms, P95 741 ms (Civic); P50 520 ms, P95 1,035 ms (Accord); one session's sample (an earlier nine-run leg read P95 3,785 ms), the flaky reruns listed in O3 beside it |

The merged count is a union across the runs of the evening (three builds,
two emulator sessions): every step passed on its latest run, and the
earlier failures are split between the driver defects listed in section 4
and the app defects I1 to I11. The full walk on the final build is one
build's result: 176 of 185 steps passed in one session from cleared data,
six were informational, and the three failures share one driver cause
(the long-press step picked the seeded batch row, whose press opens the
batch dialog by design, and the open dialog blocked the two steps after
it); the driver now picks a plain row and clears a stray dialog, and the
retry is in the table. The seven informational rows are by design or by device: no zoom control
on the emulator, no Quick Settings tile row below Android 13, no slider
node exposed by the Support screen's Compose slider, a two-code frame
that is scripted, the batch row already deleted by an earlier step, the
tile shade on API 26, and a custom-scheme code (section 4).

## 3. Performance

Release build, restarted emulator, frame times from `dumpsys gfxinfo`
(the low-end AVD renders in software; phones are faster in absolute terms).
Only one row is a like-for-like before/after: the Settings scroll, measured
on the debug build both times. The other rows are the release build's
numbers as they stand; their debug counterparts moved with the emulator's
state as much as with the code, so they are not claimed as deltas.

| Measure | Release build, after the arc | Note |
|---|---|---|
| Cold start to the first frame, median of 5 | 288 ms | the debug build 475 to 594 ms across fresh guests |
| Dock tour Scan / History / Create after a warm-up tour | 10 percent janky, p90 17 ms, p99 32 ms | the debug build with the same warm-up reads 10.1 percent |
| Eight sheets opening and closing | 5.7 percent janky, p90 10 ms | a fresh debug guest read 2.8 percent; the emulator's state dominates this row |
| Settings scroll, four flings each way, debug build before and after | 24.1 to 1.7 percent janky; p90 34 to 6 ms; p99 81 to 27 ms | the LazyColumn change; the release build reads 1.9 percent |
| Create editors while typing | re-encode settles 120 ms after the last keystroke; the last image stays up | 9 to 13 percent janky before on the debug build; the after number is owed by the next full walk |
| Memory across sixty sheets | +18 MB PSS after thirty sheets, +5 MB after thirty more | a plateau, not a leak |
| History fling at 9,000 rows | 2 percent janky, p90 9 ms | unchanged |

The full walk's own performance phase ran last, after 63 minutes of guest
uptime, and read cold start 556 ms (debug), dock tour 28 percent janky,
eight sheets 32 percent, settings scroll 11 percent, history fling 0
percent: the degradation of section 5 in numbers, not a build-to-build
change, and not comparable with the rows above (a restarted guest). The
memory plateau held: +12.7 MB after thirty sheets, +12.3 MB after thirty
more.

## 4. Issues found and their dispositions

Fixed in this arc (each with its pin or its walk step):

| # | Severity | Where a person meets it | Defect | Fix and pin |
|---|---|---|---|---|
| I1 | P1, integrity | any release build | the walk hooks `fake_full_history` and `fake_supporter` were read without a debug guard; the launcher activity is exported, so any app could clear History or set the supporter tier with an intent | every `fake_` extra guarded by `BuildConfig.DEBUG`; `DebugExtrasTest` walks the activity's source |
| I2 | P1, feature invisible | every thank-you moment | the snackbar effect was keyed on the pending moment and cleared it inside itself, restarting the effect and cancelling the snackbar it had just shown: no note ever stayed up | one collector for the root's life; walk phase J shows all six with their Support action |
| I3 | P1, wrong value | driver license sheet, a row tap | the row copy stripped on ": " but the English rows carry no colon: the clipboard held "Number D1234567" | `ParsedContent.rawValues`; `AamvaTest.rowCopyValuesAreBare`; the identity walk reads the clip (`D1234567`) |
| I4 | P2, policy | sharing the app in a private session | the SHARED moment ignored the private session | `AppRoot` passes the flag; `ReciprocityPolicyTest.aPrivateSessionBlocksEveryClass` |
| I5 | P2, performance | Create editors, the card | a 768 px code re-encoded on every keystroke with the preview blanked each time; the card re-encoded on every visit to Create | 120 ms settle, the last image kept, a three-entry render cache |
| I6 | P2, performance | Settings | a Column drew every row on every scroll frame | `LazyColumn`, one item per row |
| I7 | P3, display | long headlines | cut at two lines with no ellipsis | `TextOverflow.Ellipsis` |
| I8 | P3, display | vehicle sheet | "2013 complaints" printed without grouping, reading as a year | locale grouping; the string takes text in eleven languages |
| I9 | P3, display | GS1 pack sheet | expiry and best-before in ISO form | locale dates (a day-less month stays as written) |
| I10 | P3, noise | payment sheets | the amount twice | subtitle only |
| I11 | P3, copy | vehicle sheet hero | "Look up vehicle" sent the VIN to a web search without saying so | "Search this VIN" in eleven languages |
| I12 | P2, display | "What these checks mean" on a website sheet | the dialog's paragraph does not scroll; with online lookups on it outgrows the 720 x 1520 screen and its tail (the closing sentence and the "Also noted" notes) is cut off and unreachable | the dialog's text scrolls like the recalls dialog's; walk step B2-32 flings it to the end and keeps the shot |

Open, for the operator:

| # | Where | Observation | Options |
|---|---|---|---|
| O1 | custom-scheme codes (`myapp://open?item=42`) | the sheet is "Text" with a "Copy text" hero; History names it "Opens another app (myapp)"; there is no Open action even when an app handles the scheme | keep as is, or an "Open in app" hero behind a caution row, resolved against the installed handlers |
| O2 | the vehicle hero | still a web search of the VIN (now labelled so) | deferred as a design question by the Android Developer |
| O3 | the walks' relaunched emulator | the first lookup after an AVD relaunch carries no fan-out line (DNS warm-up); the vehicle walk now does a warm-up read | none |

Driver defects found and fixed on the way (not app defects): the
private-session line lives inside the symbology line; verdict rows are
found by their accessibility text; Compose buttons are clickable views
with a text child; a forced read persists until cleared; the two-code
frame needs sheets dismissed between frames; clean non-link sheets carry
no verdict row by design; the nearly-full-history step empties the list
for the next run (the phase seeds rows); Settings rows above the scroll
position need a top-first scroll; the manual shutter is restored by
state; a location code needs both coordinates; the run must launch the
app after clearing its data; pushed screens hide the dock; a still image
through the fake engine returns the script, not the picture; thank-you
notes stay up ten seconds, so each class takes its action before the
next; skipped-frame warnings must be read from the app's pid only; the
long-press selection step must pick a plain row (a batch row opens its
dialog on a long press, by design) and a stray dialog must be closed
before the swipe and menu steps.

## 5. Machine note

A two-hour emulator session degraded on its own: on the same build and
path the dock tour went from 2 percent janky to 88 percent and cold start
from 540 to 755 ms, with the guest's camera renderer at three quarters of
a core, while the operator watched the window lag. A cold restart of the
AVD brought the numbers back. Standing: one emulator, no build beside a
walk, a restart between long arcs, and no performance number read from a
guest that has been up more than about an hour.

## 6. What is not covered here

The decoder's read rates and latency are the validation matrix's
(`SCAN-VALIDATION.md`); this arc covered the screens, states and actions
around it. The Android 13 clipboard preview on the identity row copy is
still unevidenced on a phone. The tablet tap-target review of the changed
action rows (2026-09-04, the queued order): the Material buttons in the
vehicle, identity and recalls rows carry the 48 dp minimum touch target
themselves; the one bare clickable row, the recalls line, sat at 40 dp and
is 48 dp now; the phone layout is the tablet layout (portrait only, one
dock), so no tablet run was made on the one emulator.
