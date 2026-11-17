# Harness Code design spec

Goal: the feel of a terminal coding assistant on a phone — a calm paper
surface, one warm accent, generous spacing, and a transcript where every action
is a card you can open.

Nothing here is copied from Anthropic's apps. The system (roles, scale,
spacing, component behaviour) is standard product design; the palette, type
pairing, mark and copy are original. See `SKILLS.md` for the clean-room check.

## Colour tokens

Roles, not hex codes, in code (`ui/harness/theme/HarnessCodeTheme.kt`). Measured
with the WCAG 2.1 formula; AA needs 4.5:1 for body text and 3:1 for large text
and control borders.

### Light — warm paper

| Token | Value | Used for |
| --- | --- | --- |
| `paper` | `#F6F2EC` | screen background |
| `surface` | `#FCFAF6` | cards, composer, transcript rows |
| `raised` | `#FFFFFF` | dialogs, menus |
| `sunken` | `#EFEAE1` | user bubble, inset rows |
| `sidebar` | `#F1ECE4` | drawer / tablet sidebar |
| `border` | `#E3DCD0` | hairline borders |
| `borderStrong` | `#D2C9BA` | input outlines, dividers under focus |
| `text` | `#241F1B` | primary text |
| `muted` | `#6A6257` | secondary text |
| `faint` | `#8C8375` | section labels, metadata |
| `accent` | `#B4522F` | primary buttons, caret, links |
| `accentHover` | `#9C4526` | pressed accent |
| `onAccent` | `#FFFFFF` | text/icons on accent |
| `accentSoft` | `#F3E3D9` | selected rows, chips, glyph tiles |
| `accentInk` | `#8E3D1F` | accent text on soft/paper |
| `success` / `warning` / `danger` | `#2F6B45` / `#8A5A12` / `#A3342B` | status |
| `codeBlock` | `#F2EDE4` | code and diff surfaces |

Measured (light): text/paper **14.6:1**, text/surface **15.7:1**,
muted/surface **5.8:1**, accentInk/surface **7.1:1**, onAccent/accent **5.0:1**,
accent/paper **4.5:1**. `faint` is 3.4:1 and is therefore only used at 11sp
semi-bold for uppercase labels and metadata, never for sentences.

### Dark — warm ink

| Token | Value | Notes |
| --- | --- | --- |
| `paper` | `#1A1816` | warm near-black, never pure grey |
| `surface` | `#211E1C` | cards |
| `raised` | `#2A2623` | menus, sheets |
| `sidebar` | `#161413` | drawer |
| `border` | `#3A342F` | hairlines |
| `text` | `#F1EBE3` | 15.0:1 on paper |
| `muted` | `#B3A99C` | 7.7:1 |
| `faint` | `#8A8075` | 4.6:1 |
| `accent` | `#E08A66` | 6.7:1 on paper |
| `accentSoft` | `#3A2A22` | selected rows |
| `accentInk` | `#F0A585` | 8.8:1 |

The app also offers the upstream AMOLED mode; Harness Code maps it to the dark
palette with black paper.

## Type

Three OFL families, statically instanced from the upstream variable fonts and
subset to Latin + the symbols the UI uses (≈680 KB total). Licences ship in
`app/src/main/assets/licenses/`.

| Role | Family | Size / line | Weight |
| --- | --- | --- | --- |
| Display / greeting | Newsreader | 38/44, 30/36, 24/30 | Medium |
| Headings | Newsreader | 24/30, 20/26 | Medium |
| Titles | Inter | 20/26, 17/22, 15/20 | SemiBold |
| Body | Inter | 17/26, 15/23, 13/19 | Regular |
| Labels | Inter | 15/20, 13/17, 11/15 +0.3 tracking | Medium |
| Code | JetBrains Mono | 13/19 | Regular/Medium |

Font size follows the system scale (sp everywhere); the layout was checked at
1.3× (see `PARITY.md` for what is verified and what is not).

## Spacing, radius, motion

* Spacing: 4-base (`2, 4, 8, 12, 16, 24, 32, 48`); transcript gutter 20dp;
  reading measure capped at 720dp on tablets.
* Radius: 6 / 10 / 14 / 20 / 28dp; cards 16dp, composer 20dp, chips pill.
* Borders over shadows: 1dp hairline at `border`, never elevation.
* Motion: 150–220ms fades and slides only; a 700ms blinking streaming caret; a
  1100ms breathing dot for live runs. No spring, no parallax, no blur.

