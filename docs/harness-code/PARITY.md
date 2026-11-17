# Feature parity: AndroidHarness → Harness Code

Every user-visible feature is accounted for. **Status** is one of:

* **kept** — same behaviour, same name, now reachable from the new shell.
* **renamed** — same behaviour, Claude Code's name (only where behaviour matches).
* **added** — new in Harness Code.
* **moved** — kept, but grouped under **Labs & Device** because Claude Code has no equivalent.

Renames follow the rule in the brief: use Claude Code's vocabulary **only where
the behaviour really matches**; otherwise keep the AndroidHarness name.

## Naming map

| Old (AndroidHarness) | New (Harness Code) | Where | Why it matches |
| --- | --- | --- | --- |
| Dual planning mode | **Plan mode** | composer permission chip · Settings → All settings | Plan-first runs that end in an approvable plan. "Dual planning" (plan on model A, execute on model B) stays a separate advanced toggle inside Settings, because that part is *not* Plan mode. |
| Checkpoint undo / rewind | **Checkpoints / Rewind** | session menu → Rewind | Per-turn file + message rollback with a preview. |
| AGENTS.md, global instructions, code memory | **CLAUDE.md / Memory** | Settings → Memory · agent prompt | Memory reads and writes `CLAUDE.md` and **still reads `AGENTS.md`** for compatibility. |
| Automations | **Scheduled tasks** | drawer → Scheduled | Cron/interval/on-demand prompts. |
| Workspaces + Projects | **Projects** | drawer → Projects | A project groups sessions, memory and workspaces. |
| Permission manager + modes | **Permissions** (Ask · Auto-edit · Plan · Full access) | composer chip · session menu · Settings | Genuine per-session approval ladder. |
| Message queue, AH Auto | **Queued messages** | composer queue rows | Follow-ups wait for the current turn. |
| Subagents, Skills, Plugins, MCP, Hooks, Slash commands | same names | Settings section · Hooks panel · `/` menu | No rename needed. |

## Feature table

| Feature | New name | Location | Status |
| --- | --- | --- | --- |
| Streaming code chat with thinking + tool cards | — | Session transcript | **kept** (rebuilt) |
| Verification cards (build/test/lint results) | **Verification** | end of transcript | **kept** (rebuilt) |
| Plan mode | Plan mode | permission chip | **renamed** |
| Dual planning (plan model ≠ execute model) | Dual planning | Settings → All settings | **kept** |
| Checkpoint undo | **Rewind** | session menu → Rewind | **renamed** |
| Turn metrics (tokens, tok/s, first-token) | — | expanded tool/turn cards | **kept** |
| File diffs from tool cards | Diff review | transcript file cards → panel | **kept** |
| Diff review comments | **Inline diff comments** | Diff review → "Send review comment" | **added** |
| Message queue | **Queued messages** | composer | **renamed** |
| Subagents (+ background) | same | tool cards · Settings | **kept** |
| Skills manager | same | Settings → Skills | **kept** |
| Plugins & marketplaces | same | Settings → All settings | **kept** |
| MCP servers | same | Settings → All settings | **kept** |
| Hooks (from plugins) | **Hooks** | session menu → Hooks (read-only) | **added** (viewer) |
| Slash commands (`/compact`, `/clear`, `/cost`, `/init`, `/doctor`, `/plan`, `/btw`, `/skills`) | same | composer `/` menu | **kept** |
| AGENTS.md / global instructions / memory | **CLAUDE.md / Memory** | Settings → Memory | **renamed** (AGENTS.md still read) |
| Automations | **Scheduled tasks** | drawer → Scheduled | **renamed** |
| Projects / workspaces | **Projects** | drawer → Projects | **renamed** |
| Files manager + editor | Files | session menu → Files (browser + viewer) · editor via Classic | **kept** (editor in Classic) |
| Terminal (PTY, Shizuku/ADB/root) | Terminal | session menu → Terminal | **kept** |
| Build & Test dashboard | Build & Test | session menu → Build & Test | **kept** |
| Preview (browser/dev server/HTML) | **Preview** | session menu → Preview | **kept** (rebuilt as URL + workspace HTML) |
| GitHub import/commit/push, create PR | Pull requests | session menu → Pull requests | **kept** |
| PR status monitoring | **PR status** | Pull requests panel | **added** |
| "Fix failing checks" action | Fix failing checks | Pull requests panel | **added** |
| Parallel sessions | **Parallel sessions** | drawer · Home "Running now" | **added** |
| Git worktree isolation | **Worktrees** (`worktree_create` / `worktree_remove` / `worktree_list`) | session menu → Worktrees + agent tools | **added** |
| Chat section (research assistant) | Chat | drawer → Sessions (conversations of kind Chat) · Classic shell for the full Chat UI | **kept** |
| Chat agents, knowledge files | Chat agents | Labs & Device | **moved** |
| Live Mode | Live Mode | Labs & Device | **moved** |
| Voice input / read aloud | Voice & speech | composer `+`→Voice (dictation) · Labs & Device (engines) | **kept** |
| Rich replies (tables, charts, Mermaid, HTML previews) | Rich replies | Classic shell | **kept** (not rebuilt) |
| Saved answers, snippets | Saved | Classic shell | **kept** |
| Local GGUF models + GPU | Local models | Labs & Device | **moved** |
| 9Router | 9Router | Labs & Device | **moved** |
| Telegram remote control | Telegram | Labs & Device | **moved** |
| FTP / SFTP sync | FTP / SFTP | Labs & Device | **moved** |
| SSH workspaces | SSH workspaces | Labs & Device | **moved** |
| Shizuku / Wireless ADB / root / Linux env | Terminal & device | Labs & Device | **moved** |
| Key rotation, proxy, fallback chains | Key rotation & fallbacks | Labs & Device | **moved** |
| Harness Canvas, glass, motion blur | Canvas & glass | Labs & Device | **moved** |
| Rumble haptics | Rumble haptics | Labs & Device | **moved** |
| Caveman mode | Caveman mode | Labs & Device | **moved** |
| Custom palettes, themes | Appearance | Settings → Appearance · All settings | **kept** |
| 21 languages | same | Settings → All settings | **kept** (Classic strings) |
| Biometric lock, screenshot policy | same | Settings → All settings | **kept** |
| Storage manager, stats, doctor | same | Settings → All settings | **kept** |
| Classic shell | **Classic Harness UI** | Settings → Classic Harness UI | **kept** (escape hatch) |
| App name, icon, id | **Harness Code**, `com.harnesscode.app` | launcher, About | **renamed** |

