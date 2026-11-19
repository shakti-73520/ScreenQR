# Scanning validation matrix

Operator, 2026-09-04: "a thorough evaluation/validation of all the
scanning functionalities, with the latency ... diverse enough to try each
unique thing, and to try variations of the unique thing at least 3 of
them." This file is the matrix, the procedure, and the dated results.
Rerun it after any change to the decode path, the parser, the scan modes,
or the core.

## What is validated

The product route as it stands: the app's scan screen over the Rust core
(`core/verdetto-core`, zxing-cpp v3.1.1 through its C API), on the
constrained emulator (`lowend_api26`: x86_64, API 26, two cores, 2 GB,
720x1520). The reference reader (the Python pipelines with the own plugins, the
precision gate and the R8 verifier) has its own suites in its repository (948 tests, the 900-symbol Zint band, the public
corpora); this matrix does not repeat them.

| Group | Unique thing | Variations | Count |
|---|---|---|---|
| symbology | every format the Zint-backed writer creates and the engine reads | linear: nominal, small (55 percent of the poster), rotated 90, tilted 10; matrix: nominal, small (40 percent), rotated 45, inverted | 31 formats, 124 cases |
| content | every content kind the parser recognises, in the symbology the kind arrives in | three payloads each (website in four verdict classes, script schemes, Wi-Fi, contact, phone, email, SMS, location, calendar, sign-in code, payment, crypto address, custom scheme, text, boarding pass in PDF417, Aztec and QR, ID card, product EAN-13 and UPC-A, book ISBN, VIN in Code 39, GS1 element strings in Data Matrix and GS1-128, HIBC, ISBT 128, Code 32 medicine, DX film edge) | 30 kinds, 87 cases |
| mode | batch, manual shutter, several codes in view, cooldown, still image | batch (three codes; the same code three times with a blank between; two codes then Clear), manual shutter on three posters, three multi-code posters (QR+EAN-13, two QRs, three symbols), cooldown on three codes, stills at 720x1520, 4000x3000 JPEG, 300x300 and 1600x1200 | 11 cases |

Safety verdict classes travel inside the content group: no warnings
(three clean sites), caution (short link, unusual port, similar name),
danger (imitation, digit squat, brand misuse), other danger (nested link,
hex IP, app download), script schemes.

## Procedure

`tools/screens/matrix_cases.py <out>` renders every case as a 1024 px
poster (symbol at integer module scale on white, quiet zones kept) and
the stills, and checks each poster against the Python zxing-cpp reader
(`reference_read`); a poster the reference cannot read is kept and marked,
so an engine limit is told apart from a phone-path miss.

`tools/screens/matrix.py <out>/cases.json <out> [--group] [--only]
[--repeat N] [--keep-session]` drives one emulator: per poster case it
starts a fresh engine session when the previous case read (the core
reports a sighting under a key it verified earlier on every gated frame
that shows another symbol in the same place, and the app promotes it to a
verified read, so a key would ride into the next case; `--keep-session`
keeps the continuous session), blanks the virtual scene's wall and proves
it blank on the display (a fresh copy of the blank each time: a path or
bytes the table slot also holds is served from the emulator's cache and
shows nothing new, 5 of 30 sends on 2026-09-06), waits for the engine to
log two frames captured after the blank with no read, pins the poster on
the wall through the emulator console (`virtualscene-image wall`, a staged
copy named by the case and the bytes' SHA-1), proves it shown (the wall
region of the emulator's own screenshot carries a symbol that is not the
previous case's image nor any earlier one's, then holds still; a wall that
does not change within 3 s gets a fresh copy, twice; a wall that never
changes is a "poster not shown" failure row, no timer started), writes the
marker into the device log (`log -t VerdettoMatrix`) at the poster's first
sighting, and reads the core's per-frame lines from the debug build
through `tools/screens/engine_line.py`
(`VerdettoCore: frame WxH rot R wall X ms reads N stages {...} seq S
parts M ... gate ... lane cheap|full [over ceiling] [rival held N] first
[symbology] text`, the lanes' marks between the gate and the marker since
the two decode lanes of 2026-09-06, with the own routes' attempt lists
on `attempts seq S part i/M` continuation lines since 2026-09-06, none
over 3,800 bytes, so logcat's 4,068-byte payload never cuts the marker
off a busy frame; the single-line shape before that day is read too).
Per case it records:

- read, correct: the first read frame after the poster's sighting (the
  marker's stamp less marker_lag_s, the adb latency between the sighting's
  capture and the marker; never a frame from before the blank was proven);
  correct when the text matches the payload, the writer's text, or the
  reference reader's text (normalised; a text the log cut at 80
  characters is compared as a prefix)
- stale_poster: the sanity check of the run; an earlier case's text read
  within a second of the marker, flagged in the note with the wall's
  proof (never a wrong read of the plugin; the summary counts it apart)
- ttfr_s: the device-clock time from the poster's first sighting on the
  display to that frame's log line, which includes the frame's own
  analysis but no longer the scene's texture swap (runs before 2026-09-07
  wrote the marker before the poster command, so their ttfr carried the
  swap, 0.2 to 0.7 s, and the frame in flight before it)
