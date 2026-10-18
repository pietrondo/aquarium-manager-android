# Beads implementation workflow

Beads is the sole implementation/status tracker. The local database was initialized with **bd 1.1.2** after an independently verified isolation recovery. Prefix `aq`; metadata project_id `141cacd3-d72f-4d12-8707-27a2f8adbc1e`. The embedded database is local under this repository's `.beads/embeddeddolt/aq/`; there are no attached Dolt remotes.

## Entry points

| Epic | Scope |
|---|---|
| aq-7zd | Foundation, pinned toolchain/Gradle/CI and architecture boundaries |
| aq-7u7 | Room schema/migrations, data-driven catalogs, aquariums/profiles |
| aq-e7g | Parameter readings/trends and maintenance |
| aq-adw | Fauna/flora stock, products, timezone plans and dosing snapshots |
| aq-d34 | Versioned DTOs, coherent export, hostile parser, replace restore, SAF and automation |
| aq-ct8 | PixelLab tooling, adaptive/themed launcher, accessibility and release/device validation |

There are 24 child tasks with descriptions, design/test requirements, acceptance criteria and blocking dependencies. Query Beads for current status; do not maintain a parallel markdown checklist. Asset tooling `aq-ct8.1` is closed based on mock/offline verification, not visual approval. Adaptive/themed icons remain `aq-ct8.2`. The initial implementation entry is `aq-7zd.1` (reproducible Gradle/CI); Android work remains open.

Every command must set **process-local canonical BEADS_DIR** to avoid a home tracker:

```powershell
$env:BEADS_DIR = (Join-Path (Resolve-Path .).Path '.beads')
bd ready --type task --json
$env:BEADS_DIR = (Join-Path (Resolve-Path .).Path '.beads')
bd show aq-7zd.1 --json
```

Run these only from the verified project root. `scripts/seed_beads.py` contains the original idempotent seed definitions (not status); it sets the local environment for each subprocess and refuses foreign issue prefixes/home project identity. It must not be used to initialize a tracker. Independent isolation checks precede its first run.

## Critical initialization caveat

`BEADS_DIR` alone does **not** prevent `bd init` from inheriting home `sync.remote` in this installed version. `dolt.local-only: true` does not prevent its earlier bootstrap clone. Source-audited explicit opt-out is `--remote=` (empty). Do not initialize/force/reinitialize a nonempty tracker casually. The session required two in-project quarantines, ignored by Git, before the clean initialization. No issue mutations were made to the foreign clones. Never add quarantine contents, embedded DB chunks, credentials or interaction logs to commits. `bd init` also auto-commits metadata/ignore files; review its changes before publication.

The effective `sync.remote` may still inherit the home value even though there are no attached remotes. **Do not run `bd dolt push`, `pull` or bootstrap** against this project until a project-specific remote is deliberately configured and independently verified. No cloud/Dolt credentials or remote were configured by this delivery.

## Portable issue persistence (not a full backup)

`.beads/issues.jsonl` is a supported `bd export` snapshot containing all 30 project issues, acceptance fields and dependency relationships. It is intentionally versionable and must be included in the reviewed Git delivery; the embedded DB is ignored and does not travel with a normal Git push. After status changes:

```powershell
$env:BEADS_DIR = (Join-Path (Resolve-Path .).Path '.beads')
bd export -o .beads/issues.jsonl
```

On a **separate fresh clone** with no local database, retain the exported file; initialize using explicit `--remote=` and project-local environment, then `bd import -i .beads/issues.jsonl --json` (the installed CLI supports upsert import of export fields). Verify IDs, counts and dependencies before work. Do not run init over an active database or treat export/import as full history recovery.

JSONL preserves issue records, not Dolt branches, full commit history, working-set state or all non-issue tables. Supported full backup uses `bd backup init <destination>`, `bd backup sync`, `bd backup restore`; remote destination selection/credentials and roundtrip recovery remain an explicit future operational decision, not completed sync. GitHub repository creation alone does not synchronize Beads. The initial exported snapshot has been verified locally; a fresh-clone DB import/full-backup recovery has not yet been exercised.
