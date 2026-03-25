# Copilot Instructions

## Repository Structure

```
priv-chat/
├── .github/          # Speckit agents and prompts
├── .specify/         # Speckit memory, scripts, and templates
├── journal/          # Personal notes and architecture documentation (docsify)
└── implementation/   # Application source code (not yet started)
```

The `journal/` directory contains development process notes. No application source code exists yet.

## Commands

```bash
npm run journal   # Serve journal locally via docsify
npm run github    # Serve .github/ (agents & prompts) locally via markserv
```

## SDD Workflow

Invoke each step as a slash command in Copilot Chat (from `.github/prompts/`):

| Slash Command | Purpose |
|---|---|
| `/speckit.constitution` | Define/update project principles → `.specify/memory/constitution.md` |
| `/speckit.specify` | Create feature spec → `specs/###-feature-name/spec.md` |
| `/speckit.clarify` | Ask up to 5 targeted questions to sharpen the spec |
| `/speckit.plan` | Generate design artifacts (`plan.md`, `research.md`, `data-model.md`, `contracts/`) |
| `/speckit.tasks` | Break plan into ordered tasks → `tasks.md` |
| `/speckit.analyze` | Cross-artifact consistency check (non-destructive) |
| `/speckit.implement` | Execute tasks; resulting code goes into `implementation/` |
| `/speckit.checklist` | Generate a custom feature checklist |
| `/speckit.taskstoissues` | Convert tasks into GitHub issues |

Run these in order for each feature.

## Key Conventions

### Feature Branches & Spec Directories

Every feature gets a branch and matching spec directory:

```
Branch:     001-feature-name
Specs dir:  specs/001-feature-name/
```

Create a new feature with:

```bash
.specify/scripts/bash/create-new-feature.sh "Feature description"
# Flags: --short-name "custom-name", --number N, --timestamp
```

### Spec Directory Structure

```
specs/###-feature-name/
├── spec.md        # User stories + acceptance scenarios
├── plan.md        # Technical design
├── research.md    # Phase 0 research
├── data-model.md  # Entity definitions
├── contracts/     # API/interface contracts
└── tasks.md       # Ordered implementation tasks
```

### Constitution

`.specify/memory/constitution.md` is the **project-level source of truth**. All specs and plans must pass a "Constitution Check" gate. It is not yet filled in — run `speckit.constitution` before starting feature work.

### Task Format (tasks.md)

```
[ID] [P?] [Story] Description
```
- `[P]` = can run in parallel
- `[Story]` = user story reference (US1, US2, …)
- Tasks are grouped by user story to enable independent delivery

### User Stories (spec.md)

Each story must be:
- **Priority-ordered** (P1 = most critical MVP slice)
- **Independently testable** — one story alone produces demonstrable value
- Written in **Given/When/Then** acceptance scenario format

### Scripts (auto-approved in VS Code terminal)

| Script | Purpose |
|--------|---------|
| `create-new-feature.sh` | Create branch + seed spec dir |
| `check-prerequisites.sh` | Validate required docs exist for current phase |
| `setup-plan.sh` | Initialize plan artifacts |
| `update-agent-context.sh` | Sync agent context files |
