# Verification evidence

2026-10-18, Windows / Python 3.13.12 with installed Pillow 11.3.0. The requirements support Python 3.11+; other Python versions have not been run in this session.

| Command | Observed result |
|---|---|
| `python -m unittest discover -s tests -v` before implementation | Expected RED: missing asset tool, 1 failure / 7 skipped guards |
| Same command after implementation | GREEN: 9 tests pass, no network; mock image/HTTP interactions |
| `python -m py_compile scripts/assets.py tests/test_assets.py` | Pass |
| `python scripts/assets.py generate --dry-run` | 10 planned assets; no credentials, network, originals or generated output |

Tests verify all legacy launcher/UI density sizes plus store 512, nearest-neighbor palette preservation, aspect rejection, cache and dry-run behavior, manifest duplicate/path rejection, exact v2 HTTP shape and timeout, HTTP 402 and malformed response errors. Redirect regression was observed RED (both cross-origin HTTPS and HTTPS-to-HTTP reached a second mocked transport), then GREEN after installing a reject-all redirect handler. The regression exercises the real urllib opener/error chain with mocked HTTP/HTTPS transports; no external network or paid request. Only the original endpoint receives Authorization, and redirect status errors are sanitized. This is focused script evidence, not Android app, instrumented-device, live PixelLab or visual-art acceptance.

Local git initialized inside the project on `main`; parent/home repository is not the project. Private remote `pietrondo/aquarium-manager-android` created and verified with `gh repo view ... --json nameWithOwner,isPrivate,url`; no commit/push before fresh root review. There was no existing project graphify graph to update; parent/home was not indexed.

## Beads initialization incident

`bd 1.1.2` initialization inherited a home `sync.remote` even with process-local `BEADS_DIR`, cloning foreign issues into the newly created local tracker. No issue create/update/close command was run against that clone. Original clone was moved, using checked project-contained absolute paths, to an ignored `.beads-quarantine-isolation/`. A first approved recovery using fresh `dolt.local-only: true` still inherited the remote. Further tracker writes stopped for independent isolation audit. Do not export or commit foreign issues. Source-audited recovery with explicit empty `--remote=` succeeded; independent audit verified fresh project_id `141cacd3-d72f-4d12-8707-27a2f8adbc1e`, zero initial issues and no remotes. Created 6 epics + 24 tasks with dependency links; only asset tooling is closed. Supported JSONL export contains 30 project issues. Remote Dolt sync/full backup remains unconfigured; do not run inherited sync commands. The init command auto-committed metadata/ignore files; no manual commit/push has occurred.
