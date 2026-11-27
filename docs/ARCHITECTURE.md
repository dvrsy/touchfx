# TouchFx architecture

## The render loop

```
Mic / phone audio / tap ─► AudioCapture ─► AudioAnalyzer ─► AudioFrame (bands, beats, tempo, …)
                                                               │  60 fps
                                                               ▼
LightEngine: for each light (virtual) ─► its effect (+ XY pad overlay) ─► blur / mirror / flip /
             brightness / grouping ─► mapped onto device pixels (span, copy, panels, serpentine)
                                                               │
                                        on-screen previews ◄───┴───► DDP over UDP ─► WLED
```

`LightEngine` (`engine/LightEngine.kt`) runs the loop on its own thread at 60 fps and holds the
app's state as `StateFlow`s that the Compose UI observes.

## Data model

- **Device** (`DeviceConfig`): a physical output: WLED controller (host, LED count) or preview strip.
- **Light** (`VirtualConfig`, LedFx's "virtual"): what an effect renders onto. Segments
  `(device, start, end, invert)`, mapping span/copy, grouping, rows / serpentine / rotate / panel
  layout for matrices, on/off, and `EffectSettings` (effect, palette, brightness, settings per effect).
  Every device has its own light with the **same id**; groups are lights with several segments.
  Turning on a light switches off conflicting ones (LedFx rule).
- **Scene** (`Scene`): per light an action (activate with a look / ignore / stop); kinds NORMAL,
  HOLD (hold to play) and PLAYLIST (`Playlist` of scene ids with seconds, in order or shuffled).
- **Saved data:** SharedPreferences `engine`, schema 2. Backups (`engine/Backup.kt`): JSON with
  `app` (`touchfx`), `format` 1, `schema` (the same data model version), devices, virtuals, scenes,
  presets, settings.

## Code map (`app/src/main/java/app/touchfx/`)

| Path | Contents |
|---|---|
| `audio/` | Capture (mic, playback capture), FFT, `AudioAnalyzer` (bands, silence gate, kicks, tempo grid, tap tempo, simulated sound) |
| `effects/Effects.kt` | The effect catalogue: id, name, category, settings for each effect |
| `effects/` | Implementations: LedFx 1D ports (`EffectsClassic/Atmospheric/More.kt`), 2D (`Effects2D.kt`, `EffectsLedFx2D.kt`), WLED (`Wled.kt`), `SegmentChase.kt`, `EffectChase.kt`, `Blend.kt`, `ChildEffects.kt`, `PixelFont.kt`, palettes, presets |
| `engine/` | `LightEngine` plus pure, unit-tested logic: `VirtualLogic`, `PixelMapper`, `SceneLogic`, `PlaylistLogic`, `XyPad`, `EffectClock` (the pad's Overall speed per effect), `ViewFilters`, `Backup` |
| `model/` | `DeviceConfig`, `VirtualConfig`, `Segment`, `EffectSettings`, `Scene`, `Playlist` |
| `net/` | DDP sender, WLED discovery (mDNS) and JSON API |
| `service/` | Foreground service, phone-audio consent screen |
| `ui/` | Jetpack Compose: Lights, Scenes, Audio, Devices tabs; light, group, playlist and XY pad screens; theme |

## Adding an effect

Write a class extending `Effect` (strip) or `Effect2D` (matrix) in `effects/`, and register an
`EffectInfo` (id, name, category, settings) in `Effects.all`; the settings screen is built from it.
Use LedFx's setting keys when porting, so its presets apply. Add the effect to `XyPad.MACROS` if it
has a speed, size or intensity setting, to `XyPad.TIMING` if it follows the beat, moves a step per
frame or only follows the sound, and to `XyPad.NO_PALETTE_SHIFT` if it ignores the palette
position. Effects take random numbers from `FxRandom.source`, so tests can seed it. The generic
tests then render it at every size and check that it reacts to the palette; `PadEffectsTest` checks
the pad marks against what the effect really does.
