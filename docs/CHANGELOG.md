# Changelog

## Unreleased

- **Fixed:** scenes stopped working after groups were made or their segments changed: a newer
  group stayed on and covered the scene's lights. Playing a scene now switches off lights that
  share LEDs with its lights, as turning a light on by hand does; a held scene brings them back
  when released, and a scene card is only highlighted when nothing covers it
  ([#1](https://github.com/dvrsy/touchfx/issues/1)).
- **XY pad** ([#2](https://github.com/dvrsy/touchfx/issues/2)):
  - The axis menus only offer what works on at least one light that is on, with "(1 of 3)" when it
    works on only some. A saved choice that doesn't work right now does nothing until it does.
  - Overall speed now works on effects that follow the beat (Bar, Multicolor Bar, BPM Strobe,
    Segment Chase, Effect Chase: quarter, half, double or quadruple time) and on effects that move
    a step per frame (Scroll, Water, PlasmaWled2d). It is no longer offered for effects that only
    follow the sound, where it did nothing.
  - Palette shift is only offered for effects that draw from the palette.
  - Colours glides through the palettes sorted by colour, blending one into the next, instead of
    jumping through them in list order.
  - Finer control: nothing changes close to the centre, and small moves change less.
  - New: **Saturation** (grey to full colour) and **Strobe** (a steady rate from slow to fast, or
    1, 2, 4 or 8 flashes per beat), for every light.

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
