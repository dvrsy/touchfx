# Changelog

## Unreleased

- **Fixed:** scenes stopped working after groups were made or their segments changed: a newer
  group stayed on and covered the scene's lights. Playing a scene now switches off lights that
  share LEDs with its lights, as turning a light on by hand does; a held scene brings them back
  when released, and a scene card is only highlighted when nothing covers it
  ([#1](https://github.com/dvrsy/touchfx/issues/1)).

## TouchFx 0.1.0 (2026-11-26)

First public version, an early preview.

- **62 effects:** LedFx's strip and matrix effects, 10 from WLED, and TouchFx's own Segment Chase,
  Effect Chase and Blend; 110 LedFx presets.
- **Lights:** WLED devices and parts of them, groups across several devices, matrices with
  side-by-side or stacked panels.
- **Scenes:** normal, hold-to-play and playlists (in order or shuffled, time per scene).
- **XY pad** to change speed, size, intensity, colours, hue, palette, brightness and overall speed
  of the running effects live, for all lights or one.
- **Audio:** microphone, phone audio or tap tempo (with simulated sound); silence threshold; kick
  and tempo tracking.
- **WLED:** automatic discovery, DDP streaming, the WLED web page inside the app.
- Backup and restore of everything as a JSON file; dark, mixer-style interface.

The full list is in [FEATURES.md](FEATURES.md).
