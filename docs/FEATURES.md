# TouchFx features

## Effects (62)

All 39 of LedFx's strip effects, its VU Meter and 6 of its matrix effects are ported from LedFx's
source; 10 effects come from WLED; the rest are TouchFx's own.

| Category | Effects |
|---|---|
| Classic | Energy, Spectrum, Wavelength, Scroll, Scroll+, Power, Magnitude, Pitch Spectrum, Blade Power+, Rain, Strobe, Scan, Scan and Flare, Scan Multi, Spotlight |
| Beat synced | Bar, Multicolor Bar, BPM Strobe, Segment Chase, Effect Chase |
| Atmospheric | Fire, Block Reflections, Crawler, Energy 2, Glitch, Lava lamp, Marching, Melt, Melt and Sparkle, Water |
| Simple | Filter, Hierarchy, VU Meter |
| Bands | Bands, Bands Matrix, Blocks, Equalizer |
| No audio | Gradient, Fade, Rainbow, Single Color, Random Flash (random or fixed-rate strobe) |
| Extras | Ripples, Sparkle, Blend |
| From WLED | Blink, Blink Rainbow, Bpm, Lightning, Gravfreq, Puddlepeak |
| Matrix (2D) | Plasma2d, PlasmaWled2d, Concentric, Texter, Equalizer2d, Radial, Matrix test; from WLED: Blobs, Octopus, Soap, Tartan |

- **Settings as in LedFx:** each ported effect has LedFx's options, defaults and ranges, plus blur,
  mirror, reverse and gradient roll. Each light remembers its settings per effect.
- **Presets:** 110 from LedFx built in; save your own.
- **Colours:** LedFx's 13 gradients, 8 extra palettes, a colour picker. Effects that use their own
  colour (Scan, Filter, Texter, …) switch to the gradient when you pick a palette.
- **Segment Chase:** lights a group's devices (or parts of a strip) one after another on the beat:
  chase, fill or colour shift; on tempo, kicks, or one full cycle per kick or beat.
- **Effect Chase:** the same, but the parts show other effects (one per step or per part).
- **Blend:** one effect over another (e.g. text over a plasma), like LedFx's Blender: Over (mask),
  Mix, Add, Screen, Multiply, Lighten, Darken, with an amount.
- **Radial:** wraps a strip effect around a centre (circles, polygons, stars, twist, spin).
- **Texter:** scrolling text in a built-in pixel font (with German umlauts and €).

## Lights, groups and matrices

- **Devices:** WLED controllers found on your Wi‑Fi (mDNS) or added by IP; preview strips shown on
  the phone. Pixels are sent with DDP; long strips are split across packets.
- **Lights:** each device has its own light; **groups** combine devices or parts of them (span or
  copy, reversed segments, several LEDs per pixel). A device shows one light at a time, as in LedFx.
- **Matrices:** rows, zig-zag wiring and rotation per light; groups of panels side by side or stacked.
- **The WLED web page opens inside the app**; streaming to that device pauses meanwhile.
- **Brightness:** a master slider and one per light.

## Scenes and live control

- **Scenes** recall the look of several lights in one tap; lights left out aren't changed, unless
  they share LEDs with the scene's lights (then they are switched off, e.g. groups made later).
- **Hold to play:** a scene that runs only while pressed, then the previous look returns (strobes).
- **Playlists:** scenes played in order or at random, each for its own time; a hold-to-play scene
  pauses the playlist, which then carries on.
- **XY pad:** drag to change the running effects live, for all lights (Scenes or Lights tab) or one
  light (its screen). Targets: Speed, Size or Intensity (each effect's own setting), Colours (glides
  through the palettes sorted by colour, also for effects with their own colours), Overall speed
  (0.25× to 4×; beat effects switch to ¼, ½, 2× or 4× time), Hue, Palette shift, Brightness,
  Saturation (grey to full colour), Strobe (steady rate, or 1–8 flashes per beat). The
  menus only offer what works on the lights that are on. The centre is the saved value, with finer
  control near it; nothing is saved.
- **Filters** on the Lights tab (lights / groups) and Scenes tab (normal / hold / playlists).

## Audio

- **Sources:** microphone, phone audio (music from other apps; some DRM-protected apps block it),
  or **tap tempo** with optional simulated sound (a drum pattern from your beat).
- **Analysis:** 32-band spectrum, bass/mid/high, kick and onset detection, tempo tracking, pitch.
- **Silence threshold** per source (LedFx's `min_volume`); the tempo pauses in silence.
- **Sensitivity** for quiet rooms or low phone volume.

## Everything else

- **Backup and restore** of all devices, lights, groups, scenes, own presets and settings as JSON.
- **Background mode:** runs as a foreground service with the screen off, with a Stop button.
- **Look:** dark, mixer-style: blue for active controls, orange for peaks, LED dot meters.
