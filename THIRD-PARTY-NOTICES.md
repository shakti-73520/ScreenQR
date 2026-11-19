# Third-party notices

Verdetto's own code is free software under the GNU General Public License, version 3 or later (see LICENSE);
its scanning engine is a separate work, verdettoqr/verdetto-core, under the Apache License 2.0, mounted as the
`core/` submodule and built from source into `libverdetto_android.so` by the app's own build, with its NOTICE reproduced at the end.
It ships the work of others under their own licenses, listed here and under About, Licenses, in the app, where
each source is a link and the license texts the sources require are readable (the NOTICES text bundled in the app
at app/src/main/assets/NOTICES.txt). Each library's license also travels with it in the build.

## Code

| Component | License |
|---|---|
| ZXing core (com.google.zxing:core) | Apache License 2.0 |
| verdetto-core (the scanning engine: the `core/` submodule, built into `libverdetto_android.so`) | Apache License 2.0 (its NOTICE below) |
| zxing-cpp (vendored in the engine, built into `libverdetto_android.so`) | Apache License 2.0 |
| AndroidX (core, activity, compose, navigation, lifecycle, camera, exifinterface, room, datastore, browser, print, profileinstaller, splashscreen) | Apache License 2.0 |
| Kotlin standard library and coroutines | Apache License 2.0 |
| Rust `jni` crate (native bridge) | Apache License 2.0 / MIT |
| EdDSA (net.i2p.crypto:eddsa) | CC0 1.0 |
| Google Play Billing library | Android Software Development Kit License |
| libphonenumber, with its geocoding and carrier data (com.googlecode.libphonenumber:libphonenumber, :geocoder, :carrier) - Copyright (C) The Libphonenumber Authors | Apache License 2.0 |

## Fonts (app/src/main/res/font)

