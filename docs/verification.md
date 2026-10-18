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

## Android foundation follow-up

See [pinned Android foundation and actual build evidence](android-foundation.md). Final Windows clean `test lint assembleDebug` succeeded with AGP8.11.1/Gradle8.13/Kotlin2.2.21 in 2m23s (128 tasks), producing the debug APK. At the initial build checkpoint, Python had 12 passing tests (9 asset + 3 foundation). At the Action-pin review checkpoint: **13 passing tests** (9 asset + 4 foundation), after a review-found Action-pin regression was observed RED then GREEN. The Gradle Action reference was a nested annotated tag, not a commit; official GitHub tag objects were recursively peeled and the workflow now pins verified commit `ed408507eac070d1f99cc633dbcf757c94c7933a` (v4.4.3). Other Action SHAs were verified as commits. Lint reports 0 errors and 11 documented warnings, not a warning-free result. At this local checkpoint, no Kotlin tests, emulator, visual QA, hosted CI or isolated fresh-clone build was claimed. See the subsequent hosted result below.

Independent reviewer evidence: a separate source-copy build completed successfully in **1m13s**. This was **not a fresh Git clone or a cold-cache build**; it independently checked the candidate source but does not replace hosted-CI/fresh-clone acceptance. Hosted CI was pending at that checkpoint; the successful subsequent run below completes foundation acceptance.

### Hosted SDK command-path failure and pending fix verification

[Hosted run 39168546461](https://github.com/pietrondo/aquarium-manager-android/actions/runs/39168546461) for commit `5158e43f49ddd02f5689137702e50d93c370cfc4` failed with exit 127 at SDK installation: `sdkmanager: command not found`. Checkout, JDK and Gradle setup succeeded; Python and Gradle build steps were skipped. This is failed CI evidence, not an Android compilation failure or a green fresh-checkout result.

A fresh audit of the exact hosted runner image `20261012.334.1` identified an installed Android SDK and exported `ANDROID_HOME`, but command-line tools were not on PATH. The minimal correction resolves `cmdline-tools/latest/bin/sdkmanager` under `ANDROID_HOME`, checks executable availability and quotes the absolute path. No extra setup action, installation workaround or license-consent command was added.

Latest local Python suite: **14 passing tests** (9 asset + 5 foundation). The behavioral regression extracts the actual workflow shell block, runs it with Bash against a temporary SDK path containing spaces, removes SDK tools from PATH, and verifies the stub receives exactly the two pinned package arguments. It reproduced exit 127 before the correction and passed afterward. A hosted rerun was required at that checkpoint; it subsequently passed as recorded below. Existing local Android build evidence is unchanged; no cold-cache claim is made.

### Hosted fresh-checkout acceptance — passed

[Android foundation run 39168818925](https://github.com/pietrondo/aquarium-manager-android/actions/runs/39168818925) succeeded for exact commit `78f813da05e14045fe458162848674fcb5be4025` on hosted Ubuntu 24.04. Logs verify a fresh checkout/fetch of that commit, successful explicit SDK command-path resolution, **14 passing Python tests**, and `bash ./gradlew --no-daemon test lint assembleDebug`: **BUILD SUCCESSFUL in 3m3s**, **125 tasks executed**. The complete verification job took **3m42s**. No application/service credentials were configured or required; the workflow uses contents-read permissions.

This is fresh-checkout hosted-Linux build acceptance, **not cold-cache evidence**: setup-gradle restored a cache. Gradle Kotlin unit-test tasks remain NO-SOURCE; no emulator, device, visual, accessibility or feature acceptance is implied. The runner emitted non-failing Node20/action-generation deprecation notices; future action upgrades should be separately verified rather than silently changing the reviewed toolchain.

`aq-7zd.1` is closed against this actual hosted result plus the reviewed local foundation evidence. Remaining product/schema/UI/backup/device work stays in its own open tasks.
