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

## Read-only Room/MVVM slice — 2026-10-19

Approved `aq-7zd.2` implements the real Room-backed aquarium list and brings the complete aquariums core table forward from `aq-7u7.1`. Fresh databases contain no seeded aquariums. Full schema, CRUD screens, backup and schedulers remain out of scope. See [physical storage and lifecycle decisions](decisions/2026-10-19-room-mvvm-slice.md).

### Test-first evidence

- Domain volume-validation assertions ran against the no-op validation skeleton: **4 tests, 2 expected assertion failures** (positivity and exact net/gross ordering).
- Data storage/Room assertions ran against the pre-validation/noncanonical converter skeleton: **8 tests, 4 expected assertion failures** (canonical decimal TEXT, physical stored decimal representation, UUID/name validation and entity volume validation). The other real Room empty/observable ordering tests executed, not skipped.
- A first mixed-snapshot RED batch also had app unresolved classpath/resource symbols after files were edited while Gradle was running. These compile errors are **not behavioral RED evidence**. A fresh static audit checked current aliases, seven string resources, manifest and composition root before a new invocation; stale configured inputs were suspected, not independently proven. Subsequent batches froze source through completion.
- A fresh consistent app-only RED command then compiled successfully: **5 ViewModel tests, 4 expected assertion failures** against the Loading-only skeleton. Cancellation initially remained Loading; list, empty, safe-error/retry and active collector assertions failed as intended.
- Python boundary and real composition guards were each observed failing before their corresponding implementation existed.

### Executed GREEN verification

Windows with the existing Temurin 21 runtime and existing SDK36/build-tools35.0.0, process-local `ANDROID_HOME`. No system installations, SDK image downloads, license acceptance, paid requests, Google APIs or application/service secrets.

`gradlew.bat --no-daemon test lint assembleDebug`: **BUILD SUCCESSFUL in 3m8s**, **169 actionable tasks (119 executed, 50 up-to-date)**. Actual JUnit XML reports show **17 unique Kotlin tests / 30 variant executions**, zero failures/errors/skips: domain4 once, ViewModel5 in both debug/release, storage5 and real Room3 in both debug/release. Robolectric4.16.1 exercised Room2.8.5/SQLite on SDK36, including observed insert/update changes and stable name/UUID ordering, exact decimal and UTC persistence. This is real JVM testing, not NO-SOURCE or unexecuted instrumentation acceptance.

`python -m unittest discover -s tests -v`: **16 passed** (nine asset tests and seven Android/configuration guards). Source import guards are focused regression protection, not a complete architectural proof.

App lint: **0 errors / 17 warnings** (15 newer dependency/tool notices plus the existing extraction-rule/OEM-transfer caveat and missing final launcher icon). Data lint: **no issues**. No warning suppression/baseline was added. Native `libandroidx.graphics.path.so` remains packaged unstripped, as in the original foundation; assembly succeeded.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, **12,404,588 bytes**. `aapt dump permissions` lists only AndroidX's app-specific dynamic-receiver permission, **no INTERNET**. The source manifest declares no permissions. Compiler-exported `data/schemas/com.pietrondo.aquariummanager.data.AquariumDatabase/1.json` contains exactly the aquariums table and canonical TEXT affinities. There is no destructive migration fallback.

### Native smoke and visual limit

Device inventory initially showed no connected device and one already-installed AVD, `Medium_Phone_API_36.1`. Started that existing AVD headlessly (`-no-window -no-audio -no-boot-anim -no-snapshot-save`), with an owned hidden launcher process; no wipe, image download or license change. It booted as API36/Android16. Before installation, package query found no existing `com.pietrondo.aquariummanager`. APK installation succeeded and cold activity launch reported `Status: ok`; the focused AndroidRuntime error log was empty.

The initial pulled screenshot was inspected with `view_image`: the title, honest empty message and creation-unavailable text rendered, but a **System UI is not responding** dialog covered the lower screen. A fresh native incident audit established that its retained ANR timestamp preceded the aquarium activity launch by approximately seven minutes. The dialog belonged to System UI, not an app error. The auditor used the observed **Wait** action, then captured and visually inspected clean and stable **1080x2400** screenshots on this owned API36.1/headless AVD. The empty rendering now **passes this bounded native smoke**: title and both empty-state messages are visible, with no fake aquariums or dead create action. UI hierarchy independently records the application content. No repeated ANR or app crash appeared in the retained logs. The auditor stopped only the owned emulator; no data wipe, system-image download or unrelated process termination was used.

