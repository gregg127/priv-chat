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
npm run docs   # Serve journal docs locally via docsify (run from repo root)
```

## Spec-Driven Development (SDD) Workflow

Features flow through this agent pipeline in order:

1. **`speckit.constitution`** — Define/update project principles (`.specify/memory/constitution.md`)
2. **`speckit.specify`** — Create feature spec → `specs/###-feature-name/spec.md`
3. **`speckit.clarify`** — Up to 5 targeted questions to sharpen the spec
4. **`speckit.plan`** — Generate design artifacts → `plan.md`, `research.md`, `data-model.md`, `contracts/`
5. **`speckit.tasks`** — Break plan into ordered tasks → `tasks.md`
6. **`speckit.analyze`** — Cross-artifact consistency check (non-destructive)
7. **`speckit.implement`** — Execute tasks; resulting code goes into `implementation/`

Agents: `.github/agents/speckit.*.agent.md`  
Prompts: `.github/prompts/speckit.*.prompt.md`

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
