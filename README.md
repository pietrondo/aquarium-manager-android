# Aquarium Manager Android

An offline-first, multi-aquarium manager planned for **native Kotlin + Jetpack Compose + Material 3**, Clean Architecture, MVVM, repositories and Room.

**Current status:** approved domain/backup design, runnable Python asset tooling and Beads implementation plan. **There is no Android app scaffold yet:** no Gradle wrapper, APK, Room database or working screens. Do not interpret the commands below as a successful Android build.

## Navigation

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

### Planned Android foundation (not executable yet)

Install Android Studio with Android SDK/emulator and a compatible JDK. The foundation Beads task must select mutually compatible stable Kotlin, Android Gradle Plugin, Gradle, Compose/Material 3, Room and WorkManager versions from official docs, pin them in `gradle/libs.versions.toml`, check in a genuine Gradle wrapper and wrapper checksum, define namespace/applicationId and SDK levels, and document the selected toolchain. No versions or SDK floor are silently invented in this planning delivery.

Proposed modules: `:app`, `:domain` (Kotlin/JVM), `:data` (Android library). Keep dependency direction as above; export Room schemas and test migrations without destructive fallbacks. Once that task exists, its acceptance commands will be `./gradlew test lint assembleDebug` (Windows `gradlew.bat test lint assembleDebug`) and emulator instrumentation tests using the actual configured task names. Those commands **cannot run in this repository today**. CI must prove a fresh clone builds without secrets or network at application runtime.

## Work tracking and delivery

Use project-local Beads only; see its workflow document for isolation and export caveats. Pending app tasks remain open. The JSONL export is an issue interchange snapshot, not a full Dolt backup. Do not confuse a Git push with Beads database synchronization.

Repository: [pietrondo/aquarium-manager-android](https://github.com/pietrondo/aquarium-manager-android). Public source publication is authorized; the repository remains private until the fresh-review publication gate is completed. Work on `main`; do not deploy or merge into production without explicit authorization. Commits use conventional messages with no AI attribution.

## License and release disclosures

Copyright 2026 pietrondo. Original project source, documentation and configuration are licensed under the [Apache License, Version 2.0](LICENSE); see [NOTICE](NOTICE). The standard license is included unmodified. Its warranty disclaimer and liability limitations apply subject to applicable law and any written agreement; they are **not a promise of blanket legal immunity**. See sections 7–9 of the [official license](https://www.apache.org/licenses/LICENSE-2.0.txt) for the actual terms.

Third-party dependencies and artwork retain their own applicable terms. No PixelLab-generated artwork has been created or published in this delivery. Before publishing generated assets, verify the PixelLab terms applicable to the account, generation date and intended distribution; do not assume the source-code license grants rights to third-party output. The manifest and style file are candidate prompts, not a statement of artwork ownership or final branding approval.

Release acceptance in Beads includes an in-app license/third-party notices screen, a dependency and asset rights review, and accurate privacy/store disclosures based on the implemented application. Planned species and dosing information must be presented as informational, with limitations made clear: the app does not guarantee suitability, safety or outcomes. It must not invent biological advice or silently present data-entry validation as care recommendations. These product disclosures do not add restrictions to or modify Apache 2.0. Store publication and legal compliance review remain future work; Android Studio is a development/build tool, not a distribution store.
