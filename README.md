# AutoClick

[![CI](https://github.com/activebook/auto-click/actions/workflows/ci.yml/badge.svg)](https://github.com/activebook/auto-click/actions/workflows/ci.yml)

**Presses one spot on your screen, on a timer you set.**

Split into a 3×3 grid, pick a cell, pick the seconds, press Start. A small floating hand stays on top
of every app, so you can start, stop, retarget or change the interval without leaving what you are
doing. It prints `ON`, `OFF`, `PAUSED` or `DONE` under the glyph, so a glance answers "is it working?".

Android 8.0+ · English · no network permission · APK, not a store listing.

## Install

1. Grab `dist/AutoClick-1.3.0.apk` (or the newest release) and open it on the phone.
2. Allow "Install unknown apps" for the file manager, and on Android 13+ also **Settings → Apps →
   AutoClick → ⋮ → Allow restricted settings**.
3. Over USB: `adb install -r dist/AutoClick-1.3.0.apk`.

Then turn on the two switches it asks for: **Accessibility ("AutoClick taps")** sends the taps,
**Display over other apps** shows the hand. Both are required; notifications and unrestricted battery
only make long runs more comfortable. Android lets no app grant these itself, so each button opens
the owning system screen and the app re-reads the real state on return. The accessibility list
scrolls itself to AutoClick's row, so you do not have to hunt for it.

## Use

Pick a cell and an interval (1–3600 s, default 1 s), press **Start tapping**, leave the app. Tap the
hand for **Play/Stop**, **Settings** (with an **Open** button that brings the full app forward) and
**Close**. Retargeting or re-timing mid-run applies on the next tick. Set **Stop after** to 100, 500
or 1000 taps and the run ends itself, leaving the hand in **DONE**.

It refuses to start when the hand covers the target, because that tap would hit the hand and not the
app under it. While running, ticks the hand crosses are skipped and counted, never silently eaten.
Timing is anchored to a single start instant, so a long run does not drift.

## Docs

- [`docs/DESIGN.md`](docs/DESIGN.md) — every screen, dimension and line of copy
- [`docs/TECH.md`](docs/TECH.md) — engine, overlay windows, permissions, build and verification
- [`docs/CI.md`](docs/CI.md) — gates and release automation

No internet permission, no ads, no analytics, no screen reading. MIT licensed — see
[LICENSE](LICENSE).
