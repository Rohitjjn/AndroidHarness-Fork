# Harness Code 1.0.0 — pre-release

A rebranded AndroidHarness fork with a terminal-assistant shell: warm paper
light theme, warm ink dark theme, one clay accent, and a session screen built
around streaming transcript cards.

## What's new in this build

* **New shell** — drawer / tablet sidebar with sessions grouped Today,
  Yesterday, Earlier (pin, archive), a welcome screen with suggestion chips, and
  a session screen with a composer pinned above the keyboard.
* **Transcript cards** — streaming Markdown, collapsible Thinking, tool activity
  that expands to Input / Output, inline file-diff cards with Approve, Reject and
  Comment, question cards with options, plan approval and verification cards.
* **Side panels** — Diff review (with inline comments sent to the agent), Files,
  Terminal, Preview, Build & Test, Rewind/checkpoints, Worktrees, Pull requests,
  Hooks (read-only) and Permissions.
* **New agent features** — `worktree_create` / `worktree_remove` /
  `worktree_list` tools, parallel sessions, pull-request status monitoring with a
  "fix failing checks" action.
* **New identity** — launcher label "Harness Code", original adaptive icon
  (foreground, background, monochrome), application id `com.harnesscode.app`, so
  it installs beside the original AndroidHarness.
* **Nothing removed** — local models, Telegram, FTP/SFTP, 9Router, Canvas, glass,
  root/Shizuku/ADB, key rotation, proxies, fallbacks, Live Mode, chat agents and
  the rest live under **Labs & Device**. The original interface is one tap away
  in Settings → *Classic Harness UI*.

## Signing

If the repository has `HARNESS_KEYSTORE_BASE64`, `HARNESS_KEYSTORE_PASSWORD`,
`HARNESS_KEY_ALIAS` and `HARNESS_KEY_PASSWORD`, the release APK is signed with
that key and the file name says `signed`. Otherwise it is signed with the CI
debug key and the file name says **test-signed** — install it to try the app,
do not treat it as a distributable build.

## Reading the artefacts

Each run of `Harness Code CI` uploads `harness-code-apks` (debug + release),
`unit-test-report`, `lint-report` and `build-logs`. The instrumented Compose UI
tests run only from the Actions tab (*Run workflow* → "Also run the instrumented
Compose UI tests").

## Status

See `docs/harness-code/PARITY.md` for the feature-by-feature table and the list
of what is not rebuilt in the new shell.
