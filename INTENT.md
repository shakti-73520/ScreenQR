# Scanning with Verdetto from another app

Verdetto answers five intents and two shares. Nothing here needs a permission,
a library, or a key. The person keeps every setting of the app; your app
receives the text of the code the moment they confirm it.

## The intents

| Action | Filter in the manifest | What happens | Result |
|---|---|---|---|
| `com.google.zxing.client.android.SCAN` | yes, implicit intents work | Opens the scanner. The first code the person locks on is handed back, as the ZXing Barcode Scanner did it. | `RESULT_OK` with `SCAN_RESULT` and `SCAN_RESULT_FORMAT` |
| `app.scanner.action.SCAN` | yes | Opens the scanner. Started with `startActivityForResult` (or a result launcher) it hands the code back the same way; started plainly it just opens the app on the scanner. | `RESULT_OK` with the two extras when started for a result |
| `app.scanner.action.CARD` | no filter: an explicit intent with the package `com.verdettoqr.scanner` only | Opens the person's own contact card editor (the code they show to share their details). Meant for the app's own widgets and shortcuts; another app may call it, but nothing comes back. | none |
| `app.scanner.action.CREATE` | no filter: explicit only, as `CARD` | Opens Create, the person's own codes. Meant for the app's launcher shortcuts; nothing comes back. | none |
| `app.scanner.action.HISTORY` | no filter: explicit only, as `CARD` | Opens History. Meant for the app's launcher shortcuts; nothing comes back. | none |
| `app.scanner.action.LISTS` | no filter: explicit only, as `CARD` | Opens History with the sheet of the person's lists over it. Meant for the app's launcher shortcuts; nothing comes back. | none |
| `android.intent.action.SEND` with `image/*` | yes | Decodes a picture: the app shows the result sheet for the codes in it. | none |
| `android.intent.action.SEND` with `text/plain` | yes, on its own small activity, the entry labelled "Add to list" | Puts the lines of the text on a list of the person's choosing as typed items, one per line, blank lines and leading bullets or numbers stripped and nothing else parsed; the list then opens in the app. Text that is not one link: nothing leaves the phone. One link opens the result sheet as a scanned link does, and its link checks send what N13 names, under the switch (N15). Nothing is returned to the caller. | none |

The person can turn the hand-back off: Settings, "Answer scan requests from
other apps" ("Another app can ask Verdetto to scan a code for it; the result
goes back to that app."), on by default. With it off, a scan request is
answered `RESULT_CANCELED` at once: no scanner opens and nothing is scanned.
Back from the scanner is `RESULT_CANCELED` too. The first time any app asks
(once per install, not once per app), the person sees a one-time line naming
your app, "<your app> asked for a scan", with "Scan for <your app>" and "Not
now"; "Not now" is `RESULT_CANCELED` without a scan. Every later request runs
without the line while the setting is on.

A request that reaches Verdetto while it is already open is served the same
way, whatever screen the person is on: the scanner comes up over that screen,
a result sheet standing at that moment closes and the batch panel steps
aside, so the code your app receives is one read after it asked.

No request extras are read today: not `SCAN_MODE`, `SCAN_FORMATS`,
`PROMPT_MESSAGE`, `SAVE_HISTORY`, or any other. The scanner reads every
symbology it knows on every call. On a scan request the read goes straight
back to your app (the operator's word, 2026-09-10): nothing leaves the phone
for it, no lookup runs, no result sheet opens, no History row is kept, and the
app's own checks are not run on it; your app decides what to do with the text.

## The result

- `SCAN_RESULT` (String): the code's content, exactly the bytes the code
  carried, decoded as text (UTF-8 where the symbology allows it, the
  symbology's own character set otherwise).
- `SCAN_RESULT_FORMAT` (String): the symbology in upper case with underscores,
  the ZXing names where they exist. Delivered today: `QR_CODE`, `MICRO_QR_CODE`,
  `RMQR_CODE`, `DATA_MATRIX`, `AZTEC`, `PDF417`, `MAXICODE`, `HAN_XIN`,
  `EAN_13`, `EAN_8`, `UPC_A`, `UPC_E`, `CODE_128`, `CODE_39`, `CODE_93`,
  `CODABAR`, `ITF`, `DATABAR`, `DATABAR_EXPANDED`, `DATABAR_LIMITED`,
  `DX_FILM_EDGE`, and the other symbologies the app's own decoders add, in
  the same spelling (the name shown on the result sheet, upper-cased,
  spaces and hyphens as underscores).

Nothing else travels: no image, no location, no history.

## Kotlin

```kotlin
private val scan = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val text = result.data?.getStringExtra("SCAN_RESULT")
        val format = result.data?.getStringExtra("SCAN_RESULT_FORMAT")
        // use text and format
    }
}

fun startScan() {
    val intent = Intent("app.scanner.action.SCAN")
    if (intent.resolveActivity(packageManager) != null) scan.launch(intent)
    else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GET_VERDETTO)))
}

// the store page, with the developers referrer
private const val GET_VERDETTO =
    "https://play.google.com/store/apps/details?id=com.verdettoqr.scanner"
```

On Android 11 and later, add the query to your manifest so `resolveActivity`
can see the app:

```xml
<queries>
    <intent>
        <action android:name="app.scanner.action.SCAN" />
    </intent>
</queries>
```

## Java

```java
private final ActivityResultLauncher<Intent> scan = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                String text = result.getData().getStringExtra("SCAN_RESULT");
                String format = result.getData().getStringExtra("SCAN_RESULT_FORMAT");
                // use text and format
            }
        });

void startScan() {
    Intent intent = new Intent("app.scanner.action.SCAN");
    if (intent.resolveActivity(getPackageManager()) != null) scan.launch(intent);
    else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GET_VERDETTO)));
}
```

## The ZXing action

Code written for the ZXing Barcode Scanner keeps working: send
`com.google.zxing.client.android.SCAN` the same way and read the same two
extras. If more than one scanner on the phone answers it, the system asks the
person which to use; sending the intent to the package `com.verdettoqr.scanner`
skips that.

## What the person sees

The scanner opens as it always does, with its own safety checks. When a code
locks, the app hands it back and closes; nothing of yours appears on the
screen, and nothing of theirs (history, settings, the safety list) is
touched by the call.
