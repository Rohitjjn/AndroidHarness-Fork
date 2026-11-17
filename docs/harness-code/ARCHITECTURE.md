# Harness Code architecture

Harness Code is a re-shell of AndroidHarness. The **engine** (tools, shells,
workspaces, git, MCP, subagents, RunManager) and the **brain** (providers, API
clients, models, memory) are the upstream code, untouched in behaviour. What
changed is the app shell, the design system, the naming and branding, and a set
of new agent capabilities.

This document says exactly where UI ends and the engine begins.

## Module layout (unchanged)

```
app/                      single Gradle module, namespace com.androidharness.app
  src/main/java/com/androidharness/app/
    agent/                RunManager, AgentEngine, TaskControl, TodoStore, subagents
    tools/                Tool implementations + ToolRegistry + MCP
    workspace/            WorkspaceFs, WorkspaceManager, chat workspaces, worktrees
    llm/                  providers, streaming, fallbacks
    data/                 Room, DataStore settings, env (Shizuku/root/ADB), transfers
    github/               auth, repositories, pull requests, PR monitor
    plugins/ skills/      plugin store + manifests, skills
    ui/                   Compose screens
      chat/               the original (Classic) session UI + ChatViewModel
      settings/ files/ …  the original (Classic) management screens
      harness/            NEW: Harness Code shell, theme, session UI
    MainActivity.kt       hosts HarnessCodeRoot
local_gpu/                optional Play dynamic feature
```

## Layering

```
                 ┌────────────────────────────────────────────┐
   UI (new)      │ ui/harness/…                               │
                 │  shell · session · panels · theme          │
                 └───────────────▲────────────────────────────┘
                                 │ one state stream + intents
                 ┌───────────────┴────────────────────────────┐
  Boundary       │ agent/session/AgentSession.kt              │
                 │  HarnessSessionState, TranscriptItem,      │
                 │  SessionPermission, RewindInfo              │
                 └───────────────▲────────────────────────────┘
                                 │ pure mapping (ChatUiState → state)
                 ┌───────────────┴────────────────────────────┐
  UI adapter     │ ui/harness/session/ChatAgentSession.kt     │
                 │  HarnessSessionMapper.kt (pure)            │
                 └───────────────▲────────────────────────────┘
                                 │ ChatViewModel (existing)
  Engine/brain   │ agent/RunManager · tools · llm · data      │
                 └────────────────────────────────────────────┘
```

### The boundary

`agent/session/AgentSession.kt` is engine-neutral: it has no reference to
`RunManager`, tools, providers or Compose. It exposes:

* `state: StateFlow<HarnessSessionState>` — the single thing the UI renders.
  It holds ordered `TranscriptItem` rows (user turn, assistant turn, thinking
  block, tool activity, file change, notice) plus session-level facts: pending
  approval, pending question, plan, checks, todos, queue, permission, context
  usage, phase.
* intents — `send`, `stop`, `approve`, `reject`, `answer`, `setPermission`,
  `setPlanMode`, `approvePlan`, `discardPlan`, `cancelQueued`, `sendQueuedNow`,
  `dismissError`, `diffFor`, `rewindPreview`, `rewind`, `commentOnDiff`.

`ui/harness/session/ChatAgentSession.kt` is the only class in the new UI that
knows `ChatViewModel` exists, and every method is a one-line delegation. It does
not change engine behaviour; it maps the four-step permission ladder onto the
existing primitives:

| Composer chip | PermissionMode           | AgentMode |
| ------------- | ------------------------ | --------- |
| Ask           | `CONFIRM_RISKY`          | `ACT`     |
| Auto-edit     | `FULL_AUTO`              | `ACT`     |
| Plan          | unchanged                | `PLAN`    |
| Full access   | `FULL_ACCESS`            | `ACT`     |

`CONFIRM_ALL` (a stricter upstream mode with no Claude Code equivalent) maps
back to **Ask**; selecting Ask therefore applies `CONFIRM_RISKY`, which still
gates unsafe commands. This is the only lossy mapping in the boundary and it is
deliberate.