| Font | License |
|---|---|
| Roboto (Regular, Medium, Bold; Latin subsets of Roboto 3.015; Copyright 2011 The Roboto Project Authors, https://github.com/googlefonts/roboto-classic); the splash wordmark's outlines are drawn from Roboto Medium (tools/brand/wordmark_paths.py) | SIL Open Font License 1.1, no Reserved Font Name (the copyright notice and the licence text ship in app/src/main/assets/NOTICES.txt, shown under About) |
| Droid Sans Mono (Latin subset) | Apache License 2.0 |

## Word list (app/src/main/assets/bip39)

| Data | License |
|---|---|
| BIP-39 English word list (https://github.com/bitcoin/bips, bip-0039/english.txt, unchanged: 2,048 lines, SHA-256 2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda; BIP-39 by Marek Palatinus, Pavol Rusnak, Aaron Voisine and Sean Bowe), read to keep a recovery phrase sealed and off Search (the audit's C5, 2026-10-23) | MIT (BIP-39's License header; the MIT text in app/src/main/assets/NOTICES.txt, shown under About) |

## Data shipped in the safety bundle (app/src/main/assets/safety, refreshed from the public list pipeline, https://verdettoqr.com/source/)

| Data | License |
|---|---|
| Public Suffix List | MPL 2.0 |
| url-shorteners list by PeterDaveHello | CC BY-SA 4.0 (the list file is redistributed under the same license, with this attribution) |
| Affiliate and click-tracking redirect hosts (the safety list's curated file, built from the same family of lists) | CC BY-SA 4.0 (redistributed under the same license, with this attribution) |
| Unicode confusables | Unicode License |
| Majestic Million (top 10,000) | CC BY 3.0 |
| CERT Polska warning list | CC0 1.0 |
| PhishDestroy destroylist | MIT |
| polkadot-js phishing list | Apache License 2.0 |
| PhishTank | PhishTank terms (hashed prefixes only; PhishTank named in the app) |
| OFAC SDN digital-currency addresses (US Treasury) | US government work, public domain |
| GeoNames postal codes | CC BY 4.0 |
| AIFA Anagrafica Farmaci (Italian Medicines Agency; https://www.aifa.gov.it/liste-dei-farmaci), a reformatted extract | CC BY 4.0 |
| Wikidata IATA codes | CC0 1.0 |
| Deutsche Bundesbank bank sort code file (sort code, name) | The Bundesbank's website terms: fields verbatim, and the source line "Quelle: Deutsche Bundesbank" shown with the data and under About; quarterly refresh, the validity window in the bundle manifest |
| Base de données publique des médicaments (ANSM), CIP13 codes with names and holders | Licence Ouverte / Open Licence 1.0 (Etalab; the BDPM's own licence file: https://base-donnees-publique.medicaments.gouv.fr/docs/telechargement/licence_bdpm.pdf); the credit with the BDPM update date rides in the bundle manifest and is shown with the data and under About |

## VIN data (app/src/main/assets/vin)

| Data | License |
|---|---|
| WMI rows with source=wikibooks in wmi.tsv: from the Wikibooks page "Vehicle Identification Numbers (VIN codes)/World Manufacturer Identifier (WMI)" by its contributors, the version of 2026-09-13 (https://en.wikibooks.org/w/index.php?title=Vehicle_Identification_Numbers_(VIN_codes)/World_Manufacturer_Identifier_(WMI)&oldid=4669923), provided without warranty; modified by Verdetto: the WMI and Manufacturer columns only, the names edited, merged with other rows and re-sorted | CC BY-SA 4.0 (redistributed under the same license, with this attribution) |
| WMI rows with source=vpic in wmi.tsv: NHTSA vPIC (https://vpic.nhtsa.dot.gov) | US government work, public domain |

## Diet and allergen words (app/src/main/assets/lookup)

| Data | License |
|---|---|
| Diet and allergen words in app/src/main/assets/lookup (allergens.json, diet.json): keyed by Open Food Facts taxonomy ids, with words in part from its taxonomies (https://world.openfoodfacts.org), modified and extended by Verdetto | ODbL 1.0 (https://opendatacommons.org/licenses/odbl/1-0/), offered under the same license |

## Word search blocked lists (app/src/main/assets/wordsearch/blocked)

The 26 lists added on 2026-11-12 (row 641) are adapted by Verdetto and licensed CC BY-SA 4.0. `NOTICE` beside them names the sources,
their licences and every change; `LICENSE-MIT` and `LICENSE-Apache-2.0` carry the texts those sources ask for. The sources:
Toxicity-200 (NLLB Team, Meta; CC BY-SA 4.0), LDNOOBW (Shutterstock; CC BY 4.0), turkce-kufur-karaliste (CC BY-SA 4.0),
Persian-Swear-Words (Apache-2.0), inappropriate-words-ja, korean-bad-words and vietnamese-offensive-words (MIT). The eight lists
before them (en, de, es, fr, it, nl, pl, sv) are built from the word search books' lists by tools/wordsearch/banks.py.

## Data the app asks online, and the copies on the app's own host

The lookups become static copies on the app's own host (lookup.verdettoqr.com): one small signed file per scan, built
from the databases' exports by the public build script (https://verdettoqr.com/source/lookup-build),
each copy with its LICENSE and README on the host. Until the copies are switched on, the databases are asked online
with the app's identifying User-Agent and the contact address the open databases ask for. Their answers are shown as
given, with the source named, and are not stored by the app beyond the session.

Open Food Facts and its sister databases (Open Beauty Facts, Open Pet Food Facts, Open Products Facts): ODbL 1.0
(https://opendatacommons.org/licenses/odbl/1-0/); the product copy is a Derivative Database, offered whole with its
changes at lookup.verdettoqr.com with the build script as the alteration method; the product sheet names the source
with "(ODbL)" and the copy's date and links the product page. Open Library: the Internet Archive asserts no rights
over the data; the book copy is made from its monthly dumps and credited "From Open Library" with the copy's date.
MusicBrainz: the music copy is made from MusicBrainz's core data alone (CC0 1.0: the release, artist credit and
release country tables of the full export; never the derived data under CC BY-NC-SA) and credited "From MusicBrainz"
with the copy's date; the online lookup, while it runs, under MetaBrainz's API terms (an app with a revenue stream
counts as commercial; MetaBrainz confirmed by email on 2026-09-07 that use is free in the pre-revenue phase with rate
limiting (one request a second, paced by the lookup client, with the app's identifying User-Agent) and attribution
(the answer names MusicBrainz as its source; the website credits MusicBrainz), and asked to be contacted when the
app's income reaches about $500 a year; the lookup is one constant to switch off). openFDA (the NDC directory, the
device identifiers): US public domain, CC0; FDA does not endorse this app. NHTSA (vPIC, recalls, ratings, and the
owner-complaint and defect-investigation files behind the vehicle safety copy on lookup.verdettoqr.com) and EPA fuel
economy data (fueleconomy.gov): US public domain for the agencies' own data and records; the complaint descriptions
shown are as NHTSA published them; NHTSA and EPA do not endorse this app. The US Consumer Product
Safety Commission's recalls (a US government work, public domain; CPSC does not endorse this app): the recall copy is
made from the CPSC's recall set and the sheet links the notice; until the copies are switched on the Recalls API is
asked by product number (the Recall endpoint only). Wikidata (CC0). Crossref (api.crossref.org; bibliographic metadata made
available for reuse without restriction, no abstracts): the journals copy is made from Crossref's journal records and
Wikidata's items, credited as "Journal data: Crossref (api.crossref.org) and Wikidata (wikidata.org, CC0)" with the
copy's date on the sheet; the app asks Crossref nothing, and a DOI is shown as a link to doi.org. AEMPS CIMA (Spain's
medicines agency; public-sector information reused under Ley 37/2007 and Real Decreto 1495/2011; online only, never
an offline table; the sheet shows the mandatory source line with the retrieval date; no sponsorship implied), asked
by código nacional. EUDAMED, the European database on medical devices (European Commission), reused under CC BY 4.0,
shown as given: one request of the public udiDiData search per live scan, device, manufacturer and class only, never
a named person, never the detail record. DailyMed and RxNav of the US National Library of Medicine (US government
works, public domain; at most three requests per scan, far under the twenty a second per address RxNav allows; a 429
or 503 ends the leg; RxClass is never called), asked by the NDC inside a drug UPC once openFDA has nothing. This
product uses publicly available data from the U.S. National Library of Medicine (NLM), National Institutes of Health,
Department of Health and Human Services; NLM is not responsible for the product and does not endorse or recommend
this or any other product.

## The engine's NOTICE (core/NOTICE, verbatim)

```text
Verdetto scanning engine (verdetto-core)
Copyright 2026 Verdetto

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this work except in compliance with the License. You may obtain a copy of the
License at http://www.apache.org/licenses/LICENSE-2.0 and in the LICENSE file.

Third-party software carried in this repository:

  zxing-cpp, version 3.1.1 (https://github.com/zxing-cpp/zxing-cpp)
  Copyright 2016 Nu-book Inc., 2019 Axel Waggershauser, and the ZXing authors
  Licensed under the Apache License, Version 2.0; its LICENSE and README travel
  under third_party/zxing-cpp, and only its reader core is carried.

Data used by the tests:

  fixtures/muenster_0282925037198.*: intensity profiles derived from one crop
  of an image in the Muenster BarcodeDB (University of Muenster), a public
  research corpus; the raw frames are not carried, and the corpus terms are
  checked and noted before any frame is published.
```
