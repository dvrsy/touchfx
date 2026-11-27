# AGENTS.md

Guidance for coding agents (and humans) working on TouchFx, a native Android app (Kotlin, Jetpack
Compose) that turns audio into LED effects and streams them to WLED controllers.
Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) first; [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
has the toolchain and release steps.

## Commands

```bash
./gradlew testDebugUnitTest   # unit tests; run before every commit, all must pass
./gradlew assembleDebug       # debug APK (app id app.touchfx.debug)
```

JDK 17–21 is required (Gradle 8.11 does not run on newer Java). Kotlin package and app id:
`app.touchfx`.

## Where things go

- **Logic that can live outside Android goes into pure Kotlin with unit tests**: `engine/`
  (`VirtualLogic`, `PixelMapper`, `SceneLogic`, `PlaylistLogic`, `XyPad`, `ViewFilters`, `Backup`)
  and `effects/`. `LightEngine` and the `ui/` screens stay thin glue. There are no UI tests; UI
  changes must be tried on a phone.
- **New effect**: a class extending `Effect` (strip) or `Effect2D` (matrix), registered as an
  `EffectInfo` in `Effects.all`. When porting from LedFx, keep its setting keys, ranges and
  defaults so its presets apply. If the effect has a speed, size or intensity setting, add it to
  `XyPad.MACROS`. If it doesn't follow the clock, mark it in `XyPad.TIMING`; if it ignores the
  palette position, add it to `XyPad.NO_PALETTE_SHIFT`. Take random numbers from `FxRandom.source`.
  The generic tests then render it at every size, apply all presets and check it reacts to the
  palette (`ColoursTest`); `XyPadTest` checks every mapped setting exists, and `PadEffectsTest`
  checks the pad marks against what the effect really does.
- **Effects that run other effects** (`composite = true`, `EffectListParam`, `ChildEffects`) can't
  nest; their chosen effects use the light's own saved settings.
- **Saved data** (SharedPreferences, schema 2) and **backups** (`engine/Backup.kt`, format 1) must
  stay readable: change them only with a migration and a test that loads the old form.
- **Settings UI**: build a new `ParamValues` when values change; Compose's strong skipping compares
  by instance, so mutating one in place leaves controls stale.

## Conventions

- Readable, plain English in user-facing text and docs; no jargon where a plain word works.
- Keep the README short; lasting detail goes into `docs/`. Update `docs/CHANGELOG.md`,
  `docs/FEATURES.md`, `docs/USER_GUIDE.md` (for anything users see or do differently) and, for
  structure changes, `docs/ARCHITECTURE.md` with the change.
- Releases: raise `versionCode` and `versionName` in `app/build.gradle.kts`, update the changelog,
  tag `vX.Y.Z` (see DEVELOPMENT.md).
- Code ported from other projects keeps its credits: LedFx (GPL-3.0), WLED (EUPL-1.2, effect authors
  in `effects/Wled.kt`), FastLED (MIT). Add new sources to [docs/CREDITS.md](docs/CREDITS.md).
  The project is GPL-3.0; only add code under a compatible licence.

## Never commit

`keystore.properties`, `*.jks` / `*.keystore` (the release signing key), `local.properties`, APKs or
anything in `dist/`, and the private `docs/IMPLEMENTATION_PLAN.md`; all are in `.gitignore`. Don't
put device addresses or other personal details into code, tests or docs (use examples like
`192.168.1.50`).
