# Android foundation

## Pinned baseline

| Component | Selection |
|---|---|
| Gradle | 8.13, genuine upstream wrapper with distribution SHA-256 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin / Compose compiler plugin | 2.2.21 / 2.2.21 |
| Compose BOM / Material 3 | 2025.12.00 / BOM-managed 1.4.0 |
| Activity Compose | 1.12.2 |
| JDK runtime / bytecode | Temurin 21 / Java and Kotlin 17 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 23 |
| SDK build-tools | 35.0.0 |
| Room / WorkManager | 2.8.5 / 2.11.2, catalog reservations only |

These are deliberately stable compatible versions, not a claim to use every latest release. AGP 8.11.1 is within Kotlin 2.2.21's documented AGP compatibility range; Gradle 8.13 is within both ranges. JDK 21 is an available LTS supported by Gradle 8.13; AGP requires at least 17. Bytecode stays at 17. API 23 is the technical floor for the selected AndroidX generations, including Room 2.8 and WorkManager 2.11; API 21/22 devices are excluded. API 36 is supported by this AGP; target behavior still requires actual device testing before release.

Room and WorkManager are not applied or linked to the app yet: persistence and background jobs belong to later tasks. No KSP/compiler choice is required until the schema task. No entities, biological rules, login, service keys or network implementation are supplied by this foundation. The application identifier `com.pietrondo.aquariummanager` is a development choice, not an existing production/store binding. The launcher is temporary and has no final icon; icon design remains its own task. Cloud Auto Backup is opted out with `allowBackup=false`; Android documents that some manufacturers may still permit device-to-device transfer. Explicit extraction-rule policy and on-device verification are required alongside the later backup implementation; no data is stored by this scaffold.

## Official provenance (checked 2026-10-18)

- [AGP 8.11 compatibility: Gradle 8.13, build tools 35, SDK 36, JDK 17 minimum](https://developer.android.com/build/releases/agp-8-11-0-release-notes).
- [Kotlin Gradle compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html): Kotlin 2.2.20–2.2.21 supports Gradle through 8.14 and AGP through 8.11.1.
- [Compose compiler setup for Kotlin 2+](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) and [December Compose BOM](https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-december-release).
- [Activity releases](https://developer.android.com/jetpack/androidx/releases/activity), [Room releases](https://developer.android.com/jetpack/androidx/releases/room), [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work).
- [Gradle Java compatibility](https://docs.gradle.org/8.13/userguide/compatibility.html) and [wrapper verification](https://docs.gradle.org/8.13/userguide/gradle_wrapper.html#sec:verification).

Wrapper JAR and scripts were downloaded from the official Gradle `v8.13.0` Git tag. The JAR was SHA-256 checked against the [official checksum](https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256): `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`. Distribution checksum from [Gradle](https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256): `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`, enforced by the wrapper. Do not replace wrapper binaries without rechecking provenance/checksums.

## Reproduce and inspect

Install JDK 21 and Android Studio with SDK platform 36/build-tools 35.0.0. Set process-local `ANDROID_HOME` or `local.properties`; select JDK 21 in Android Studio's Gradle settings. Dependencies require build-time internet, but no application keys or runtime network. Review SDK licenses yourself; CI uses the hosted runner's Android SDK installation, not a script piping consent into license prompts.

Run `gradlew.bat --no-daemon test lint assembleDebug` (Windows), or `bash ./gradlew --no-daemon test lint assembleDebug` (Linux/macOS). Run the Python checks from README too. Configure an API 36 emulator in Device Manager (AOSP image is sufficient; Google login/services not needed). Start it, install the debug APK with Android Studio or `adb install -r app/build/outputs/apk/debug/app-debug.apk`, and launch Aquarium Manager. Repeat on API 23 for floor coverage. Emulator/system-image download and license acceptance are developer-controlled; no emulator image is required for JVM tests/lint/APK assembly.

CI runs on pushes to main and pull requests, with contents-read only, commit-pinned official checkout/setup-java/setup-python/setup-gradle actions, wrapper validation through setup-gradle, pinned SDK packages, Python tests and the same Gradle command. Repository Actions were read-only verified as enabled. A workflow file alone is not proof of a hosted green run. That post-push gate subsequently passed for `78f813d`: see the hosted evidence below.

## Validation

Foundation Python assertions were observed failing before the scaffold existed, then all 12 Python tests passed (3 foundation, 9 asset tooling). The `test` task currently has no Kotlin test sources; do not describe it as feature-unit-test coverage. Module boundaries are build/configuration guards, not a complete future architecture enforcement system.

Actual Gradle build evidence is recorded below after execution. No emulator, instrumentation, UX, accessibility or store acceptance is claimed by this task.

### Local build evidence — 2026-10-18

Windows, Temurin 21.0.11, existing SDK platform 36/build-tools 35.0.0, process-local `ANDROID_HOME`. Final pinned baseline command `gradlew.bat --no-daemon clean test lint assembleDebug` succeeded in **2m 23s**, **128 tasks executed**. The lint XML identifies engine **8.11.1**. App lint has **0 errors / 11 warnings**: nine newer-tool/dependency notices, missing temporary launcher icon, and the documented extraction-rule/OEM-transfer caveat. Data lint has no issues. No warning suppression or baseline was added. Native `libandroidx.graphics.path.so` was packaged unstripped because local stripping was unavailable; assembly still succeeded.

APK: `app/build/outputs/apk/debug/app-debug.apk`, 10,955,701 bytes. `aapt dump permissions` confirms **no INTERNET permission**; AndroidX adds only the app-specific `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. The source app manifest declares no permissions. The three modules have no Kotlin unit-test sources, so Gradle test tasks are NO-SOURCE; the 12 Python tests at this initial checkpoint were the actual automated test assertions for this foundation and existing asset tool (subsequently expanded to 14).

This initial evidence is a clean local rebuild, not an isolated fresh-clone or hosted-CI result. Independent review and the subsequent hosted fresh-checkout build below completed foundation delivery gates; no emulator/device was launched, and no runtime visual acceptance is claimed.

### Hosted verification — 2026-10-18

[Run 39168818925](https://github.com/pietrondo/aquarium-manager-android/actions/runs/39168818925), exact commit `78f813da05e14045fe458162848674fcb5be4025`, passed on hosted Ubuntu 24.04 from a fresh checkout: **14 Python tests**, then `test lint assembleDebug` **successful in 3m3s / 125 tasks executed**. SDK tools are resolved explicitly below `ANDROID_HOME`, not assumed to be on PATH. No application/service secrets were required. This satisfies the foundation hosted-build gate and closes `aq-7zd.1`; caching was enabled/restored, so it is not a cold-cache claim. Kotlin tests remain NO-SOURCE and device/visual/feature acceptance is still future work. Full incident and validation details are in [verification evidence](verification.md).