- frames_to_first: frames logged between the marker and the first read
- wall: the gate's record per case; blank_ok and blank_ms (the wall
  proven blank, and how long that took), quiet and quiet_ms (the engine's
  two empty frames after it), show_ms (command to first sighting),
  settle_ms (command to proof), retries (fresh copies sent again),
  captures, the blank's and the poster's view hashes, std_poster and
  zdist_prev (the view's standard deviation and its standardized distance
  to the previous poster's view), sheet_during_settle, and table_resets
  and table_code (the emulator gave the table slot the wall's texture, so
  two codes were in view; the table is set again at the sighting);
  wall_settle_ms and wall_retries repeat the two headline numbers
- first_wall_ms, stats: the JNI wall and stage split of the read frames
  in the window (p50, p95), and the no-read frames' wall beside them
- sheet_open, sheet_label: the result sheet dumped through uiautomator,
  its kind label against the parser's expected content type
- one screenshot per feature (its first variant) and every failure

Modes: batch enters through the More menu, shows the posters in turn (a
blank poster between repeats so the same code counts again), reads the
panel's "N unique" line, taps Clear or Done and reads the notice; manual
shutter toggles through the More menu, expects no sheet while the code is
in view, taps the shutter and expects the sheet; several codes expects no
sheet, finds the amber outlines in a screenshot and taps the first;
cooldown dismisses the sheet, expects it to stay closed for the four
seconds the session holds, and records when it reopens; stills go through
the `fake_image` debug extra to the still-image path and read the
"still image ... hits N" line and the still's own frame line.

`tools/screens/matrix_report.py <out>` rebuilds the per-feature table and
`summary.json` from `results.jsonl` (the latest row per case wins).

On the phone (`tools/screens/phone_matrix.py`, a stand and the poster
window) the same judgment runs with the protocol of 2026-09-07: the leg is
gated on the built APK's md5 against the phone's, waits for a cool phone
and records the thermal status at its start and end, can rerun its first N
cases for a noise band, judges the pair poster (a QR above a Code 128 since
the cut of 2026-09-07; a Code 16K before it) as
two expected texts, and carries the lane counts and the rival-held count
per row; a wrong read is told from a held sighting throughout, and the
compare (`phone_lanes_compare.py`) never calls a delta inside the band a
change. Since lane 11 (2026-09-07) the leg also force-stops the app for
its cool wait, stops on a first window under the camera floor ("camera
not delivering", exit 4) and carries the cheap lane's last completion and
longest silence per window; since lane 12a (the same day) it reads the
skin sensor from the dump's HAL section, answers the online-lookups
question after the app's first start and keeps the buffer from that
start to the first case; since the frame-area band (the same day,
perf/frame-area) `--start-extras` puts a debug extra on every start of
the leg, and `fake_frame_area <int>` sets the analysis frame's area bound
for that process (the release bound stays 1,300,000), so one build serves
Also, with `--calibration <card>` (the camera probes,
2026-09-07) it shows a calibration card three times before the first case
and writes the rig's span, analysis px per poster px from the app's own
read of the card's QR, into the header. Since the best-frame seam
(2026-09-07, feat/best-frame) a tail's frame line says whether the tail
read the kept sharpest frame of the last 0.3 s or the latest (`tail frame
best|latest ratio R age A ms`), each row counts the takes and states the
ratio's distribution, and `--start-extras "--es fake_best_frame off"`
runs the A/B's off arm. `tools/screens/README.md` names
the pieces. Lane 12's scoreboard (the same day, `phone_scoreboard.py`) reads a
leg into the per-family table of losses, misreads, p95 and max with a
mark, the delta against a before leg beside the noise band, and the
keep-or-remove reading of the shipped families; `outline_conformance.py`
sums the leg's scan shots per family (IoU, offset, mirrored, whole-frame,
no-outline, ghost, and an edge reading for an object at the view's edge
taken for the path); both are pinned on fixtures.

Evidence lives beside the screens of the day in the reference reader's
repository, under its app-design evidence folder for 2026-09-04
(`matrix/`: cases.json, posters, stills, results.jsonl, summary.json,
matrix.log, shots).

## Results, 2026-09-04

Three full runs on the `lowend_api26` AVD (x86_64, API 26, two cores;
`emulator-5554`, 720x1520 in the logs), debug build, the emulator's virtual-scene camera with the poster on its
wall. The first full run was invalid on driver defects (recorded in the
UI-pass skill; its symbology and content passes sit at the top of
`baseline-640/matrix.log`, the rows file holds the valid pass); the
tables below are the baseline at the app's default
640x480 analysis frame against the final build: the shared frame-size
rule (1280x720 on this emulator, 1280x960 on a phone that offers it)
and the confirmation gate (a read acts when this frame and one of the
two before it carry the same text). A run in between, with the gate
demanding two consecutive frames, read the same cases and is kept as
`after-1280-consecutive/`.

Reading the columns. "Read" is the engine's first read frame after the
marker; "Correct" its text against the payload (or the reference
reader's text for Code 32, whose reader returns the AIC digits);
"Sheet" whether the result sheet opened within three seconds of it;
"Label" whether the sheet's kind matched the parser's expected type
(the matrix's expectation, not the app's; the ISBT 128 identifier opens
a Product sheet by design, which the matrix now expects, so the two
label misses in the final tables are that earlier expectation and the
MATMSG payload). TTFR is
the device-clock time from the marker to the first read frame's log
line, so it includes the scene's texture swap of roughly 150 ms; it is
the engine's first read, and in the final build the sheet follows at
least one frame later, once the gate has its second sighting. Wall is
the app's per-frame time around the core call (`NativeEngine`, the
`wall` field of the debug log line) at p50 over the read frames within
the case, then the median over cases; engine is the zxing-cpp stage
alone. "Wrong reads" counts a wrong first read frame, whether or not
anything acted on it; whether a sheet opened, and on what, is in the
rows' sheet texts. Every variant of every feature is
listed by name under Variants.

### Symbologies and content kinds

| Group | Feature | n | Read 640 | Read 1280 | Correct 640 | Correct 1280 | Sheet | Label | TTFR med (s) | Wall p50 (ms) 640 | 1280 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| symbology | Code39 | 4 | 0 | 3 | 0 | 3 | 3 | - | 0.52 | - | 95.1 |
| symbology | Code93 | 4 | 3 | 4 | 3 | 4 | 4 | - | 0.433 | 37.2 | 80.05 |
| symbology | Code128 | 4 | 1 | 4 | 1 | 4 | 4 | - | 0.391 | 36.5 | 71.65 |
| symbology | Codabar | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.494 | 38.6 | 77.2 |
| symbology | ITF | 4 | 3 | 4 | 3 | 4 | 4 | - | 0.49 | 37.7 | 77.85 |
| symbology | ITF14 | 4 | 3 | 4 | 3 | 4 | 4 | - | 0.431 | 39.4 | 74.85 |
| symbology | EAN13 | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.475 | 38.9 | 74.85 |
| symbology | EAN8 | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.406 | 40.75 | 71.9 |
| symbology | UPCA | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.49 | 39.2 | 81.7 |
| symbology | UPCE | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.512 | 39.8 | 84.1 |
| symbology | ISBN | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.446 | 37.1 | 81.55 |
| symbology | Telepen | 4 | 0 | 3 | 0 | 3 | 3 | - | 0.481 | - | 57.7 |
| symbology | PZN | 4 | 0 | 3 | 0 | 3 | 3 | - | 0.496 | - | 101.2 |
| symbology | Code32 | 4 | 3 | 4 | 3 | 4 | 4 | - | 0.399 | 38.8 | 87.55 |
| symbology | DataBar | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.474 | 46.4 | 95.85 |
| symbology | DataBarLtd | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.391 | 42.65 | 71.5 |
| symbology | DataBarStk | 4 | 3 | 3 | 3 | 3 | 3 | - | 0.52 | 41.1 | 75.3 |
| symbology | DataBarStkOmni | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.494 | 44.6 | 80.55 |
| symbology | DataBarExp | 4 | 1 | 3 | 1 | 3 | 3 | - | 0.489 | 55.5 | 91.7 |
| symbology | DataBarExpStk | 4 | 4 | 4 | 3 | 4 | 4 | - | 0.444 | 49.4 | 81.55 |
| symbology | DXFilmEdge | 4 | 0 | 0 | 0 | 0 | 0 | - | - | - | - |
| symbology | QRCode | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.517 | 42.75 | 80.3 |
| symbology | MicroQRCode | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.431 | 44.85 | 74.0 |
| symbology | RMQRCode | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.427 | 47.8 | 78.3 |
| symbology | Aztec | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.424 | 46.75 | 79.95 |
| symbology | AztecRune | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.454 | 45.15 | 79.35 |
| symbology | DataMatrix | 4 | 4 | 4 | 4 | 4 | 4 | - | 0.487 | 41.4 | 79.3 |
| symbology | PDF417 | 4 | 1 | 2 | 1 | 2 | 2 | - | 0.452 | 46.5 | 64.25 |
| symbology | CompactPDF417 | 4 | 2 | 2 | 2 | 2 | 2 | - | 0.508 | 47.7 | 84.7 |
| symbology | MicroPDF417 | 4 | 1 | 1 | 1 | 1 | 1 | - | 0.373 | 45.4 | 77.7 |
| symbology | MaxiCode | 4 | 0 | 0 | 0 | 0 | 0 | - | - | - | - |
| content | website | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.45 | 44.0 | 112.8 |
| content | website-caution | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.435 | 46.4 | 116.2 |
| content | website-danger | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.437 | 48.6 | 75.8 |
| content | website-other-danger | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.46 | 48.2 | 84.1 |
| content | script-scheme | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.346 | 47.7 | 79.6 |
| content | wifi | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.422 | 44.7 | 70.9 |
| content | contact | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.534 | 49.0 | 72.5 |
| content | phone | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.413 | 46.7 | 74.0 |
| content | email | 3 | 3 | 3 | 3 | 3 | 3 | 2 | 0.456 | 45.8 | 69.3 |
| content | sms | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.509 | 46.5 | 83.8 |
| content | location | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.426 | 45.3 | 81.7 |
| content | calendar | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.423 | 46.9 | 74.0 |
| content | otp | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.484 | 47.0 | 87.4 |
| content | payment | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.464 | 47.3 | 79.4 |
| content | crypto-address | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.437 | 45.7 | 83.5 |
| content | custom-scheme | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.485 | 46.4 | 77.0 |
| content | text | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.452 | 47.8 | 82.0 |
| content | boarding-pass | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 0.503 | 43.0 | 103.3 |
| content | boarding-pass-aztec | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 0.469 | 54.5 | 78.3 |
| content | boarding-pass-qr | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 0.451 | 50.4 | 77.9 |
| content | id-card | 3 | 1 | 3 | 0 | 3 | 3 | 3 | 0.452 | 61.0 | 63.3 |
| content | product-ean13 | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.392 | 48.5 | 82.5 |
| content | product-upca | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.402 | 44.0 | 78.2 |
| content | book-isbn | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.483 | 40.3 | 82.5 |
| content | vehicle-vin | 3 | 0 | 0 | 0 | 0 | 0 | 0 | - | - | - |
| content | gs1-element-string | 3 | 2 | 2 | 2 | 2 | 2 | 2 | 0.38 | 52.2 | 78.75 |
| content | gs1-128 | 3 | 0 | 3 | 0 | 2 | 3 | 3 | 0.646 | - | 95.5 |
| content | hibc | 3 | 0 | 2 | 0 | 2 | 1 | 1 | 1.414 | - | 76.3 |
| content | isbt-128 | 3 | 2 | 3 | 2 | 3 | 3 | 2 | 0.426 | 43.65 | 78.7 |
| content | medicine-code32 | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.463 | 54.8 | 95.4 |
| content | dx-film | 2 | 0 | 0 | 0 | 0 | 0 | 0 | - | - | - |

### Modes

| Feature | n | ok | Variants |
|---|---|---|---|
| several-codes | 3 | 2 / 3 | qr+ean13: fail, two-qr: ok, three: ok |
| batch | 3 | 3 / 3 | batch: ok, batch-repeat: ok, batch-clear: ok |
| manual | 1 | 1 / 1 | manual: ok |
| cooldown | 1 | 1 / 1 | cooldown: ok |
| still | 58 | 48 / 21 | ean13-720: ok, qr-4000: ok, pdf417-300: ok, dm-1600: ok, symbology-Code39-nominal: ok, symbology-Code39-small: ok, symbology-Code39-rot90: ok, symbology-Code39-tilt10: ok, symbology-Code93-small: ok, symbology-Code128-small: ok, symbology-Code128-rot90: ok, symbology-Code128-tilt10: ok, symbology-ITF-small: ok, symbology-ITF14-small: ok, symbology-Telepen-nominal: ok, symbology-Telepen-small: ok, symbology-Telepen-rot90: ok, symbology-Telepen-tilt10: ok, symbology-PZN-nominal: ok, symbology-PZN-small: ok, symbology-PZN-rot90: ok, symbology-PZN-tilt10: ok, symbology-Code32-small: ok, symbology-DataBarStk-tilt10: fail, symbology-DataBarExp-nominal: ok, symbology-DataBarExp-small: ok, symbology-DataBarExp-rot90: ok, symbology-DXFilmEdge-nominal: ok, symbology-DXFilmEdge-small: ok, symbology-DXFilmEdge-rot90: ok, symbology-DXFilmEdge-tilt10: fail, symbology-PDF417-small: ok, symbology-PDF417-rot45: fail, symbology-PDF417-inverted: fail, symbology-CompactPDF417-rot45: fail, symbology-CompactPDF417-inverted: fail, symbology-MicroPDF417-small: ok, symbology-MicroPDF417-rot45: fail, symbology-MicroPDF417-inverted: fail, symbology-MaxiCode-nominal: ok, symbology-MaxiCode-small: ok, symbology-MaxiCode-rot45: fail, symbology-MaxiCode-inverted: fail, content-id-card-v1: ok, content-id-card-v3: ok, content-vehicle-vin-v1: ok, content-vehicle-vin-v2: ok, content-vehicle-vin-v3: ok, content-gs1-element-string-v3: ok, content-gs1-128-v1: ok, content-gs1-128-v2: ok, content-gs1-128-v3: ok, content-hibc-v1: ok, content-hibc-v2: ok, content-hibc-v3: ok, content-isbt-128-v1: ok, content-dx-film-v1: ok, content-dx-film-v2: ok |

### Groups

| Group | Cases | Read | Correct | Wrong reads | Sheet | Label | TTFR med (s) | TTFR p95 (s) | TTFR max (s) | Read-frame wall p50 (ms), median over cases | p95 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| symbology (640x480) | 124 | 85 | 84 | 1 | 85 | 124 | 0.221 | 0.9 | 2.66 | 41.35 | 51.0 |
| content (640x480) | 86 | 71 | 70 | 1 | 71 | 69 | 0.221 | 0.3 | 2.201 | 46.8 | 54.6 |
| mode (640x480) | 66 | - | - | - | - | - | - | - | - | 55 ok | |
| symbology (1280x720) | 124 | 104 | 104 | 0 | 104 | 124 | 0.454 | 0.6 | 0.684 | 78.7 | 109.1 |
| content (1280x720) | 86 | 79 | 78 | 1 | 78 | 76 | 0.453 | 0.6 | 2.339 | 79.1 | 113.9 |
| mode (1280x720) | 39 | - | - | - | - | - | - | - | - | 29 ok | |

### What the numbers say

- Coverage on the product route: 25 of 31 symbologies read every
  variant the reference can read; all the remaining misses at
  1280x720 are either engine limits the reference shares (PDF417,
  Compact and Micro PDF417 and MaxiCode rotated 45 degrees or
  inverted, a tilted stacked DataBar, a tilted DX film edge) or density
  under 1.2 px per module (the small linear variants, the 17-character
  VIN), or three cases that read as upright stills at full size and never
  from the camera (MaxiCode, DX film edge, one large GS1 Data Matrix),
  whose root causes differ: the vendored engine's MaxiCode reader is a
  pure-image decoder with no locator, so it needs a locator (an own
  plugin or an engine detector) rather than a different frame
  orientation; the DX film edge reader restricts its rows under the
  rotate option; the Data Matrix case is unexplained. Every content kind reads and opens
  the right sheet, the four safety verdict classes included; the two
  label misses are a MATMSG mail payload shown as text (a parser gap)
  and the ISBT 128 expectation above.
- Modes: batch (three codes counted, a repeat counted again after a
  blank, Clear then Done), the manual shutter, the four-second cooldown,
  several codes in view (a tap picks one) and the still path (720x1520,
  a 4000x3000 JPEG at 2000x1500, 300x300, 1600x1200) all pass; the
  small EAN-13 beside a QR came in at 1280x720.
- Precision: the baseline produced two single-frame wrong reads that
  opened a sheet (a DataBar Expanded Stacked GTIN at 0.88 px per
  module, a garbled AAMVA PDF417 at 1.68). The gate removes single-frame
  reads from the sheet by construction; those two posters read correctly
  at 1280x720, so the rows show the wrong texts not occurring rather
  than being blocked. A GS1-128 at 1.7 px per module gave a wrong first
  frame in every run (a different wrong text each time); the gate held
  each one and the sheet that followed carried the correct GTIN, expiry
  and lot (the summary's one "wrong read" is that first frame). No
  wrong read reached a sheet in the final run. A stable alias, one wrong
  text recurring across frames, would pass any recurrence rule; it was
  not observed, and the reference reader's witness floor for weak
  linear formats (an independent second reading before a weak 1D read
  counts) is the next layer of the core port for it.
- Latency: the frame costs 79 ms at p50 on two emulated cores at
  1280x720 (engine about 10 ms), against 41 to 47 ms at 640x480; time
  to first read (the engine's first read frame) a median of 0.45 s with
  the scene swap inside it, p95 0.6 s; the sheet follows at least one
  frame later under the gate. Both are far under the 1 s
  ceiling; the numbers are host-CPU numbers through the emulator, not a
  phone claim.
- The gate's cost and the ceiling: 26 of the 27 cases that came in
  reached a sheet; the HIBC at 1.1 px per module read in 2 of 37 frames
  and never had two sightings within three frames, so the gate's cost is
  every read sparser than one frame in three, not one frame. Two first
  reads sat over the 1 s ceiling, both in the hard band of density near
  1.1 px per module and both recovering late: the GS1-128 at 2.0 s and
  the HIBC at 2.3 s (the HIBC feature's median 1.4 s); listed for the
  operator as the decoder gates require.
- Driver-side: the tilted stacked DataBar read in 17 of 19 frames yet
  opened no sheet because the engine returned the same symbol two or
  three times per frame and the "several codes in view" test counted
  reads; it now counts distinct texts; on the targeted rerun (the same build,
  installed again) every stacked DataBar variant opened its sheet, and
  the symbology group's sheets equal its reads (104). The same rerun
  gave the GS1-128 another single-frame wrong text, held off as before.

### Open items

1. Witness floor for weak linear formats in the core (the layer above
   the gate for a stable alias, which the matrix did not see; single-frame
   wrong texts on the dense GS1-128 were all held off); then the full
   frame verifier and precision gate. Its place ahead of or behind the
   remaining coverage is the operator's decision.
2. A per-symbol root-cause probe of MaxiCode (needs a locator: the
   vendored reader has none), DX film edge (row restriction under the
   rotate option) and the large GS1 Data Matrix (unexplained), all of
   which read as upright stills and not from the camera.
3. MATMSG mail payloads in the parser.
4. The 4:3 branch of the frame-size rule is untested here (the
   emulator's legacy-level camera caps the analysis stream at the
   preview size); a phone with 1280x960 exercises it.
5. Density near or below 1.1 px per module (the VIN, the smallest
   linear variants) stays for the still path, closer framing or a larger
   frame; the band is not sharp (two 1.1 px Code 128 symbols read from
   the camera, late). The own linear pipeline's keystone handling
   addresses perspective, not sampling density, and is not the lever.

## Results, round 2 (2026-09-04, the final build)

The matrix grew to 316 cases (every symbology at 30 and 60 degrees and
under a perspective tilt beside the earlier variations; batch with
several codes in view) and the app gained the core's region second
pass, the preview off the UI thread, the two-frame confirmation window,
the engine-only-frames-are-no-evidence rule, the auto-replacing sheet,
the ITF truncation refusal and the control-token rebuild (APP-DESIGN.md
section 41). The rows below are all on the final decode path: the symbology group
rerun in full on the final build (16:09 to 16:46) on a fresh emulator
instance; the content and mode groups rerun after it (17:04 to 17:25) on
the installed build of 16:56, whose decode path is the final build's (the
only change between them is the Report action, outside the scan path);
and nine cases rerun after two driver fixes (the scene blanks before the
reset; the log is split on LF only, since `str.splitlines()` also breaks
at the record separator the licence carries). A clean-room verifier had
refuted the earlier wording, under which most content and mode rows came
from the build before the final one; the rerun is the answer, and it
produced one wrong first read the earlier rows had not. The tables compare the 640x480 baseline (left columns) with
the final build (right columns) as `matrix_md.py` writes them; n is the
baseline's variant count (four per symbology), while the final build's
counts cover all seven variants, so a "Read 1280" of 6 over n 4 means
six of seven.

### Symbologies and content kinds

| Group | Feature | n | Read 640 | Read 1280 | Correct 640 | Correct 1280 | Sheet | Label | TTFR med (s) | Wall p50 (ms) 640 | 1280 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| symbology | Code39 | 4 | 0 | 7 | 0 | 7 | 6 | - | 0.208 | - | 34.8 |
| symbology | Code93 | 4 | 3 | 7 | 3 | 7 | 7 | - | 0.167 | 37.2 | 8.0 |
| symbology | Code128 | 4 | 1 | 7 | 1 | 7 | 7 | - | 0.177 | 36.5 | 8.3 |
| symbology | Codabar | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.157 | 38.6 | 8.4 |
| symbology | ITF | 4 | 3 | 7 | 3 | 7 | 7 | - | 0.171 | 37.7 | 8.4 |
| symbology | ITF14 | 4 | 3 | 7 | 3 | 7 | 7 | - | 0.169 | 39.4 | 8.2 |
| symbology | EAN13 | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.173 | 38.9 | 8.5 |
| symbology | EAN8 | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.15 | 40.75 | 8.4 |
| symbology | UPCA | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.165 | 39.2 | 9.0 |
| symbology | UPCE | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.155 | 39.8 | 9.3 |
| symbology | ISBN | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.178 | 37.1 | 8.4 |
| symbology | Telepen | 4 | 0 | 6 | 0 | 6 | 6 | - | 0.175 | - | 9.85 |
| symbology | PZN | 4 | 0 | 6 | 0 | 6 | 5 | - | 0.362 | - | 24.85 |
| symbology | Code32 | 4 | 3 | 7 | 3 | 7 | 7 | - | 0.304 | 38.8 | 16.4 |
| symbology | DataBar | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.486 | 46.4 | 16.0 |
| symbology | DataBarLtd | 4 | 4 | 7 | 4 | 7 | 6 | - | 0.414 | 42.65 | 8.4 |
| symbology | DataBarStk | 4 | 3 | 4 | 3 | 4 | 4 | - | 0.395 | 41.1 | 7.2 |
| symbology | DataBarStkOmni | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.353 | 44.6 | 8.2 |
| symbology | DataBarExp | 4 | 1 | 6 | 1 | 6 | 6 | - | 0.37 | 55.5 | 7.7 |
| symbology | DataBarExpStk | 4 | 4 | 7 | 3 | 7 | 7 | - | 0.398 | 49.4 | 8.6 |
| symbology | DXFilmEdge | 4 | 0 | 5 | 0 | 5 | 5 | - | 0.503 | - | 62.8 |
| symbology | QRCode | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.359 | 42.75 | 8.5 |
| symbology | MicroQRCode | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.362 | 44.85 | 7.2 |
| symbology | RMQRCode | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.336 | 47.8 | 7.4 |
| symbology | Aztec | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.384 | 46.75 | 8.6 |
| symbology | AztecRune | 4 | 4 | 6 | 4 | 6 | 6 | - | 0.324 | 45.15 | 7.7 |
| symbology | DataMatrix | 4 | 4 | 7 | 4 | 7 | 7 | - | 0.389 | 41.4 | 7.2 |
| symbology | PDF417 | 4 | 1 | 3 | 1 | 3 | 3 | - | 0.305 | 46.5 | 7.4 |
| symbology | CompactPDF417 | 4 | 2 | 3 | 2 | 3 | 3 | - | 0.384 | 47.7 | 8.2 |
| symbology | MicroPDF417 | 4 | 1 | 3 | 1 | 3 | 3 | - | 0.347 | 45.4 | 7.6 |
| symbology | MaxiCode | 4 | 0 | 0 | 0 | 0 | 1 | - | - | - | - |
| content | website | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.332 | 44.0 | 25.6 |
| content | website-caution | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.367 | 46.4 | 15.5 |
| content | website-danger | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.342 | 48.6 | 23.4 |
| content | website-other-danger | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.393 | 48.2 | 14.2 |
| content | script-scheme | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.31 | 47.7 | 21.8 |
| content | wifi | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.345 | 44.7 | 21.9 |
| content | contact | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.36 | 49.0 | 19.9 |
| content | phone | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.263 | 46.7 | 7.9 |
| content | email | 3 | 3 | 3 | 3 | 3 | 3 | 2 | 0.324 | 45.8 | 13.4 |
| content | sms | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.386 | 46.5 | 19.7 |
| content | location | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.362 | 45.3 | 7.5 |
| content | calendar | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.378 | 46.9 | 26.8 |
| content | otp | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.304 | 47.0 | 23.8 |
| content | payment | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.378 | 47.3 | 7.7 |
| content | crypto-address | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.337 | 45.7 | 30.0 |
| content | custom-scheme | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.307 | 46.4 | 24.5 |
| content | text | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.417 | 47.8 | 24.4 |
| content | boarding-pass | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 0.361 | 43.0 | 9.4 |
| content | boarding-pass-aztec | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 0.335 | 54.5 | 15.4 |
| content | boarding-pass-qr | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 0.301 | 50.4 | 20.9 |
| content | id-card | 3 | 1 | 3 | 0 | 3 | 3 | 3 | 0.339 | 61.0 | 27.3 |
| content | product-ean13 | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.375 | 48.5 | 26.5 |
| content | product-upca | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.388 | 44.0 | 25.7 |
| content | book-isbn | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.363 | 40.3 | 19.2 |
| content | vehicle-vin | 3 | 0 | 0 | 0 | 0 | 0 | 0 | - | - | - |
| content | gs1-element-string | 3 | 2 | 2 | 2 | 2 | 2 | 2 | 0.386 | 52.2 | 18.35 |
| content | gs1-128 | 3 | 0 | 3 | 0 | 3 | 3 | 3 | 0.343 | - | 12.5 |
| content | hibc | 3 | 0 | 2 | 0 | 1 | 1 | 1 | 0.486 | - | 30.3 |
| content | isbt-128 | 3 | 2 | 3 | 2 | 3 | 3 | 1 | 0.347 | 43.65 | 15.3 |
| content | medicine-code32 | 3 | 3 | 3 | 3 | 3 | 3 | 3 | 0.338 | 54.8 | 8.7 |
| content | dx-film | 2 | 0 | 2 | 0 | 2 | 2 | 2 | 0.499 | - | 104.5 |

### Modes

| Feature | n | ok | Variants |
|---|---|---|---|
| several-codes | 3 | 2 / 3 | qr+ean13: fail, two-qr: ok, three: ok |
| batch | 3 | 3 / 4 | batch: ok, batch-repeat: ok, batch-clear: ok |
| manual | 1 | 1 / 1 | manual: ok |
| cooldown | 1 | 1 / 1 | cooldown: ok |
| still | 58 | 48 / 20 | ean13-720: ok, qr-4000: ok, pdf417-300: ok, dm-1600: ok, symbology-Code39-nominal: ok, symbology-Code39-small: ok, symbology-Code39-rot90: ok, symbology-Code39-tilt10: ok, symbology-Code93-small: ok, symbology-Code128-small: ok, symbology-Code128-rot90: ok, symbology-Code128-tilt10: ok, symbology-ITF-small: ok, symbology-ITF14-small: ok, symbology-Telepen-nominal: ok, symbology-Telepen-small: ok, symbology-Telepen-rot90: ok, symbology-Telepen-tilt10: ok, symbology-PZN-nominal: ok, symbology-PZN-small: ok, symbology-PZN-rot90: ok, symbology-PZN-tilt10: ok, symbology-Code32-small: ok, symbology-DataBarStk-tilt10: fail, symbology-DataBarExp-nominal: ok, symbology-DataBarExp-small: ok, symbology-DataBarExp-rot90: ok, symbology-DXFilmEdge-nominal: ok, symbology-DXFilmEdge-small: ok, symbology-DXFilmEdge-rot90: ok, symbology-DXFilmEdge-tilt10: fail, symbology-PDF417-small: ok, symbology-PDF417-rot45: fail, symbology-PDF417-inverted: fail, symbology-CompactPDF417-rot45: fail, symbology-CompactPDF417-inverted: fail, symbology-MicroPDF417-small: ok, symbology-MicroPDF417-rot45: fail, symbology-MicroPDF417-inverted: fail, symbology-MaxiCode-nominal: ok, symbology-MaxiCode-small: ok, symbology-MaxiCode-rot45: fail, symbology-MaxiCode-inverted: fail, content-id-card-v1: ok, content-id-card-v3: ok, content-vehicle-vin-v1: ok, content-vehicle-vin-v2: ok, content-vehicle-vin-v3: ok, content-gs1-element-string-v3: ok, content-gs1-128-v1: ok, content-gs1-128-v2: ok, content-gs1-128-v3: ok, content-hibc-v1: ok, content-hibc-v2: ok, content-hibc-v3: ok, content-isbt-128-v1: ok, content-dx-film-v1: ok, content-dx-film-v2: ok |

### Groups

| Group | Cases | Read | Correct | Wrong reads | Sheet | Label | TTFR med (s) | TTFR p95 (s) | TTFR max (s) | Read-frame wall p50 (ms), median over cases | p95 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| symbology (640x480) | 124 | 85 | 84 | 1 | 85 | 124 | 0.221 | 0.9 | 2.66 | 41.35 | 51.0 |
| content (640x480) | 86 | 71 | 70 | 1 | 71 | 69 | 0.221 | 0.3 | 2.201 | 46.8 | 54.6 |
| mode (640x480) | 66 | - | - | - | - | - | - | - | - | 55 ok | |
| symbology (1280x720) | 217 | 189 | 189 | 0 | 187 | 217 | 0.295 | 0.5 | 1.998 | 8.3 | 61.9 |
| content (1280x720) | 86 | 81 | 80 | 1 | 80 | 77 | 0.359 | 0.5 | 0.584 | 21.6 | 31.5 |
| mode (1280x720) | 53 | - | - | - | - | - | - | - | - | 29 ok | |

### What the numbers say, round 2

- Symbology: 189 of 217 read, all 189 correct, no wrong first read on
  any case, 187 sheets (the two sheet misses are the nominal PZN and the
  DataBar Limited at 60 degrees, both read sparsely). First-read time
  median 0.30 s, p95 0.52 s. Against the 1280x720 run of section 34 on
  the 222 common cases: Code 39 3 to 4, DX film edge 0 to 3 of 4,
  MicroPDF417 1 to 2, nothing the other way; every linear symbology now
  reads at 30 and 60 degrees and under the perspective tilt, where the
  engine alone read none; and the read-frame wall time fell from
  70-100 ms to 7-9 ms in every feature but DX film edge (63 ms, read by
  the second pass) and Code 32 (16 ms), because the localizers no longer
  run on a frame the engine has read.
- Content: 81 of 86 read, 80 of the 81 first reads correct, 80 sheets, 77 labels as the
  matrix expects (the MATMSG mail and two ISBT identifiers open as Text,
  the parser gap and the expectation both still filed). Median 0.36 s, p95 0.48 s on an instance that had run walks for two
  hours (read-frame wall 22 ms at p50 against 7 ms fresh); the fresh-instance
  figure of the symbology group is the latency evidence for that group;
  the content group's latency on the final decode path is inferred from
  it, not measured fresh (the 0.19 s of the earlier build's run is not the
  final path). The one wrong first read is the HIBC v3, a dense Code 128 at about 0.7 px
  per module: one frame read "+Z8888eED9/y$326090zJ" for
  "+Z8888MED9/$$3260901J", the gate held it off, and the sheet opened on the
  true payload from the frames that followed (8 read frames of 17). The three driver licences read and parse (name, number,
  dates, issuer) once the log was split on LF; the earlier "wrong" was
  the harness.
- Modes: all 13 mode cases proper pass: batch, batch-repeat, batch-clear, batch with
  several codes in view (the panel counts 2 unique, then 5 unique,
  without a tap: the operator's second ask was already met, so no new
  switch was built), manual, cooldown, the three several-codes cases
  (outlines on each code, tap picks one, the sheet shows the picked
  code), the four still cases; and of the 33 posters the camera path did not read on the final rows, 12 read as upright stills (the still-check rerun of 17:36 to 17:39 on the same installed build: DXFilmEdge-rot60, DXFilmEdge-rot90, DataBarExp-small, MaxiCode-nominal, MaxiCode-small, PZN-small, Telepen-small, gs1-element-string-v3, hibc-v2, vehicle-vin-v1, vehicle-vin-v2, vehicle-vin-v3).
- Over the 1 s ceiling, all recovering, listed for the operator: the PZN
  under keystone at 2.0 s, the DataBar Limited at 60 degrees at 1.25 s,
  the small Code 39 at 1.2 s (0.9 s on the earlier pass; about 1 px per
  module, read in 3 of 72 frames).
- What still misses, by class: the PDF417 family oblique and inverted
  (12 cases; the engine reads it upright only and has no inverted pass),
  MaxiCode in every camera variant (7; no locator), the two-row stacked
  DataBar under tilt and rotation (3), DX film edge at 60 and 90 degrees
  (2), the Aztec rune under keystone (1), the small Telepen, PZN and
  expanded DataBar, the three VINs and the HIBC v2 at 0.7-1.1 px per
  module (density, 8), and the large GS1 Data Matrix (1, unexplained).
- Harness: two driver defects found on the final rows and fixed, the
  reset before the blank (a code still in view reopened its sheet, and
  the MaxiCode nominal case then showed the MicroPDF417 sheet of the case
  before it, which the shot proves) and the log split at record
  separators; neither touched a product path.
- UI: 8 percent janky frames at a 5 ms median during the scan loop on the
  fresh emulator instance; the witness sheets show the region-box
  outline hugging rotated symbols and the sheet following a different
  code without a tap.

### Open items, round 2

1. The PDF417 family at any angle and inverted (12 cases) and MaxiCode
   in every camera variant (7): own plugins under the format-agnostic
   pipeline, or a rotation and inversion pass for PDF417 in the core.
2. The two-row stacked DataBar under tilt and rotation (3): root-cause
   probe of the localizer's split rows before any reader change.
3. DX film edge at 60 and 90 degrees (2): probe the region view offline
   with the rotated poster (crop, angle, upscale); the 90-degree still
   reads, so the view is the suspect.
4. Density under 1.1 px per module (8 cases: the small Telepen, PZN,
   expanded DataBar, the three VINs, the HIBC v2): the still path or
   closer framing; no decoder lever named.
5. Three first reads over the 1 s ceiling, all recovering (PZN keystone
   2.0 s, DataBar Limited at 60 degrees 1.25 s, small Code 39 1.2 s):
   the operator decides whether the sparse-read band is worked; an agent
   may not accept a read over the ceiling. The small Code 39's spread
   across passes (1.06, 0.91, 1.20 s, each a read in 3 of about 70 frames)
   is sampling variance on a sparse read, not a slower leg: its read-frame
   wall is the same engine pass as every other linear symbol.
6. The outline of a rotated linear code read by the engine pass is the
   engine's scan line; a box from the strip's extent and the bar angle
   is the cheap lever.
7. One structure tensor for both localizers (efficiency-only, gated by
   byte identity of the regions).
8. MATMSG in the parser; the ISBT 128 expectation per variant; the large
   GS1 Data Matrix that reads as a still and not from the camera.
9. The single-frame wrong-text class, first rank: the HIBC v3 frame above,
   and, on the 15:02 build, two MaxiCode symbols read as Aztec ("134",
   "172"), all held off by the gate, none shown fixed (the final symbology
   run shows only no-reads on the MaxiCode cases, which is not the class
   fixed, only not hit). The frame verifier and witness floor of the
   reference reader are the lever. The walls today are the confirmation
   window (the same text in this frame and one of the two before, in
   `ScanSession.confirmed`), the ITF ten-digit floor and the distinct-text
   rule; nothing else stands between a garbled frame that repeats and a
   sheet. Root cause not yet probed: how a Code 128 frame with several
   wrong characters passed the engine's mod-103 check at 0.7 px per
   module is the first question, before the lever is worked, with the
   HIBC v3 poster pinned as the rerun of any gate change.
10. The driver's matcher accepted a case's own and its siblings' reference
   reads whatever the variant, so an ITF fragment ("7890") would have been
   scored correct on the rotated ITF cases; no final row depended on it
   (the verifier checked). The matcher now takes reference reads from the
   nominal variant only.
