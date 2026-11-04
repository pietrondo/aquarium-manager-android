# Species search, subspecies, NFC quick actions and breeding status — 2026-11-04

## Problem

Four requests from the project owner, all on the same surface:

1. When adding fauna, the species catalog is one wall of chips (85+ rows, now 103): the
   list is too long to use. It needs a search.
2. The catalog must also carry **subspecies with their Latin name**, e.g. betta has many
   of them.
3. A **new NFC tag** should be able to create an aquarium and stay bound to it.
4. Touching a bound tag must identify the aquarium and offer the actions you actually do
   at the tank: animals, plants, water test, food/dosing, maintenance, edit.
5. Aquariums need a **breeding status**: are the animals breeding, since when, and any
   particular note.

## Decision

### Species search and subspecies

- The catalog JSON gains no new schema. A subspecies is a normal catalog row whose
  **scientific name is a trinomial** (`Trigonostigma heteromorpha sumatrana`) or a
  cultivar (`Betta splendens 'Plakat'`, `Apistogramma cacatuoides 'Double Red'`).
  The parent is derived from the first two words of the scientific name, which is
  exactly the taxonomic meaning of a trinomial, so the relation needs no column, no
  migration and no backup change.
- `SpeciesCatalogGrouping` groups the catalog: a species, then its subspecies and
  varieties indented under it. A row whose parent is missing stays visible on its own,
  so a search never hides a match.
- `SpeciesSearch` matches common name **and** scientific name, case- and
  accent-insensitive. Searching `betta`, `splendens` or `plakat` all reach the bettas.
- The catalog seed is additive (`insertSpeciesIgnore` by id), so the new rows reach
  installed devices on the next start without touching user-edited rows. New ids live in
  the `...0004xx` range to keep the ids of the existing catalog free.

### NFC

- `NfcStatus.UnknownTag` keeps the scanned tag: the card offers *Create an aquarium with
  this tag*, which opens the ordinary aquarium editor with the tag pending. Saving binds
  the tag inside the same flow (`AquariumEditorState.nfcTagToBind`). The NFC card is also
  rendered on the empty state, so the very first aquarium can be created from a sticker.
- A scan of a bound tag now identifies the aquarium **and** opens its quick actions, not
  only the armed *Identifica acquario* flow. The reader reports a resting tag every two
  seconds, so the identified tag id is remembered: dismissing the sheet keeps it closed
  until another tag is scanned or identification is armed again.
- The quick actions run on the identified aquarium: it is selected first, then the
  matching tab/section opens. Animals and plants open the stock editor filtered to that
  kind; the water test opens the measurement editor; food opens the dosing section with
  the ad-hoc dose editor; maintenance opens its editor.

### Breeding status

- `aquariums` gains `breedingSince` (null = not breeding) and `breedingNotes`, Room
  **v5** via `MIGRATION_4_5` (`ALTER TABLE ... ADD COLUMN`). One column already answers
  the question: a non-null date means breeding, and it carries *since when*.
- The backup format goes to **version 3**: the two optional keys travel with each
  aquarium, and v0/v1/v2 archives are migrated by adding them as null, so old backups
  still restore.
- The repository normalizes: breeding notes are trimmed and dropped while the breeding
  period is closed; the domain refuses notes without a start date so a note can never be
  invisible.

## Alternatives considered

**A parent column for subspecies.** It would need a Room migration, a backup format
change and a parent id on every user-created species. The trinomial already carries the
relation, so the column would only duplicate the Latin name.

**Write the subspecies into the aquarium notes.** The owner asked for a structured
"since when", which a free-text note cannot filter or sort.

## Consequences and limits

- The grouping is a display rule, not a constraint: a user can still create a species
  whose scientific name has three words for other reasons, and it will be shown as a
  subspecies of the first two words if that species exists.
- Cultivars are shown as *Variety of X*, true trinomials as *Subspecies of X*.
- The breeding start is stored at UTC midnight, like the other dates of the app; it is a
  date, not an instant of the day.