## Components

| Component | Spec | Notes |
| --- | --- | --- |
| `HarnessCard` | surface fill, 1dp border, 16dp radius, 16dp padding, optional click | the only card shape |
| `PrimaryButton` | 48dp, accent fill, onAccent text, 12dp radius | one per screen region |
| `QuietButton` | 48dp, transparent, 1dp border | secondary and destructive (danger tint) |
| `Pill` | 34dp, 999dp radius, sunken/soft fill | branch, model, permission, counts |
| `GlyphTile` | 36dp square, 10dp radius, accentSoft | list leading icon |
| `ListRow` | 44/56dp | drawer, hubs, files |
| `ContextRing` | 26dp ring, 2dp stroke | amber ≥75%, red ≥90% |
| `StreamingCaret` | 2×15dp blinking bar | appended to a streaming answer |
| `UserTurn` | sunken bubble, right aligned, max 90% width | attachments count under text |
| `AssistantTurn` | full width markdown, no bubble | matches a terminal transcript |
| `ThinkingBlock` | collapsed row, expands inline | "Thought for 12s" |
| `ToolActivity` | one row per call: icon, title, monospace summary, status, ± counts; expands to INPUT / OUTPUT | status: running dot, ✓, ✕ |
| `FileChange` | path + `+N −M`, actions Review / Comment / Rewind | opens diff panel |
| `ApprovalCard` | border in accent, diff preview, Approve / Always allow / Reject / Comment instead | pinned above composer |
| `QuestionCard` | options, "Suggested" pill, multi-select submit | blocks the turn |
| `PlanCard` | markdown plan + Approve plan / Discard | Plan mode result |
| `VerificationCard` | ✓/✕ per build/test/lint command | hidden while a turn runs |
| `Composer` | 20dp radius surface, multiline field, `+` menu (photo, voice, slash, skills), model chip, permission chip, context ring, send/stop, queue rows above | grows to 160dp then scrolls |
| `ErrorBanner` | danger tint on 10% danger fill | dismissible |

Touch targets: every interactive element is ≥48dp tall (icon buttons are 40dp
visual inside 48dp hit areas; `Pill` chips are 34dp tall but sit in 40dp+ rows —
see the accessibility gaps in `PARITY.md`).

## Screen map

```
Home (welcome)            greeting · project picker · suggestion chips · running · recent
├─ Session               top bar (title · project · branch · permission) → transcript → composer
│  ├─ Diff review        unified diff · inline comment → agent
│  ├─ Files              workspace browser · mono viewer
│  ├─ Terminal           existing PTY terminal
│  ├─ Build & Test       existing dashboard · "fix with agent"
│  ├─ Preview            URL bar · workspace HTML · WebView
│  ├─ Rewind             per-turn file/message preview → rewind
│  ├─ Worktrees          create / list / remove · storage report
│  ├─ Pull requests      status · checks · fix failing checks
│  ├─ Hooks              read-only, from installed plugin manifests
│  └─ Permissions        Ask · Auto-edit · Plan · Full access
├─ Parallel sessions     running first, then recent
├─ Projects              project list · sessions · manage workspaces
├─ Scheduled             existing automations screen
├─ Labs & Device         local models, 9Router, Telegram, terminal/device, FTP/SFTP,
│                        SSH, voice, Live Mode, chat agents, canvas/glass, rumble,
│                        key rotation/proxy/fallbacks, caveman, automations
├─ Settings              appearance · all settings · providers · skills · labs · about · classic
├─ About                 disclaimer · engine licence · typefaces · version
└─ Classic               the full original AppNav shell
```

Drawer (phone) / persistent sidebar (≥840dp): wordmark, search, New session,
sessions grouped Pinned · Today · Yesterday · Earlier with per-row pin / archive
/ delete, then Projects, Scheduled, Parallel sessions, Labs & Device, Settings,
and a full-width New session button at the bottom.

## Light / dark / accessibility

* Light, dark, system, plus upstream AMOLED, switchable in Settings → Appearance.
* Every icon-only control has a `contentDescription`; cards expose their title
  and status text; the composer field and send button are labelled; colour is
  never the only signal (✓/✕ glyphs accompany success/failure colours).
* Large font scale: `sp` throughout, no fixed-height text containers except
  icons/pills; the composer caps at 160dp and scrolls.
