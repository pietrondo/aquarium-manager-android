# Room schema v2 and non-destructive migrations

Date: 2026-10-19. Status: **approved** for `aq-7u7.1` and `aq-7u7.2`.

## Context

`aquarium-pit-manage` shipped schema v1 with only the `aquariums` table (`data/schemas/com.pietrondo.aquariummanager.data.AquariumDatabase/1.json`). The full 10-table domain model in `docs/decisions/2026-10-18-library-and-backup-design.md` requires settled storage encodings, foreign-key deletion policies, canonical-unit validation rules, indexes, and a non-destructive `1 -> 2` Room migration.

## Settled schema v2 decisions

1. **Non-destructive migration (`1 -> 2`)**:
   - The v1 `aquariums` table schema is preserved untouched.
   - `AquariumDatabase` is upgraded to `version = 2` with `exportSchema = true`, exporting `2.json` alongside `1.json`.
   - `AquariumDatabase.MIGRATION_1_2` (`ALL_MIGRATIONS`) creates the 9 remaining tables and their indexes non-destructively.
   - `fallbackToDestructiveMigration*` is never enabled; opening an unsupported schema version fails closed without wiping user tables.

2. **Canonical storage encodings (`TEXT`)**:
   - Primary and foreign keys: canonical lowercase hyphenated UUID `TEXT` (`UUID.toString()`).
   - Decimal values (`BigDecimal`): canonical plain text (`stripTrailingZeros().toPlainString()`), avoiding `REAL`/`DOUBLE` binary floating-point rounding.
   - Timestamps (`Instant`): ISO-8601 UTC `TEXT` (`Instant.toString()` / `Instant.parse()`).
   - Dates (`LocalDate`): ISO-8601 `YYYY-MM-DD` `TEXT` (`LocalDate.toString()` / `LocalDate.parse()`).
   - Time zones (`ZoneId`): validated IANA `TEXT` (`ZoneId.id` / `ZoneId.of()`).
   - Enums (`SpeciesKind`, `DosingFrequency`): exact enum name `TEXT` (`FAUNA`, `FLORA`, `DAILY`, `WEEKLY`).
   - Dosing weekdays (`Set<DayOfWeek>`): canonical comma-separated sorted ISO day numbers `1..7` (for example `"1,3,5"`, or `""` when empty).

3. **Foreign-key and deletion semantics**:
   - Parent references to `aquariums`, `aquarium_profiles`, `products`, and `species` use `ForeignKey.RESTRICT` so referenced catalog or aquarium rows cannot be silently deleted without explicit orchestration.
   - `dosing_events.planId` references `dosing_plans(id)` with `ForeignKey.SET_NULL` so deleting a dosing plan preserves historical dosing event snapshots (`productNameSnapshot`, `dose`, `unit`, `occurredAt`) with `planId = null`.
   - Deleting a row from `aquarium_profile_links` removes only the profile association for that aquarium.

4. **Canonical units and validation boundaries**:
   - `measurements`: null means unmeasured (never zero-by-default). Every measurement row requires at least one non-null parameter reading among `ph`, `khDkh`, `ghDgh`, `no2MgL`, `no3MgL`, `tdsPpm`, and `temperatureC`. `khDkh`, `ghDgh`, `no2MgL`, `no3MgL`, and `tdsPpm` must be `>= 0` when present. `ph` and `temperatureC` accept any finite `BigDecimal` without hardcoded biological safe ranges.
   - `maintenance_events`: `type` must be non-blank; `changedLitres` must be `> 0` when present.
   - `dosing_plans`: `dose > 0`, `unit` non-blank, `WEEKLY` plans require at least one weekday in `weekdays`, and `endDate >= startDate` when `endDate` is present.
   - `dosing_events`: `productNameSnapshot` non-blank, `dose > 0`, `unit` non-blank.
   - `species`: `kind` (`FAUNA` or `FLORA`), `commonName` required non-blank, `scientificName` optional (`null` or non-blank).
   - `stock_entries`: `quantity` is nullable `BigDecimal?` (`> 0` when known, `null` when unknown, supporting both integer fauna counts and fractional plant quantities); `exitedAt >= introducedAt` when `exitedAt` is present.