## What is not translated or not rebuilt

| Gap | Detail |
| --- | --- |
| New-UI strings | The new package uses English literals. The 21 existing translations still cover the Classic shell, which remains reachable; new screens fall back to English. |
| Rich reply renderer | Tables, charts, Mermaid, HTML previews and saved answers stay in the Classic session screen. The Harness transcript renders Markdown + code + diffs natively. |
| Editor | Tapping a file opens a read-only monospace viewer in the new shell; the full editor (sora-editor, search/replace, language tools) is one tap away in Classic. |
| Chat (research) section | Full Chat UI is in Classic; the new shell lists Chat conversations in the drawer and opens them in Classic. |
| Screenshot tests | No Paparazzi/Roborazzi suite: the toolchain is AGP 9.3.1 + Compose BOM 2026.08 and neither plugin has a release for it that was verified here. Compose UI tests live in `app/src/androidTest` and run only in the opt-in emulator job. |

## Manual test steps

### Worktree flow

1. Open a session whose workspace is a git repository (any project folder with `.git`).
2. Session menu → **Worktrees**. The panel lists existing worktrees from `git worktree list`.
3. Enter a branch, e.g. `feature/try-worktree`, tap **Create worktree**.
   * Expect: "Worktree ready on branch …", the path under the app's `worktrees` folder, and a disk figure.
   * Expect: the project picker now contains a workspace named after the branch folder.
4. Tap **Remove** on that row.
   * Expect: the checkout is deleted, the branch is kept, and the workspace row disappears.
5. Negative checks:
   * Branch already checked out (use the current branch) → refused with the path of the other checkout.
   * A SAF/SSH workspace → refused ("needs a real folder").
   * A folder that is not a repo → refused ("not a Git repository").
6. Ask the agent: "create a worktree on branch demo/wt and list worktrees" → `worktree_create`, then `worktree_list`, both approve-able.

### Pull request flow

1. Connect GitHub in Settings and open a session on a repo with an open PR for the current branch.
2. Session menu → **Pull requests** → expect number, title, `head → base`, a summary pill, and one row per check with ✓ / ✕ / •.
3. With a failing check, tap **Fix failing checks**.
   * Expect: a composer turn containing the PR number, branch and every failing check name, sent as a normal user message; approvals still apply.
4. With no PR on the branch → "No open pull request" with "Ask the agent to open a PR".

### Diff review comment

1. In a session, tap a file card → **Review**.
2. Scroll the unified diff, type a comment, tap **Send review comment**.
3. Expect: the panel closes and the comment arrives as a user turn quoting the file path; the agent can act on it.

### Parallel sessions

1. Start a run in session A, open the drawer → **Parallel sessions**.
2. Expect: A under RUNNING with a live dot; open B, answer a question there; A must still be running when you return.
