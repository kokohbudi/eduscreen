## Agent skills

### Issue tracker

Specs and issues live under `specs/<NNN>-<feature-slug>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, each label string equal to its role name. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Technical constitution

`CONSTITUTION.md` at the repo root holds the binding technical rules (`TC-01`..`TC-49`): the hybrid Hexagonal/Layered split, identity behind a port, the IDOR policy, and the stack. Read it before writing code. Violating any article is grounds to reject a change.

Division of labour: `CONTEXT.md` is the vocabulary, `specs/001-student-exercise-portal/business-rules.md` is the behaviour (`BR-*` rules and `AC-*` acceptance criteria), `CONSTITUTION.md` is the technical form, `docs/adr/` records why each was chosen.
