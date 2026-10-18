# Project-local Beads

Read [the project workflow](../docs/beads.md) before using this tracker.

**Do not run `bd init`, `bd dolt push`, `bd dolt pull` or bootstrap against this active tracker.** The installed CLI can inherit the home remote; ordinary Git pushes do not synchronize the embedded database. Any new initialization requires the audited explicit empty `--remote=` opt-out and independent isolation verification described in the workflow.

Before **every** Beads command, set process-local `BEADS_DIR` to this repository's canonical `.beads` path, from the verified project root. Do not use the home tracker or configure credentials here.

`.beads/issues.jsonl` is the supported versionable issue export, not a full Dolt backup. Refresh it after changes using the project workflow. Embedded database files and interaction logs are local operational state, not publication artifacts. No Dolt remote sync or full-backup recovery is configured or claimed.
