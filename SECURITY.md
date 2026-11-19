# Reporting a security problem

Verdetto for Android is a free QR and barcode scanner. If you find a weakness in this repository, in a release, or in
the scanning engine it uses (verdetto-core), please tell us before anyone else.

## How to report

- Write to support@verdettoqr.com with "security report" in the subject line.
- Say what you found, how to reproduce it, and which version, commit or release you looked at. A crafted image, code
  or payload that shows the problem is welcome as an attachment.
- You will get an answer within a week.
- We fix first and talk afterwards: the details stay between us until a fixed release is out. We will credit you in
  the release notes if you want that.
- We take no action against anyone who reports in good faith, does not access, alter or keep other people's data,
  and does not disrupt the site, the report route or the list pipeline while doing so.
- Do not send a security report through the report page or a public issue in any of our repositories: both are
  public the moment they arrive.

## Scope

This repository (the app), verdetto-core (the engine), link-safety-list (the safety-list pipeline) and verdettoqr.com.
The threat model the app is built against is SECURITY-MODEL.md in this repository. The site's own contact file is
https://verdettoqr.com/.well-known/security.txt.

## What there is not

There is no bug bounty, and there is no server of ours behind the app holding accounts or people's data: what a build
sends off the device is limited to the lookups and list updates the person can see and switch off, as the privacy
policy describes them.