`HarnessSessionMapper` is a pure function from `ChatUiState` (+ a small
`Extras` for project name, git branch and workspace path) to
`HarnessSessionState`. No Android, no flows, no side effects — that is what
makes `HarnessSessionMapperTest` able to assert exact transcript rows.

### What the UI is allowed to touch

The new UI may call:

* `AgentSession` (above).
* Read-only lookups for chrome: `WorkspaceManager.forSession/projectForSession`
  (project and branch chip), `SettingsRepository.settings` (theme, pin/archive
  ids), `SessionRepository.sessions` (drawer list), `RunManager.runningSessionIds`
  (live dots, parallel sessions view), `PluginManager.store` (hooks viewer),
  `GitHubAuth.accessToken` + `PullRequestMonitor` (PR panel).
* Existing Classic screens, embedded whole: Settings, Providers, Skills,
  Projects, Automations, Terminal, Build & Test, Files, Changes.

The new UI may **not** call `RunManager` mutation methods, `AgentEngine`, tool
implementations, or providers directly. If a new capability needs engine work,
it is added as a tool (see worktrees) or as a small read-only service
(`PullRequestMonitor`), never inline in a screen.

## Engine-adjacent changes (all additive)

| Change | Why | Behavioural impact |
| --- | --- | --- |
| `workspace/WorktreeManager.kt` | git worktree support for sessions | new capability only |
| `tools/WorktreeTool.kt` + three tools registered in `ToolRegistry.default` | the agent can create/remove/list worktrees | new tools; existing tools unchanged |
| `github/PullRequestMonitor.kt` | read-only PR status + failing-check prompt | new capability only |
| `HarnessApp`: `worktrees`, `pullRequestMonitor` singletons | wire the two services | construction only |
| `app/build.gradle.kts`: applicationId, version, optional CI signing | install alongside upstream; signed releases | no engine impact |
| `MainActivity`: hosts `HarnessCodeRoot` instead of `AppNav` | new shell | Classic shell reachable from Settings → Classic Harness UI |

Nothing was removed. `AppNav` and every Classic screen still compile and still
work; `HarnessRoute.Classic` renders `AppNav(container)` inside the new shell.

## Session lifecycle

1. `HarnessShell` picks a route (`Home`, `Session(id)`, hubs…). A session route
   creates `ChatViewModel.factory(container, sessionId)` — the same factory the
   Classic shell uses — keyed by session id so two sessions never share state.
2. `rememberChatAgentSession` builds the adapter and resolves extras (project
   name, `.git/HEAD` branch, workspace path) off the main thread.
3. The mapper turns `ChatUiState` into transcript rows; `HarnessSessionRoute`
   renders them in a `LazyColumn` and follows the tail while streaming.
4. Panels (diff, files, terminal, build & test, preview, rewind, worktrees, PRs,
   hooks, permissions) are full-screen overlays inside the same composition;
   `BackHandler` closes them before navigating away.
5. Leaving the session does not stop the run: runs live in `RunManager` on an
   app-wide scope, exactly as before, which is what makes the parallel sessions
   view honest.

## Tests

* JVM unit tests (`app/src/test`) — `HarnessSessionMapperTest`,
  `SessionGroupingTest`, `HarnessMarkdownTest`, `WorktreeCommandTest`,
  `FixFailingChecksPromptTest`. These run in CI.
* Instrumented Compose tests (`app/src/androidTest`) — run only in the optional
  `ui-tests` CI job (emulator), never on a plain PR.

## Deliberate limits

* The transcript renderer is a purpose-built Markdown subset (headings, lists,
  quotes, rules, fenced code, inline bold/italic/code/links). Rich blocks
  (tables, charts, Mermaid, interactive previews) remain in the Classic screen.
* New UI strings are English literals inside the new package, not string
  resources, so the 21 existing translations cover the Classic shell only. This
  is listed as a gap in `PARITY.md`.