Local ignored evidence: `.toolchain/room-empty-api36.png` (historical overlay), `.toolchain/room-audit-clean.png`, `.toolchain/room-audit-stable.png`, `.toolchain/room-audit-after.xml`, emulator/audit logs and build/test logs. This is empty-state rendering acceptance only, **not performance acceptance**: the observed cold launch recorded `Displayed`/launch time **11.814s** and **316 skipped frames** on this emulator. Startup/frame timing must be measured on a stable representative device before release; the observation alone does not establish its app-versus-emulator cause. Follow-up is recorded in existing release/device acceptance task `aq-ct8.4`, not a duplicate issue. Populated/error/retry behavior is proven by JVM tests, not native rendered acceptance. Loading, TalkBack, large text, other sizes, API23 and release/store acceptance are not claimed.

Fresh independent source review passed with no P1/P2 findings; its documentation mojibake findings were mechanically corrected with explicit UTF-8 reads/writes, and all changed Markdown was scanned for remaining corruption markers. Hosted CI for this exact slice was still pending at this local/native checkpoint; the successful exact-commit result below subsequently completed that gate. Prior foundation hosted results above do not certify these changes.

### Hosted Room/MVVM acceptance — passed

[Run 39172244427](https://github.com/pietrondo/aquarium-manager-android/actions/runs/39172244427) passed for exact main commit `7d896f767e09b304f10dcc6c1ae2216ecfcc018f` on hosted Ubuntu 24.04 (2026-10-18 UTC / 2026-10-19 Europe/Rome). The verify job completed in **3m43s**. Logs confirm checkout/fetch of that exact commit, **16 passing Python tests**, and `bash ./gradlew --no-daemon test lint assembleDebug`: **BUILD SUCCESSFUL in 2m52s**, **169 actionable tasks / 169 executed**.

The hosted logs show actual execution of all five Kotlin test tasks: `:domain:test`, app debug/release unit tests and data debug/release unit tests, with no NO-SOURCE, FROM-CACHE or skipped marker on those tasks. The verified unchanged suite contains **17 unique Kotlin tests / 30 variant executions** (domain 4 once, app 5 twice, data 8 twice). Exact numerical JUnit counts were independently inspected in the local XML reports above; this workflow does not publish hosted JUnit XML artifacts, so no remote-report download/count inspection is claimed. Hosted success establishes the full suite/build/lint/APK command passed for the reviewed source, not additional native or performance coverage.

This is a fresh-checkout hosted Linux verification, **not a cold-cache claim**: setup-gradle restored the Gradle home/dependency/wrapper cache. All 169 tasks still executed. No application/service credentials or Google API setup were needed. Non-failing Node20/action-generation and setup-java v4 deprecation notices remain future CI maintenance, not an excuse to change the reviewed toolchain during this delivery.

`aq-7zd.2` is closed against this exact hosted result plus fresh source review and the bounded native empty-state rendering audit. Other schema/CRUD/backup/UI/release tasks and the startup-performance follow-up in `aq-ct8.4` remain open. Foundation or other epics are not automatically closed by this slice.

## External catalogs and multi-aquarium CRUD (`aq-7u7.3` & `aq-7u7.4`) — 2026-10-19

Implemented versioned external JSON catalog loading/seeding (`aq-7u7.3`) and multi-aquarium CRUD with overlapping profile labels (`aq-7u7.4`):

- **RED -> GREEN TDD counts**:
  - `:domain:test`: **14 tests, 3 failed (RED)** in `CatalogAndAquariumDraftTest` -> **14 passed, 0 failed (GREEN)**.
  - `:data:testDebugUnitTest`: **20 tests, 5 failed (RED)** in `CatalogAndCrudRepositoryTest` -> **20 passed, 0 failed (GREEN)**.
  - `:app:testDebugUnitTest`: **9 tests, 4 failed (RED)** in `AquariumListViewModelTest` -> **9 passed, 0 failed (GREEN)**.
- **Full verification**:
  - `python -m unittest discover -s tests -v`: **16 passed**.
  - `gradlew.bat --no-daemon test lint assembleDebug`: **BUILD SUCCESSFUL** across `:domain`, `:data` (debug + release unit tests, lint, AAR), and `:app` (debug + release unit tests, lint, `app-debug.apk`), totaling **43 unique Kotlin unit tests (72 variant executions)** with 0 failures.

## Water parameter readings, parameter trends, and maintenance logs (`aq-e7g.1`, `aq-e7g.2`, `aq-e7g.3`) — 2026-10-19

Implemented optional water parameter recording and timestamped history (`aq-e7g.1`), parameter trend calculation and native Compose sparklines with explicit missing-value gaps and non-color direction cues without fabricated safe bands (`aq-e7g.2`), and typed maintenance logging with optional changed water volume and non-blocking advisory warning when changed litres exceed aquarium volume (`aq-e7g.3`):

- **RED -> GREEN TDD counts**:
  - `:domain:test`: **18 tests, 4 failed (RED)** in `ReadingsTrendsAndMaintenanceDomainTest` -> **18 passed, 0 failed (GREEN)**.
  - `:data:testDebugUnitTest`: **22 tests, 2 failed (RED)** in `MeasurementsAndMaintenanceRepositoryTest` -> **22 passed, 0 failed (GREEN)**.
  - `:app:testDebugUnitTest`: **11 tests, 2 failed (RED)** in `AquariumListViewModelTest` -> **11 passed, 0 failed (GREEN)**.
- **Full verification**:
  - `python -m unittest discover -s tests -v`: **16 passed**.
  - `gradlew.bat --no-daemon test lint assembleDebug`: **BUILD SUCCESSFUL** across `:domain`, `:data` (debug + release unit tests, lint, AAR), and `:app` (debug + release unit tests, lint, `app-debug.apk`), totaling **51 unique Kotlin unit tests (84 variant executions)** with 0 failures.

## Livestock and dosing (`aq-adw.1`, `aq-adw.2`, `aq-adw.3`, `aq-adw.4`, `aq-adw.5`) — 2026-10-19

Implemented fauna/flora species catalog editing with case-insensitive duplicate prevention, in-place rename propagation, and referenced-deletion protection (`aq-adw.1`), per-aquarium stock entries with unknown (`null`) vs positive quantities, introduction/exit timestamp validation, active/exited visibility, and 1-tap exit marking (`aq-adw.2`), shared product library and `mL`/`g`/`drops`/`tabs` dose-unit validation without cross-dimension conversion (`aq-adw.3`), daily and weekly dosing recurrence with explicit IANA timezones covering DST transitions, timezone travel, leap dates, and date-window validation (`aq-adw.4`), and transactional immutable dosing snapshots from plans or ad-hoc entries that survive subsequent product renames and plan edits/deletions (`aq-adw.5`):

- **RED -> GREEN TDD counts**:
  - `:domain:test`: **23 tests, 5 failed (RED)** in `LivestockAndDosingDomainTest` -> **23 passed, 0 failed (GREEN)**.
  - `:data:testDebugUnitTest`: **25 tests, 3 failed (RED)** in `LivestockAndDosingRepositoryTest` -> **25 passed, 0 failed (GREEN)**.
  - `:app:testDebugUnitTest`: **13 tests, 2 failed (RED)** in `AquariumListViewModelTest` -> **13 passed, 0 failed (GREEN)**.
- **Full verification**:
  - `python -m unittest discover -s tests -v`: **16 passed**.
  - `gradlew.bat --no-daemon test lint assembleDebug`: **BUILD SUCCESSFUL** across `:domain`, `:data` (debug + release unit tests, lint, AAR), and `:app` (debug + release unit tests, lint, `app-debug.apk`), totaling **61 unique Kotlin unit tests (99 variant executions)** with 0 failures.

## Safe offline library backups (`aq-d34.1`–`aq-d34.4`) — 2026-10-23

Full-library ZIP backup/restore is implemented in `LibraryBackupCodec`, `RoomAquariumRepository` and `SafBackupDocumentGateway`, with the Compose settings surface wired to SAF create/open/tree pickers:

- **Versioned DTOs and fixtures (`aq-d34.1`)**: exact-key `library.json`/`manifest.json` parsing, canonical UUID/Instant/IANA-zone/`BigDecimal` text, bounded text and row counts, duplicate-ID, duplicate-name, duplicate-link and orphan-FK rejection, unknown maintenance-type/dose-unit/enum rejection plus a `formatVersion = 0` migration fixture. The golden fixture covers all ten tables, nulls and Unicode names, and asserts that device-local `treeUri`/generation markers are never exported.
- **Verified export (`aq-d34.2`)**: one Room read transaction, codec work on the injected background dispatcher, SHA-256 of the exact `library.json` bytes, local ZIP reopen/verify, and destination readback. A concurrent edit/export test asserts the snapshot stays coherent; failure/corruption tests assert the previous valid destination is untouched.
- **Hostile parser (`aq-d34.3`)**: allowlist of exactly two entries, traversal/subdirectory/duplicate/extra/missing entry rejection, size and 100:1 expansion ceilings, hash and count mismatch rejection, and — added in this session — explicit rejection of unsupported ZIP compression methods and encrypted entries. Every case asserts the live Room library is unchanged.
- **Transactional replace (`aq-d34.4`)**: explicit `confirmReplace`, exclusive writer gate, verified safety snapshot written outside the replacement transaction, then all ten tables replaced in one Room `@Transaction`. Tests cover missing confirmation, safety-backup failure aborting before mutation, safety-backup promotion failure leaving the previous safety copy intact, injected mid-import failure rolling back every table, semantic roundtrip hashes, and preservation of device-local schedule settings. A process kill cannot commit a partial replace because the whole replace is one SQLite transaction; no kill-fixture test exists.

Executed on Windows with the existing Temurin 21 runtime and existing SDK 36/build-tools 35.0.0 (`ANDROID_HOME` set for the process only):

| Command | Observed result |
|---|---|
| `python -m unittest discover -s tests -v` | **16 passed** |
| `.\gradlew.bat --no-daemon test lint assembleDebug` | **BUILD SUCCESSFUL in 2m 5s**, **169 actionable tasks (7 executed, 162 up-to-date)**, incremental rather than clean build |

Actual JUnit XML reports: **87 unique Kotlin tests / 142 variant executions**, zero failures, zero errors, zero skipped — `:domain:test` 32, app debug/release 15 each, data debug/release 40 each. Lint: **0 errors / 18 warnings** (dependency-version notices plus `PluralsCandidate` for the retention string, `DataExtractionRules` and `MissingApplicationIcon`); no baseline or suppression was added. Debug APK: **17,128,902 bytes**. `aapt dump permissions` now lists WorkManager's merged `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` permissions plus the app-specific dynamic-receiver permission; **`INTERNET` is still absent**, so the offline/no-account claim holds, but `ACCESS_NETWORK_STATE` should be reflected in future privacy copy.

`aq-d34.1`–`aq-d34.4` are closed against this evidence. `aq-d34.5` stays open because its acceptance requires **instrumented** SAF tests (cancelled picker, revoked grant, unavailable provider, out-of-space, partial write), and this project still has no `androidTest` source or device run. `aq-d34.6` stays open because Doze/delay behavior is owned by WorkManager and has not been exercised on a device; JVM tests do cover repository restart, revoked permission, failed-generation non-retention and app-owned-only pruning. No emulator, instrumented, SAF-provider or device acceptance is claimed here.

Hosted CI [run 39924097531](https://github.com/pietrondo/aquarium-manager-android/actions/runs/39924097531) passed for exact code commit `b10d13f88c5d738121a9b6cd9f1f41c820b467e8` on hosted Ubuntu 24.04: the `verify` job succeeded in 4m 37s, running the 16 Python tests and `bash ./gradlew --no-daemon test lint assembleDebug`. This is fresh-checkout hosted verification with a restored Gradle cache, not a cold-cache claim, and it does not add device or SAF coverage.

## Generated icon integration — 2026-10-23

Ten PixelLab originals (48×48, explicit paid generation, no automatic retries) were resized with the existing tooling and copied from `generated/android-res/` into `app/src/main/res/`. The nine UI PNGs are wired as: five bottom-navigation icons, four tab-header icons (`ic_temperature`, `ic_water_change` included) and two `FilterChip` leading icons (`ic_dose`, `ic_plant`). They render through a small `PixelIcon` composable with `contentDescription = null` because every slot already has a visible text label, and no Material tint is applied so the multi-colour art stays intact. The legacy launcher PNG family is now the manifest `android:icon`.

Verification: `.\gradlew.bat --no-daemon test lint assembleDebug` → **BUILD SUCCESSFUL in 3m 2s**, 169 tasks (22 executed, 147 up-to-date); lint **0 errors / 17 warnings** (the former `MissingApplicationIcon` warning is gone; no adaptive/monochrome warning is emitted, so `aq-ct8.2` stays open on its own criteria). Debug APK **17,133,062 bytes**; `aapt dump badging` resolves `application-icon` at mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi and `application: icon='res/mipmap-mdpi-v4/ic_launcher.png'`.

Bounded native smoke on the existing headless `Medium_Phone_API_36.1` AVD (API 36): install succeeded, `MainActivity` resumed, the system splash showed the launcher icon, and a 1080×2400 screenshot shows all five navigation icons, the Aquariums header icon and the empty state. The known System UI ANR dialog appeared first and was dismissed with its own **Wait** action, as in the earlier empty-state audit. Observed limits: `ic_aquariums` is hard to read at 24 dp on the selected pill, `ic_maintenance` reads more like seaweed than a brush, and header icons drawn at 32 dp are upscaled from the 24 dp master. Adaptive foreground/background, monochrome/themed icon, circle/squircle QA and store-512 review remain `aq-ct8.2` work; generated originals stay local/ignored and no PixelLab artwork has been pushed.

### Follow-up fixes and adaptive icon — 2026-10-23

The three observed defects were fixed and `aq-ct8.2` resources were implemented:

- `resize` now trims each original's transparent padding and fits the art to the target box, so every icon uses its full 24 dp instead of a small subject in a mostly empty canvas. The regression assertion in `tests/test_assets.py` also checks the emitted adaptive foreground sizes and transparent corners.
- `ic_aquariums` and `ic_maintenance` were regenerated with sharper prompts (big fish in a framed tank; vertical brush with handle and bristles). Both are legible at 24 dp in the device screenshot; the aquarium no longer reads as a coloured pill and the brush no longer reads as seaweed.
- Header icons now render at 24 dp (native master) instead of upscaling to 32 dp, so they are crisp at every density.
- Adaptive launcher: `mipmap-anydpi-v26/ic_launcher.xml` with a navy `@color/ic_launcher_background` and a 48 dp art foreground on a 108 dp canvas; `mipmap-anydpi-v33/ic_launcher.xml` adds the `<monochrome>` layer backed by the new `drawable/ic_launcher_monochrome.xml` vector (tank outline, waterline, fish silhouette). Circle, squircle and rounded-square previews were rendered from the real 432 px foreground and inspected: the navy background merges with the tank frame, so masked corners clip cleanly. The store 512 icon is now a fit of the trimmed art rather than a 48→512 nearest upscale.

Verification after the fixes: `.\gradlew.bat --no-daemon test lint assembleDebug` → **BUILD SUCCESSFUL in 2m 56s**, **142 test executions**, **0 failures**; Python **16 passed**; lint **0 errors / 18 warnings**. The extra warning is `MonochromeLauncherIcon` on the v26 icon, which is expected because the `<monochrome>` tag is deliberately declared only in the `anydpi-v33` variant; it was not suppressed. Debug APK **17,133,774 bytes**. Clean device screenshot on the same AVD confirms the larger, crisper navigation and header icons and the new adaptive launcher in the system splash. Themed-icon rendering on API 33+ and user visual approval are still not claimed, so `aq-ct8.2` stays open.

Hosted CI [run 39935098960](https://github.com/pietrondo/aquarium-manager-android/actions/runs/39935098960) passed for exact commit `d8c5e2b78fe6dd510e5cc1eb2f811dc0255fd22c` on hosted Ubuntu 24.04; the `verify` job succeeded in 3m 55s with the same Python and Gradle `test lint assembleDebug` steps. Fresh checkout, restored Gradle cache, no device or themed-icon coverage.
