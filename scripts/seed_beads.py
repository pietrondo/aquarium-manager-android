"""Seed the approved Beads plan idempotently after independent isolation verification."""
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
EPICS = {
 'foundation': 'Foundation and reproducible delivery',
 'library': 'Aquarium library and Room domain',
 'care': 'Readings and maintenance',
 'life': 'Livestock and dosing',
 'backup': 'Safe offline library backups',
 'quality': 'Artwork accessibility and release validation',
}
# This is seed data for Beads, not a parallel status tracker. Beads owns all status.
TASKS = [
 ('foundation','toolchain','Pin Android toolchain and reproducible Gradle CI',[],
  'Create real Gradle wrapper/checksum, version catalog, app/domain/data modules and CI. Select compatible stable Kotlin/AGP/Gradle/Compose/Room/WorkManager and SDK levels from official docs; document JDK and emulator.',
  'Fresh clone builds test lint assembleDebug without secrets; CI repeats it; domain has no Android imports.'),
 ('foundation','architecture','Wire MVVM repository boundaries and application composition',['toolchain'],
  'Implement presentation -> domain <- data; immutable UI states, repository interfaces, explicit Room mappings and coroutine error handling. No direct UI DAO access.',
  'Fake repository ViewModel tests prove loading/success/error; module dependency tests prevent reversed edges.'),
 ('library','schema','Implement UUID Room schema indexes and canonical-unit validation',['architecture'],
  'Implement all approved tables from design; finite numeric checks, null readings, ISO dates and IANA zones. External content catalogs; settle decimal serialization and confirmed deletion decisions before schema.',
  'DAO/FK/index tests reject orphans/duplicate links; null is not zero; unit roundtrip is lossless; no biological safe ranges hardcoded.'),
 ('library','migrations','Export schemas and test non-destructive Room migrations',['schema'],
  'Version Room schemas and implement migration test fixtures. Never use destructive fallback for user histories.',
  'Migration fixture preserves UUIDs, readings, links and snapshots; unsupported schema fails visibly without wiping data.'),
 ('library','catalogs','Load editable versioned external catalogs',['schema'],
  'Define JSON catalogs for profile labels, maintenance types, species and units. Separate user Room records from packaged seeds; no automatic overwriting user edits.',
  'Fixture tests cover malformed catalogs, unknown unit codes, stable IDs and seed upgrade conflicts.'),
 ('library','aquariums','Build multi-aquarium CRUD and overlapping profile screens',['schema','catalogs'],
  'Create aquarium list/detail/forms and many-to-many profile editing. Unknown litres supported. Confirm archive/permanent deletion behavior before destructive actions.',
  'Repository and Compose tests show isolated histories across two aquariums, duplicate label prevention and validation preserving typed input.'),
 ('care','readings','Record optional water parameters and timestamped history',['aquariums'],
  'Implement pH KH GH NO2 NO3 TDS temperature input and history using canonical units and explicit missing values. Confirm minimum-one-reading policy.',
  'Tests distinguish null/zero, sort same timestamps deterministically and keep aquarium histories isolated; restore readings roundtrip.'),
 ('care','trends','Render parameter trends without fabricated safe bands',['readings'],
  'Display null gaps and unit-labelled history/trends. If advisory ranges are introduced, use configurable documented sources rather than mandatory hardcoded limits.',
  'UI tests cover sparse/no data, large text and non-color cues; native-size chart labels readable.'),
 ('care','maintenance','Log maintenance and optional changed water volume',['aquariums','catalogs'],
  'Implement typed maintenance history with optional litres and notes. Confirm whether changes greater than net volume are warning or error; do not invent hard cap.',
  'Repository/Compose tests cover missing volume, invalid numeric input, editing and correct aquarium filtering.'),
 ('life','species','Implement fauna/flora catalog editing',['catalogs'],
  'Build species library with identifying names and kind; resolve duplicate naming and referenced deletion semantics before adding destructive controls.',
  'Tests cover missing names, editing referenced species, fauna/flora filtering and offline persistence.'),
 ('life','stock','Track introductions exits and unknown quantities',['species','aquariums'],
  'Build stock entries per aquarium with optional quantity and introduced/exited dates; resolve fractional quantities and date precision explicitly.',
  'Tests reject exit before introduction and nonpositive supplied quantities; unknown stays null and past stock remains visible.'),
 ('life','products','Implement shared product library and dose-unit catalog',['catalogs'],
  'Add products and explicit unit choices; never infer mass/volume/drops conversions. Preserve referenced products through confirmed lifecycle policy.',
  'Tests reject blank names/unknown units and retain dosing histories after product edits.'),
 ('life','plans','Implement daily and weekly dosing recurrence with timezone',['products','aquariums'],
  'Implement plans using IANA timezone, local start/end dates and ISO weekdays. Confirm time-of-day notification policy separately; no exact-clock promises.',
  'Pure Kotlin tests cover DST gap/overlap, timezone travel, leap date, daily/weekly windows and end before start.'),
 ('life','dose_events','Record immutable dosing snapshots including ad hoc events',['plans'],
  'Persist aquarium, instant, optional plan, product-name snapshot, dose and unit independent of later plan/product changes.',
  'Tests cover ad hoc event, changed/deleted plan policy and immutable historical values; transaction rollback on invalid plan-aquarium relation.'),
 ('backup','dto','Define versioned library JSON DTO validation and fixtures',['migrations'],
  'Define complete snapshot DTOs and manifest, counts/hash and supported format policy. Bounded arrays/strings, duplicate UUID/FK and unknown-code rejection.',
  'Golden fixture includes every table/nulls/Unicode; future formats rejected; explicit older format migration fixture; no device permissions or credentials exported.'),
 ('backup','export','Export coherent verified ZIP snapshots',['dto'],
  'Read all tables in coherent transaction, detach DTOs, write temporary ZIP manifest/library, hash exact bytes and verify locally. Bound memory.',
  'Concurrent edit tests cannot yield mixed snapshots; cancellation/out-of-space leaves last valid backup untouched; exact counts/hash fixtures.'),
 ('backup','parser','Implement hostile ZIP parser and restore preview',['dto'],
  'Allowlist exactly manifest.json/library.json; limit sizes/ratio/rows and validate types/hash/counts/FKs before preview. Confirm tested resource ceilings.',
  'Reject traversal, duplicate entries, extra files, encrypted/unsupported ZIP, bombs, malformed JSON, future format, bad hash and orphan FK before DB mutation.'),
 ('backup','restore','Replace library transactionally with verified safety backup',['export','parser'],
  'Preview and explicit Replace library confirmation, exclusive writer gate, safety snapshot then one FK-safe transaction. Preserve device destination/scheduler.',
  'Injected mid-import failure rolls back all tables; failed safety backup aborts; concurrent edits blocked; roundtrip semantic equality; process interruption recovery fixture.'),
 ('backup','saf','Implement SAF manual export import and destination readback',['export','restore'],
  'Use document pickers and uniquely named documents; close and reopen destination to verify actual bytes before success. No Google APIs or filesystem-path assumptions.',
  'Instrumented tests cover cancelled picker, revoked grant, unavailable provider, out-of-space, partial write and no overwrite of last valid document.'),
 ('backup','automatic','Schedule best-effort backups with safe retention',['saf'],
  'Persist tree grant device-locally, WorkManager daily proposal and seven-valid-generation proposal subject to confirmation. Visible last result and reselect folder action.',
  'Tests exercise process restart, revocation, Doze/delays, failed generation no retention, prune only app-owned verified auto files; manual files untouched.'),
 ('quality','pixellab','Deliver explicit-opt-in PixelLab generation and offline density sizing',[],
  'Implement official verified API contract, data-driven candidate prompts, cached originals, no secret logging, bounded calls, dry-run and nearest-neighbor density export.',
  'Mock/unit tests cover exact request/response, no network dry-run, cache, HTTP error, safe paths, image aspect and all Android density sizes. No paid generation needed.'),
 ('quality','modern_icons','Produce approved adaptive and monochrome launcher resources',['pixellab','toolchain'],
  'Obtain visual approval, then add adaptive foreground/background XML and Android 13 monochrome resource. Distinguish legacy mipmaps from Compose UI drawables.',
  'Validate circle/squircle/rounded-square masks, adaptive safe zone and themed icon on API33+; inspect every density and store512 native-size.'),
 ('quality','accessibility','Validate Material3 screens accessibility and pixel-art readability',['aquariums','readings','maintenance','stock','dose_events','modern_icons'],
  'Use content descriptions, >=48dp touch targets, scalable text, contrast and non-color cues. Keep art crisp without sacrificing semantics; verify light/dark layouts.',
  'TalkBack traversal and large-font/device-size screenshots pass; UI icons readable at24dp and hdpi; no clipped labels or color-only status.'),
 ('quality','release','Run offline device restore and migration release acceptance',['automatic','accessibility','trends'],
  'Verify airplane mode core flows, multiple aquariums, migration from previous fixture, backup recovery after reinstall/manual copy and no embedded secrets/network dependency. Prepare in-app Apache2.0 and third-party notices, privacy/store disclosures and informational limitations for species/dosing; review dependency and generated-asset rights before publication.',
  'Record device/API matrix, export/import/export equality, interruption rollback, SAF revocation, fresh-clone CI build and release artifact review. In-app license/third-party notices are accessible and complete; privacy/store disclosures match actual collection, permissions and backup behavior; species/dosing limitations avoid safety guarantees or invented advice. Verify applicable PixelLab terms before distributing generated assets. No blanket legal immunity claims, custom Apache restrictions or source-only acceptance.'),
]


