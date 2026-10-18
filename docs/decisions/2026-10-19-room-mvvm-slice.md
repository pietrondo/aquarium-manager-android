# Read-only aquarium list and Room core schema

Date: 2026-10-19. Scope explicitly approved for `aq-7zd.2`.

## Scope and boundaries

Deliver the real chain `Compose -> ViewModel -> domain repository <- Room repository -> DAO -> SQLite`. A new installation has no aquariums. No demo records, create/edit/delete screens, profiles, other tables, backups, scheduler, service keys or runtime networking are included. The full aquariums core table is brought forward from `aq-7u7.1`; that task must expand version 1 through non-destructive migrations, not recreate the table.

The plain Kotlin domain exposes immutable `AquariumSummary(id, name)`, an observable repository contract and exact decimal volume validation. Presentation imports domain only, never Room, DAO or data implementations. `AquariumApplication` is the sole composition root; it owns one process-wide database/repository. `MainActivity` injects the repository through the lifecycle ViewModel factory. No DI framework or one-method use-case wrapper is needed for a read-only projection.

## Physical schema and precision

`aquariums`: required UUID `id` primary key, required nonblank `name`, nullable `netLitres` and `grossLitres`, nullable `notes`, required UTC `createdAt`. All physical columns are TEXT. UUIDs serialize as lowercase hyphenated `UUID.toString()`. Decimal litres serialize using `BigDecimal.stripTrailingZeros().toPlainString()`, never binary floating-point or SQLite REAL. Canonicalization drops insignificant trailing zeros, not numeric precision; display/input scale is not a separate data requirement. Null remains unknown. Supplied volumes must be positive, and net must not exceed gross when both are supplied. Entity construction (including Room reads and DAO writes) invokes domain validation; future import/write paths must reuse it. Raw SQL can bypass entity validation and is not a supported application write API.

Instants serialize through `Instant.toString()` as ISO-8601 UTC with a `Z` suffix and exact available nanoseconds. This is not device-local time or an SQLite LOCAL timestamp. Core-library desugaring supports java.time on API 23–25. No time ordering is currently queried; future text time-order indexes need a fixed-width format or explicit chronology conversion rather than assuming variable fractional text is lexicographically chronological.

DAO reads full entities ordered by SQLite binary `name ASC, id ASC` for deterministic ties. No locale/case-insensitive sort promise is made. Explicit data-layer mapping projects only id and name. Insert/update DAO methods support persistence testing and later approved write use cases; the application exposes no write UI. Schema version 1 is compiler-exported under `data/schemas/`. No destructive fallback is configured. A later database version needs a tested migration.

## Lifecycle and errors

The ViewModel starts Loading, then emits Success (including an empty list) or a generic Error object with no raw SQL/path messages. Cancellation is propagated. Retry cancels and joins the previous collection before starting another; collector lifetime belongs to the lifecycle ViewModel. Compose consumes StateFlow with `collectAsStateWithLifecycle`. Empty and error copy is in English resources. Empty state explains that creation is not yet available; there is no dead create action. Retry is an actual error-recovery action, not a placeholder.

## Dependency choices and primary provenance

Keep AGP 8.11.1, Gradle 8.13, Kotlin 2.2.21, SDK 36/min 23, JDK 21/bytecode 17 and the existing Compose baseline unchanged. These deliberately conservative choices do not claim the latest version of every library.

- [Room 2.8.5 release/setup](https://developer.android.com/jetpack/androidx/releases/room), including KSP and schema export.
- [Kotlin-matched KSP 2.2.21-2.0.4](https://github.com/google/ksp/releases/tag/2.2.21-2.0.4), avoiding an unnecessary Kotlin upgrade.
- [Lifecycle 2.9.4 releases](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.4), ViewModel factory and [lifecycle-aware Compose collection](https://developer.android.com/develop/ui/compose/state).
- [Coroutines 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.10.2) and [test dispatcher APIs](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/).
- [Robolectric 4.16.1](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.16.1) for real Room/SQLite JVM tests on SDK 36, not unexecuted instrumentation-only tests.
- [Core-library desugaring](https://developer.android.com/studio/write/java8-support) for Java APIs below API 26.

Context7 official Android/Kotlin/Robolectric documentation was resolved and queried before implementation. WorkManager remains a catalog reservation only.

## Verification contract

Run fake-repository ViewModel tests for loading, list, empty, safe error/retry, cancellation and non-overlapping collectors. Run exact decimal/UTC/UUID/domain-validation tests plus a real Room database empty/insertion/update/ordering/physical-TEXT test under Robolectric. The Python import/configuration guards are useful focused regressions, not complete static architecture assurance. Run `gradlew.bat --no-daemon test lint assembleDebug` and the Python suite. Actual results and native visual limits belong to `docs/verification.md`; build success alone does not certify device behavior, accessibility, release readiness or the future full schema.
