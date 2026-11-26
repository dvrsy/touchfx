# Developing TouchFx

## Toolchain

- **JDK 17–21** (Gradle 8.11 does not run on newer Java). If your default Java is newer,
  point Gradle to a JDK 21 with `org.gradle.java.home=/path/to/jdk-21` in
  `~/.gradle/gradle.properties`.
- **Android SDK** with platform 35 and build-tools 35.0.0; `local.properties` (not in git) holds
  `sdk.dir=…`.

## Build and test

```bash
./gradlew testDebugUnitTest      # all unit tests (engine, effects, scenes, backups, …)
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk  (app.touchfx.debug)
./gradlew assembleRelease        # signed if keystore.properties exists       (app.touchfx)
```

Debug builds are a separate app (`app.touchfx.debug`, "TouchFx (debug)"), so they install next to
the release. Logic that can live outside Android goes into pure Kotlin under `engine/` and
`effects/` with unit tests; the UI has no automated tests, so every change is also tried on a phone.

## Testing on a phone

1. Enable USB debugging and connect; check with `adb devices`.
2. Back up the app's settings first (debug builds only):
   `adb exec-out run-as app.touchfx.debug cat shared_prefs/engine.xml > engine-backup.xml`.
   Never write into an app's data without looking at what is there.
3. `adb install -r app/build/outputs/apk/debug/app-debug.apk`, start it, check
   `adb logcat -b crash`.

## Releases

1. Raise `versionCode` (always) and `versionName` in `app/build.gradle.kts`.
2. Update [CHANGELOG.md](CHANGELOG.md), commit, merge into `main`, tag `vX.Y.Z`.
3. `./gradlew assembleRelease`, copy the APK to `dist/TouchFx-X.Y.Z.apk` (`dist/` is not in git),
   check it with `apksigner verify --print-certs`, install with `adb install -r`.

**Signing:** `keystore.properties` in the project root (not in git) points to the release key
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Every update of `app.touchfx` must be
signed with the **same key**, or Android refuses it: keep the keystore and `keystore.properties`
backed up. (The key's certificate is named "LedFx Mobile"; that is invisible to
users.)

**Publishing:** if you give the APK to others, the GPL-3.0 requires that they can get the source
code too (e.g. a public repository of this fork). Before a store release: check that the name
"TouchFx" is free, and provide a privacy policy (the app records audio, but only analyses it on the
phone and sends only LED colours to your controllers).

## Tools and reference sources

- **LedFx presets:** `python3 -I tools/convert_ledfx_presets.py <LedFx>/ledfx/presets.py app/src/main/java/app/touchfx/effects/LedFxPresets.kt`
  (keeps presets of effects listed in the script's `APP_EFFECTS`).
- **LedFx:** `git clone --depth 1 https://github.com/LedFx/LedFx` (ported from `28ad9d5`).
- **WLED:** `git clone --depth 1 https://github.com/wled/WLED` (ported from `57d6788`; effects in
  `wled00/FX.cpp`, helpers in `util.cpp`, `wled_math.cpp`, `FX_fcn.cpp`, `FX_2Dfcn.cpp`). Keep the
  effect authors' credits in `effects/Wled.kt` and [CREDITS.md](CREDITS.md).
