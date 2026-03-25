<!--
SYNC IMPACT REPORT
==================
Version change: (new) → 1.0.0
Added sections: Core Principles, Security Requirements, Development Workflow, Governance
Modified principles: N/A (initial ratification)
Templates requiring updates:
  ✅ .specify/templates/plan-template.md — Constitution Check gates reference these principles
  ✅ .specify/templates/spec-template.md — No structural changes required; principles apply at authoring time
  ✅ .specify/templates/tasks-template.md — No structural changes required; security tasks expected in Phase 2
Deferred TODOs: None
-->

# Private Chat Constitution

## Core Principles

### I. Privacy by Design (NON-NEGOTIABLE)

End-to-end encryption using the Signal Protocol MUST be applied to all user
messages. The server MUST never hold plaintext message content, private keys,
or session state that would allow it to read user communications. Every feature
MUST be evaluated against a "zero-knowledge server" standard: if the server
can read it, the design is wrong.

- Private keys MUST be generated and stored exclusively on user devices.
- Key exchange MUST follow the Signal Protocol (X3DH + Double Ratchet).
- Metadata exposure (sender, recipient, timing) MUST be minimized by design.
- Any relaxation of these rules requires explicit threat-model justification
  and governance approval.

### II. Security First

Security controls are not optional enhancements — they are first-class
requirements with the same priority as core functionality.

- Every feature MUST include a threat model entry before implementation begins.
- Authentication MUST use industry-standard mechanisms (e.g., SRP or
  token-based with short-lived credentials).
- Dependencies MUST be audited for known vulnerabilities before adoption.
- No security-relevant code ships without peer review.
- Secrets (keys, tokens, credentials) MUST never appear in source code,
  logs, or version control.

### III. Test-First (NON-NEGOTIABLE)

TDD is mandatory. Tests MUST be written and confirmed failing before any
implementation begins. The Red-Green-Refactor cycle is strictly enforced.

- Unit tests MUST cover cryptographic operations with known test vectors.
- Integration tests MUST cover the full message send/receive lifecycle.
- Security-relevant paths (key generation, encryption, authentication) MUST
  have dedicated test suites.
- A feature is not complete until all its tests pass and coverage gates are met.

### IV. Web-First, Progressive Enhancement

The application is a web application. It MUST function correctly in modern
evergreen browsers without requiring native app installation.

- The frontend MUST be usable without JavaScript frameworks for core flows
  where feasible (progressive enhancement).
- WebCrypto API MUST be used for all in-browser cryptographic operations.
- Offline resilience (message queue, reconnect handling) MUST be considered
  from the start, not retrofitted.

### V. Simplicity & Incremental Delivery

Start with the simplest design that satisfies the privacy and security
requirements. Avoid speculative complexity.

- YAGNI: features not required by current user stories MUST NOT be built.
- Each user story MUST be independently deliverable and demonstrable.
- Architectural abstractions MUST be justified by a concrete current need,
  not anticipated future need.
- Complexity introduced to satisfy Security First or Privacy by Design is
  always justified; all other complexity MUST be documented.

## Security Requirements

All work on this project MUST comply with the following non-negotiable security
constraints:

- **Signal Protocol compliance**: Implementations MUST conform to the
  published Signal Protocol specifications (X3DH key agreement, Double Ratchet
  Algorithm). No proprietary modifications to the cryptographic core.
- **Key storage**: Private identity keys and session keys MUST be stored using
  platform-appropriate secure storage (e.g., IndexedDB with encryption at rest,
  or device secure enclave where available). Keys MUST NOT be transmitted to
  the server.
- **Transport security**: All client-server communication MUST use TLS 1.2+.
  Certificate pinning SHOULD be employed where the deployment model supports it.
- **Dependency hygiene**: Third-party libraries handling cryptography MUST be
  well-audited, actively maintained, and pinned to specific versions.
- **Audit trail**: Security-relevant events (failed auth attempts, key
  registration, device changes) MUST be logged server-side in a tamper-evident
  manner without logging message content.
- **Threat modeling gate**: Every plan.md MUST include a threat model section
  before tasks are generated.

## Development Workflow

- **Branch per feature**: Every feature lives on a numbered branch
  (`###-feature-name`) with a corresponding `specs/###-feature-name/` directory.
- **Spec before plan**: A feature spec (`spec.md`) MUST exist and pass
  Constitution Check before a plan (`plan.md`) is authored.
- **Plan before tasks**: `plan.md` (including threat model) MUST exist before
  `tasks.md` is generated.
- **Security review gate**: Any task touching cryptographic code, key
  management, or authentication MUST be reviewed by a second developer before
  merge.
- **No plaintext secrets in commits**: Enforced via pre-commit hooks. Any
  accidental commit of secrets triggers immediate key rotation.
- **Definition of Done**: A task is done when its implementation is complete,
  tests pass, code is reviewed, and the feature is demonstrable end-to-end.

## Governance

This constitution supersedes all other project practices and conventions.
Amendments require:

1. A written proposal describing the change and rationale.
2. Review and approval by the project lead.
3. A version bump following semantic versioning (MAJOR/MINOR/PATCH rules above).
4. Propagation of changes to all dependent templates and specs.

All pull requests and spec reviews MUST verify compliance with this
constitution. Violations MUST be resolved before merge. Complexity that
contradicts Principle V (Simplicity) MUST be explicitly justified in the
relevant `plan.md` Complexity Tracking table.

**Version**: 1.0.0 | **Ratified**: 2026-03-25 | **Last Amended**: 2026-03-25
