# Skills used, and the clean-room check

## 1. `replica-skill` (Jakeschincariol/replica-skill) — read in full, verified safe

Fetched on 2026-11-17 from `https://github.com/Jakeschincariol/replica-skill.git`
(MIT, Copyright (c) 2026 Jake Schincariol). Files read: `README.md`,
`replica-design/SKILL.md`, `replica-brand/SKILL.md`, `replica-diff/SKILL.md`
and the plugin manifest.

**What it is safe for.** The skill is explicitly a clean-room method: it says
"it rebuilds what an app does, never what it owns", and every skill enumerates
what must not be taken — logos, icons, illustrations, photos, sounds, licensed
fonts, and the original's copy or brand colour. `replica-design` mandates an
open icon set and an open font with the same *job*; `replica-brand` requires a
sweep (`sweep.py`) for the original's name, domains and colours before launch.

**What was used here.**

| Skill | How it was applied |
| --- | --- |
| `replica-design` | Colour **roles** (bg, surface, border, text, muted, accent, on-accent, danger, success, warning), a snapped type scale, a 4-based spacing scale, two radii and two motions — written as tokens in `HarnessCodeTheme.kt`, then components, then screens. Contrast checked with the WCAG formula; results in `DESIGN.md`. |
| `replica-brand` | Own name (Harness Code), own warm clay accent from a different hue family than the reference's, a logo brief satisfied by an original geometric mark (open loop + dot), a voice check on the most-seen strings (empty states, composer placeholder, About), and a leftovers sweep (below). |
| `replica-diff` | The parity table in `PARITY.md` is written as a feature matrix (kept / renamed / moved / added) rather than a pixel diff, exactly as the skill argues: parity means the same job, not the same look. |

**What was NOT done.** No screenshot was taken of any official Claude app, no
APK was decompiled or unpacked, no asset, string, icon or font was extracted.
The layout was rebuilt from the publicly described shape of a terminal coding
assistant (transcript + tool cards + diffs + permissions), which is what the
skill's `replica-recon` describes as public-surface recon.

**Leftovers sweep** (the skill's `sweep.py` logic, applied by hand):

| Check | Result |
| --- | --- |
| "Claude" / "Anthropic" inside the app name, icon, launcher label | none — the launcher label is "Harness Code" |
| Anthropic trademarks in store/short copy | none |
| "Claude" as a feature name | only where the feature *is* the Claude Code convention the brief asked for: the `CLAUDE.md` memory contract in docs, the `/plan` etc. slash command names, and the About disclaimer |
| Reference brand colour (`#D97757`-family) in code | not used; the accent is `#B4522F` (light) / `#E08A66` (dark) |
| Anthropic mark resemblance | none: the icon is a stroked open ring with a centre dot; there is no starburst, no radiating strokes, no shared letterform |
| Proprietary fonts | none; Newsreader, Inter and JetBrains Mono are OFL, licences shipped in `app/src/main/assets/licenses/` |
| Upstream licence / attribution | `LICENSE` untouched, upstream attribution in the About screen and README section |

## 2. Anthropic "frontier designer" skill — **not available**

The frontier designer skill is an internal Anthropic skill. It is not published
in any repository reachable from this environment (checked against
`github.com`, which is the only code host reachable here), so it could not be
read or applied.

**Closest available substitute, used instead:**

1. `replica-design` + `replica-brand` above — the design-system-first method:
   tokens → contrast report → component specs → screens.
2. The repo's own bundled design skills, which the agent in this app uses:
   `app/src/main/assets/skills/design-tokens`, `web-design` and
   `popular-web-designs`. `design-tokens` was used for the token naming and the
   light/dark value pairs; `web-design` for the spacing and hierarchy rules.

The result is the same shape of deliverable the frontier skill would produce —
tokens, type scale, component specs, screen map, motion rules — documented in
`DESIGN.md`.

## 3. Attribution

* AndroidHarness — MIT, Sanuu7 and contributors. `LICENSE` retained verbatim.
* replica-skill — MIT, Jake Schincariol. Used as a method, no code copied.
* Newsreader, Inter, JetBrains Mono — SIL Open Font License 1.1, full texts in
  `app/src/main/assets/licenses/`.
* Harness Code is an unofficial personal project, not affiliated with Anthropic.
