# NFC aquarium identification — 2026-11-02

## Problem

The user bought NTAG215 NFC stickers (60 pieces, **ISO/IEC 14443-3A (NFC-A) / NFC
Forum Type 2**, 504 bytes of user memory) and wants the app to identify an aquarium by
touching its physical tag to the phone, without typing anything.

## Decision

A tag is bound to an aquarium by its **hardware UID**, stored in Room, and read with
the NFC reader mode of the foreground activity.

- `aquarium_nfc_tags` (Room schema **v4**) maps `tagId` (the lowercase hexadecimal
  UID) to `aquariumId`, with an optional label and the binding timestamp. The tag UID
  is the primary key, so one tag can only point at one aquarium and re-binding moves
  it instead of duplicating it.
- `NfcTagReader` (app module, `com.pietrondo.aquariummanager.nfc`) enables
  `NfcAdapter.enableReaderMode` with `FLAG_READER_NFC_A` (plus `FLAG_READER_NFC_V` for
  stickers that answer ISO 15693) while the app is in the
  foreground and reports the UID of the tag that is touched.
- `AquariumListViewModel.onNfcTagScanned` either binds the tag (while the assignment
  is armed) or resolves the bound aquarium and selects it.
- The manifest declares `android.permission.NFC` and a non-required
  `android.hardware.nfc` feature: the app still installs on devices without NFC.

## Alternatives considered

**Write the aquarium UUID into the NDEF message of the tag.** The tag would then
describe itself and no binding table would be needed. Rejected: it only works on
tags that accept a write, and cheap NTAG215 sticker packs are frequently shipped
pre-formatted with a URL and write protected. Reading the UID works on every
NTAG21x chip, formatted or not, locked or not, so the sticker keeps identifying its
aquarium even after the app is reinstalled or the database is rebuilt.

**Keep the binding in a side file instead of Room.** Rejected: Room is the source of
truth in this project and a second store would need its own backup, migration and
consistency rules.

**Write the tag *and* keep the UID binding.** Rejected as unnecessary complexity: the
UID alone is enough for identification, and writing adds a failure mode (locked tag)
that the user would have to diagnose.

## Consequences and limits

- **Tag bindings are not part of the backup archive.** The backup format is a strict
  JSON document with an exact key set and a format version; adding a table would
  require a format version bump and a migration path for existing archives. A restore
  re-inserts the aquariums with the same UUIDs, so `replaceEntireLibrary` re-links the
  bindings of the aquariums that came back from the archive. A restore onto a device
  that never saw the tags leaves the physical stickers unbound and they have to be
  scanned again — the stickers themselves are not lost.
- The reader only runs while the app is in the foreground. There is no background
  tag dispatch and no lock-screen shortcut.
- The reader accepts any tag UID and does not require a specific technology: the
  NTAG21x family answers NFC-A, while other stickers may answer NFC-V.
- The same tag is reported once every two seconds, so holding the phone on a sticker
  does not spam the state.

## Related work completed in the same change

The working tree contained unfinished schema v3 work (custom maintenance types:
exported `3.json` plus two tests) that did not compile, and three legal disclosure
strings missing from the English resources. Both were completed so the build, the
schema chain (v2 → v3 → v4) and the test suite are consistent:

- v3 adds the `maintenance_types` table and `RoomAquariumRepository.createMaintenanceType`,
  with `custom:` prefixed codes that are accepted by the maintenance event validation.
- v4 adds the `aquarium_nfc_tags` table described above.

Completing v3 exposed a defect in the backup: the archive validated every
maintenance event type against the packaged catalog only, so a library using a
user-defined type could not be exported. The backup format is now **version 2** and
carries the user-defined maintenance types, so an archive is self-contained and an
event type is accepted only when it is in the packaged catalog or in the archive
itself. Archives of the previous versions are migrated on read.
