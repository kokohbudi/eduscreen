# Issue tracker: Local Markdown

Specs and issues for this repo live under `specs/`, alongside the Spec Kit artifacts.

## Conventions

- One feature per directory: `specs/<NNN>-<feature-slug>/`
- The feature spec is `specs/<NNN>-<feature-slug>/spec.md`
- Detailed behaviour (`BR-*` rules, `AC-*` criteria) is `business-rules.md` in the same directory
- Implementation tasks are `tasks.md`, one checklist line per task
- Ad-hoc issues are one file per ticket at `specs/<NNN>-<feature-slug>/issues/<NN>-<slug>.md`,
  numbered from `01`, never a single combined tickets file
- Triage state is recorded as a `Status:` line near the top of each issue file
  (see `triage-labels.md` for the role strings)
- Comments and conversation history append to the bottom of the file under a `## Comments` heading

## When a skill says "publish to the issue tracker"

Create a new file under `specs/<NNN>-<feature-slug>/issues/` (creating the directory if needed).

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path. The user will normally pass the path or the issue number
directly.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a file with one **child** file per ticket.

- **Map**: `specs/<NNN>-<effort>/map.md` (the Notes / Decisions-so-far / Fog body).
- **Child ticket**: `specs/<NNN>-<effort>/issues/NN-<slug>.md`, numbered from `01`, with the
  question in the body. A `Type:` line records the ticket type
  (`research`/`prototype`/`grilling`/`task`); a `Status:` line records `claimed`/`resolved`.
- **Blocking**: a `Blocked by: NN, NN` line near the top. A ticket is unblocked when every file
  it lists is `resolved`.
- **Frontier**: scan `specs/<NNN>-<effort>/issues/` for files that are open, unblocked, and
  unclaimed; first by number wins.
- **Claim**: set `Status: claimed` and save before any work.
- **Resolve**: append the answer under an `## Answer` heading, set `Status: resolved`, then append
  a context pointer (gist + link) to the map's Decisions-so-far in `map.md`.
