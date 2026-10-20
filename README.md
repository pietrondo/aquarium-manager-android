# Aquarium Manager Android

An offline-first, multi-aquarium manager planned for **native Kotlin + Jetpack Compose + Material 3**, Clean Architecture, MVVM, repositories and Room.

**Current status:** native Compose/Material 3 multi-aquarium CRUD and overlapping profile management backed by real Room schema v2, versioned external JSON catalog seeding (`default_catalog_v1.json`) that never overwrites user-edited Room records, lifecycle MVVM, and `:app`, `:domain`, `:data` boundaries. See [schema v2 and migrations](docs/decisions/2026-10-19-room-schema-v2-and-migrations.md), the [initial slice](docs/decisions/2026-10-19-room-mvvm-slice.md), and [actual verification evidence](docs/verification.md).

## Navigation

- [Room schema v2 and non-destructive migrations](docs/decisions/2026-10-19-room-schema-v2-and-migrations.md)
- [Read-only list and Room core schema](docs/decisions/2026-10-19-room-mvvm-slice.md)
- [Ordered schema, ERD and full-library backup architecture](docs/decisions/2026-10-18-library-and-backup-design.md)
- [PixelLab API contract, sizing and verification](docs/assets.md)
- [Beads workflow and persistence](docs/beads.md)
- [Verification evidence](docs/verification.md)
- [Asset manifest](assets/manifest.json)

## Product

Manage several aquariums with overlapping profiles, optional water readings, maintenance history, fauna/flora stock and product dosing. Unknown readings/quantities stay unknown, not zero. Dosing events retain a product-name/dose snapshot. Biological guidance is not an arbitrary hardcoded safe-range validator. Content catalogs are external versioned data.

Bottom navigation direction: **Aquariums, Tests, Maintenance, Livestock, Settings**. A proposed navy/teal/aqua/coral pixel-art visual direction is represented in the manifest and prompts, not approved final branding. Accessibility labels, non-color status cues, Material touch targets, contrast, scalable text and device-size rendering are required future acceptance criteria.

## Architecture

```text
presentation (Compose, ViewModels, UI state)
       | depends on
       v
domain (plain Kotlin models, use cases, repository interfaces)
       ^ implemented by
       |
data (Room entities, DAOs, repository implementations, JSON backup DTO mappings)
```

The app composition root wires dependencies. Domain has no Android dependencies. Room is the source of truth, changes flow to ViewModels; UI never directly accesses DAOs. Multi-table writes use transactions. Full-library ZIP export/replace-restore is local, unencrypted and explicitly confirmed; automatic destination grants and scheduler state are device-local. See the design for consistency, checksum limitations and rollback protocol.

## Development setup

### Runnable now: artwork tooling

Python 3.11+ and Pillow are required. From this repository:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
python scripts/assets.py generate --dry-run
```

Dry-run is the default and performs no network calls or writes. To explicitly spend PixelLab credits, obtain a PixelLab key from your own account and set it in the current development process (not a committed file):

```powershell
$env:PIXELLAB_API_KEY = '<your-development-key>'
python scripts/assets.py generate --generate
Remove-Item Env:PIXELLAB_API_KEY
python scripts/assets.py resize
```

Existing originals are cached and never regenerated automatically. Do not retry an uncertain paid request blindly. Generated PNGs remain local/ignored until visual review. Generated resource folders are staging output, **not** a working Android module. No Google API key, OAuth client, Google Cloud project or Google API setup is needed. PixelLab credentials must never enter Android resources, BuildConfig, an APK, logs, commits or backups.

### Android foundation

Use JDK 21 (Java/Kotlin bytecode 17), Android SDK platform 36 and build-tools 35.0.0. Open the root in Android Studio, select the same Gradle JDK, and set `ANDROID_HOME` or an ignored `local.properties` with `sdk.dir`. Install SDK packages and review their licenses through Android Studio; do not commit machine paths.

```powershell
.\gradlew.bat --no-daemon test lint assembleDebug
```

Linux/macOS: `bash ./gradlew --no-daemon test lint assembleDebug`. The first build downloads Gradle and Maven dependencies; the APK has no network permission or service credentials. Debug output: `app/build/outputs/apk/debug/app-debug.apk`. [Pinned versions, provenance, emulator setup and validation limits](docs/android-foundation.md).

The modules implement `presentation -> domain <- data`, with only the application composition root constructing Room/data dependencies. Domain is plain Kotlin/JVM. Room version 1 contains the complete approved aquariums core table (UUID, name, optional exact decimal litres, notes, UTC creation instant); no other planned tables are present. Decimal litres use canonical TEXT, not lossy REAL. Schema JSON is exported under `data/schemas`; future schema changes require non-destructive tested migrations.

Kotlin JVM tests cover domain validation, exact mappings/converters, real Room/SQLite observable reads under Robolectric, and ViewModel loading/list/empty/safe-error/retry/cancellation behavior. Python tests provide focused architecture/configuration and asset-tooling guards, not full architecture proof. There are no instrumented tests yet. See the latest verification entry for executed counts, device smoke and remaining limits; historical foundation entries correctly retain their then-NO-SOURCE results.

## Work tracking and delivery

Use project-local Beads only; see its workflow document for isolation and export caveats. Pending app tasks remain open. The JSONL export is an issue interchange snapshot, not a full Dolt backup. Do not confuse a Git push with Beads database synchronization.

Repository: [pietrondo/aquarium-manager-android](https://github.com/pietrondo/aquarium-manager-android). Public source publication is authorized; source publication does not imply a store release. Work on `main`; do not deploy or merge into production without explicit authorization. Commits use conventional messages with no AI attribution.

## License and release disclosures

Copyright 2026 pietrondo. Original project source, documentation and configuration are licensed under the [Apache License, Version 2.0](LICENSE); see [NOTICE](NOTICE). The standard license is included unmodified. Its warranty disclaimer and liability limitations apply subject to applicable law and any written agreement; they are **not a promise of blanket legal immunity**. See sections 7–9 of the [official license](https://www.apache.org/licenses/LICENSE-2.0.txt) for the actual terms.

Third-party dependencies and artwork retain their own applicable terms. No PixelLab-generated artwork has been created or published in this delivery. Before publishing generated assets, verify the PixelLab terms applicable to the account, generation date and intended distribution; do not assume the source-code license grants rights to third-party output. The manifest and style file are candidate prompts, not a statement of artwork ownership or final branding approval.

Release acceptance in Beads includes an in-app license/third-party notices screen, a dependency and asset rights review, and accurate privacy/store disclosures based on the implemented application. Planned species and dosing information must be presented as informational, with limitations made clear: the app does not guarantee suitability, safety or outcomes. It must not invent biological advice or silently present data-entry validation as care recommendations. These product disclosures do not add restrictions to or modify Apache 2.0. Store publication and legal compliance review remain future work; Android Studio is a development/build tool, not a distribution store.
