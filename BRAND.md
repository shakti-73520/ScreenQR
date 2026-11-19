# Verdetto brand assets

The code of this repository is free software under the GNU General Public
License, version 3 or later (see `LICENSE`). The brand is not.

The following are **not** licensed under the GPL and are **all rights
reserved** by the owner of the Verdetto trade name:

- the name **Verdetto** and the app title "Verdetto: QR & Barcode Scanner";
- the launcher icon in every form, the splash mark, and the QR mark and
  wordmark lockup drawn from them (the warning card, the table card, the
  plain-code caption);
- the wordmark, in any typeface or rendering;
- the screenshots, the feature graphics, the store listing text and every
  other store or press material;
- the domain verdettoqr.com and the store listing identity.

Verdetto and the Verdetto QR mark are trademarks; a United States
application for VERDETTO is pending (serial no. 50092495).

Use of the name, the icon and the "Built on Verdetto" badge is governed by
the Verdetto Community License, a trademark license, at
verdettoqr.com/community-license.

## Reserved paths

Everything under these paths is reserved; everything else in the repository
is code or data under `LICENSE` or the third-party terms in
`THIRD-PARTY-NOTICES.md`.

- `app/src/main/res/mipmap-*/` (the launcher icon)
- `app/src/main/res/drawable/ic_launcher_foreground.xml`,
  `ic_launcher_background.xml`, `ic_launcher_monochrome.xml`, `ic_splash.xml`,
  `splash_wordmark.xml` (the wordmark's outlines, on the splash)
- `app/src/main/res/drawable/ic_scan.xml` where it renders the mark
- `tools/l10n/listing/` (the store listing text and its translations)
- `tools/screens/out/` and any screenshot or feature graphic under
  `tools/screens/`
- the strings that carry the name (`app_name` and the sentences that say
  "Verdetto") in `app/src/main/res/values*/strings.xml`

## What a fork must do

A fork of this code must use its own name and its own artwork: replace the
name wherever it appears (`app_name` and the sentences that say it in the
strings, the listing, `INTENT.md`), the icons and the splash mark, and the
site addresses, **except in the copyright and license notices**, which the
GPL requires a fork to keep as they are (the copyright line names Verdetto
as the holder of the code's copyright; that is not a use of the brand).
Keeping the name or the mark for the fork's own identity is not permitted.

Third-party material keeps its own terms (`THIRD-PARTY-NOTICES.md`).
