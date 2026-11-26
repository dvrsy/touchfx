# Credits and licences

TouchFx is licensed under the **GNU General Public License v3.0** ([LICENSE](../LICENSE)).

## LedFx Mobile

TouchFx is a fork of [LedFx Mobile](https://github.com/mahatosourav00/ledfx-mobile) by Sourav
Mahato, a native Android port of LedFx (forked at its version 0.3.0, November 2026). TouchFx is a
modified version of it, changed from November 2026 on.

## LedFx

Effects, setting names and ranges, gradients and the built-in presets are adapted from
[LedFx](https://github.com/LedFx/LedFx) by the LedFx developers, licensed under the GPL-3.0. The
presets are generated from LedFx's `presets.py` (`tools/convert_ledfx_presets.py`).

## WLED

Blink, Blink Rainbow, Bpm, Lightning, Gravfreq, Puddlepeak, Blobs, Octopus, Soap and Tartan and
their helper functions (`effects/Wled.kt`) are ported from [WLED](https://github.com/wled/WLED),
© 2016-present Christian Schwinne and individual WLED contributors, licensed under the EUPL v. 1.2
or later and used here under the GPL-3.0 via the EUPL's compatibility clause. Effect authors as
WLED credits them:

| Effect | Authors |
|---|---|
| Gravfreq, Puddlepeak | Andrew Tuline |
| Blobs | Stepko, adapted by Blaz Kristan |
| Octopus | Stepko and Sutaburosu, adapted by @blazoncek |
| Soap | Stepko (idea by Stefan Petrick), adapted by @blazoncek, optimised by @dedehai |
| Tartan | Elliott Kember, modified by Andrew Tuline |
| Blink, Blink Rainbow, Bpm, Lightning | WLED contributors |

## FastLED

WLED's beat, easing and scaling helpers used by these effects derive from
[FastLED](https://github.com/FastLED/FastLED):

```
The MIT License (MIT)

Copyright (c) 2013 FastLED modified by @dedehai

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

## TouchFx's own

The pixel font (`effects/PixelFont.kt`), the icons, Segment Chase, Effect Chase, Blend's layer modes,
scenes, playlists, tap tempo and the XY pad were written for this project.