def bd(*args):
    env=os.environ.copy(); env['BEADS_DIR']=str(ROOT/'.beads')
    result=subprocess.run(['bd',*args,'--json'],cwd=ROOT,env=env,text=True,capture_output=True,check=True)
    return json.loads(result.stdout)


def main():
    metadata=json.loads((ROOT/'.beads'/'metadata.json').read_text(encoding='utf-8-sig'))
    if metadata.get('project_id')=='abf3e3a9-1d4a-45ae-b3b6-6f8b81171cb7':
        raise RuntimeError('Refusing inherited home tracker')
    existing=bd('list','--all','--limit','0')
    if any(not x['id'].startswith('aq-') for x in existing):
        raise RuntimeError('Refusing foreign tracker contents')
    by_title={x['title']:x['id'] for x in existing}
    ids={}
    for key,title in EPICS.items():
        ids[key]=by_title.get(title) or bd('create',title,'-t','epic','-p','1','--description',
          'Approved offline-native project delivery boundary; child issues define implementation and evidence.',
          '--acceptance','All child issue criteria met with fresh review; no production merge without approval.',
          '--design','docs/decisions/2026-10-18-library-and-backup-design.md')['id']
    for epic,key,title,deps,description,acceptance in TASKS:
        ids[key]=by_title.get(title) or bd('create',title,'-t','task','-p','1','--parent',ids[epic],
          '--description',description,'--acceptance',acceptance,
          '--design','Use RED -> GREEN -> REFACTOR. See approved design; proposals need confirmation before implementation.')['id']
    for epic,key,title,deps,description,acceptance in TASKS:
        for dependency in deps:
            bd('dep','add',ids[key],ids[dependency])
    print(json.dumps(ids,indent=2))

if __name__=='__main__': main()
