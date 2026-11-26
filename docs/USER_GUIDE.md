# TouchFx user guide

How to set up your lights and use the app. The full list of effects and options is in
[FEATURES.md](FEATURES.md).

The app has four tabs: **Lights**, **Scenes**, **Audio** and **Devices**. At the top right are
**Start / Stop** (the engine that listens and drives the lights) and **⋮** (backup and restore).

## 1. Add your LEDs

1. Make sure the phone and your WLED controllers are on the **same Wi‑Fi**.
2. Open the **Devices** tab and tap **Add device**.
   - Controllers found on the Wi‑Fi are listed: tap **Add**.
   - Not listed? Enter its address under **Add by IP address** and tap **Connect**.
   - No hardware yet? **Add a preview strip** shows LEDs on the phone screen.
3. Tap **Start**. Each device now has its own light on the **Lights** tab.

The number of LEDs is read from WLED. To change it, tap the pencil next to the device (or use
**Number of LEDs** on the light's screen; **From WLED** reads it again). The globe
button opens the WLED web page inside the app.

## 2. Matrices (LED panels)

A new device is treated as a strip. If it is a matrix, tell TouchFx its shape:

1. On the **Lights** tab, tap the light.
2. Scroll down to **Matrix** and enter the number of **Rows**. TouchFx shows the result, e.g.
   "16 × 8 matrix".
3. If your panel is wired zig-zag (every second row runs backwards), switch on **Zig-zag wiring**.
   Leave it off if WLED itself is already set up as a 2D matrix.
4. Use **Rotate the matrix** if the picture is on its side or upside down.

Pick the **Matrix test** effect to check: red should start top-left and blue top-right.

## 3. Groups

A group plays one effect across several devices, or parts of them.

1. On the **Lights** tab, tap **New group**.
2. Add segments with **Add segment**: choose a device and the LEDs to use (**From LED**, **To LED**).
   **Reverse** runs that segment backwards. While the engine runs, tapping a segment lights it up
   white so you can see which LEDs it is.
3. Choose how the effect is laid out:
   - **Span:** one effect runs across all segments, in the order shown.
   - **Copy:** every segment shows the whole effect.
4. **LEDs per effect pixel** makes effects chunkier on dense strips.

**Panels as one matrix:** make a group of your panels with **Span**, open it and set **Rows** as
above. Then choose **Panels: Side by side** (each segment is one panel, left to right) or
**Stacked** (one above the other). A reversed segment is a panel turned by 180°.

A device shows **one light at a time**: turning on a group switches off the device's own light,
and the other way round.

## 4. Effects, presets and colours

Tap a light to open its screen:

- **Effect:** tap the effect name to choose another one.
- **Presets:** ready-made looks from LedFx. **Save current** keeps your own.
- **Colours:** pick a palette or your own colours.
- **Brightness** and the effect's own settings follow below. Each light remembers its settings for
  every effect.

The switch on each light turns it on or off; the slider at the top of the Lights tab dims all
lights at once.

## 5. Chase and Blend

These effects are made for groups and for combining effects:

- **Segment Chase** lights the parts of a group one after another, in time with the music
  (on a single strip it splits the strip into **Parts**).
  - **Mode:** *Chase* (one part at a time), *Fill* (parts light up one after another, then it starts over) or
    *Colour shift* (all parts lit, colours move along).
  - **Step on:** *Tempo* (steps on the beat), *Kicks* (steps on every bass kick), or *Kick runs a
    cycle* / *Beat runs a cycle*: each kick or beat runs once through all parts, taking **Cycle
    length in beats**.
  - **Speed:** how many steps per beat (e.g. 1 per beat, 4 per beat, or only every 2nd beat).
  - **Order:** forward, backward, bounce or random. **Trail** lets parts fade out.
- **Effect Chase** works the same, but the parts show other effects instead of a colour. Choose
  them under **Effects shown in the parts**; they use each light's own saved settings.
- **Blend** puts one effect over another, e.g. *Texter* (scrolling text) over a plasma. Choose a
  **Background effect** and a **Foreground effect**, a **Blend mode** and the **Amount**.

## 6. Scenes and playlists

A scene saves the look of several lights and brings it back with one tap.

1. Set up your lights, open the **Scenes** tab and tap **Save scene**.
2. Choose which lights belong to the scene. Lights left out aren't changed when you play it.
3. Switch on **Hold to play** for a scene that only runs while you press it; letting go brings back
   the previous look. Good for strobes.

Tap a scene to switch to it. With **⋮** on a scene you can rename it, update it from the current
lights, change it between tap and hold, or delete it.

**Playlists** play scenes one after another: tap **Playlist** on the Scenes tab, add scenes, set
the seconds for each and choose **In order** or **Random**. A running playlist shows the current
scene with Skip and Stop. Pressing a hold-to-play scene pauses the playlist; it carries on when you
let go.

The filter chips at the top show only normal scenes, hold-to-play scenes or playlists (on the
Lights tab: only lights or only groups).

## 7. XY pad

The XY pad changes the running effects live by dragging a finger. Open it from:

- the **Scenes** tab: **XY pad** button (works on all lights),
- the **Lights** tab: **XY pad** next to the filter (all lights),
- a light's screen: the four-arrow button at the top (only that light).

Choose what each axis changes under **X ↔** and **Y ↕**: Speed, Size, Intensity, Colours, Overall
speed, Hue, Palette shift or Brightness. The centre leaves everything as saved; towards the edges a
setting goes to its minimum or maximum. Lifting your finger returns to normal unless **Hold
position** is on. Nothing is saved.

## 8. Audio

On the **Audio** tab, choose what the lights listen to:

- **Mic:** sound in the room.
- **Phone:** music playing on this phone (Spotify, YouTube, …). Android asks for permission once;
  some apps block it.
- **Tap:** you tap the beat (4 or more taps). Beat effects follow your taps; with **Simulate sound**
  the other effects move to a made-up drum pattern.

**Silence threshold:** lights stay dark while the level is below the marker. Set it just above the
background noise. **Sensitivity:** raise it for quiet rooms or low phone volume.

## 9. Backup and restore

**⋮ → Back up to file…** saves all devices, lights, groups, scenes, your presets and settings as
one JSON file. **⋮ → Restore from file…** loads it again (it replaces the current setup).

## Tips

- **Nothing happens:** tap **Start**; check that the light is switched on and the Audio tab shows
  "Listening", not "Silence" (lower the silence threshold).
- **WLED not found:** same Wi‑Fi? Add it by IP address (shown in the WLED app or your router).
- **Matrix looks scrambled:** check **Rows**, **Zig-zag wiring** and, for groups, **Side by side** /
  **Stacked**, using the Matrix test effect.
- **Running in the background:** TouchFx keeps running with the screen off and shows a
  notification with a Stop button.
