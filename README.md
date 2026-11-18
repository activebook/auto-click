# AutoClick

[![CI](https://github.com/activebook/auto-click/actions/workflows/ci.yml/badge.svg)](https://github.com/activebook/auto-click/actions/workflows/ci.yml)

**Tap the same spot on your screen, over and over, on a timer you set.**

AutoClick splits the screen into a 3×3 grid. You pick a cell and a number of seconds, press Start,
and it keeps pressing the centre of that cell while you do something else on your phone.

A small floating hand stays on top of every app so you never have to switch back to AutoClick: tap
it to start, stop, retarget, or change the interval mid-run.

- Android 8.0 and newer
- English only
- No network permission. No ads. No analytics.
- Sideloaded APK — not on Google Play

---

## Why this one

There are plenty of auto-clickers. Most of them share three problems; AutoClick was built around
avoiding them.

**It never eats its own tap.** If the floating hand sits over the spot you chose, a tap would land on
the hand instead of the app underneath — and the loop would look broken while doing nothing.
AutoClick refuses to start in that case and tells you to drag the hand away. If you drag the hand
over the target *while* it is running, that tick is skipped and counted instead of being swallowed.

**The timing does not drift.** Intervals are measured from one fixed starting point, not from
"now plus one second". An hour at 1 s is still an hour at 1 s, and if Android freezes the app for a
while you get one clean resume instead of a burst of catch-up taps.

**It never lies about what is on.** Every switch, badge and counter shows what the system actually
reports. If tapping stops because a permission was revoked, you get a sentence that names the fix —
not a bubble that quietly stays green.

---

## What you get

| | |
|---|---|
| **Nine-grid targeting** | The screen is divided into nine equal cells. Pick one; the tap lands in its exact centre. Press **Pick on screen** to aim at the real screen with a full-screen grid — what you tap is where it will press. |
| **Seconds, not guesswork** | 1 s to 3600 s, default 1 s. Quick presets for 1 / 2 / 5 / 10 / 30 / 60 s, or type any value. |
| **A floating hand that means something** | 56 dp, translucent, draggable, snaps to the edge, and prints **ON / OFF / PAUSED** under the glyph so a glance answers "is it clicking right now?". |
| **Controls without leaving the app** | Tap the hand and a compact panel opens beside it: Play/Stop, Settings (target cell and interval), Minimize. Press Start and the panel folds itself away. |
| **Edits during a run** | Change the cell or the interval while the loop is running. It applies on the next tick; you never have to stop and restart. |
| **Honest feedback** | A tap counter, a skipped counter, a crosshair ring at the exact point pressed, and an optional short vibration on each tap. |
| **Pause when the screen is off** | A locked phone is not a useful place to tap. AutoClick pauses when the display sleeps and resumes on unlock. |
| **Memory** | Cell, interval, both feedback toggles and the hand's position survive restarts and updates. |

---

## Installing

AutoClick ships as an APK, not through a store.

1. Copy `dist/AutoClick-1.2.0.apk` to the phone (USB, cloud drive, or send it to yourself).
2. Open it with the file manager and allow "Install unknown apps" for that file manager when asked.
3. If your phone runs Android 13 or newer, you will also need to allow restricted settings before the
   accessibility switch can be turned on: **Settings → Apps → AutoClick → ⋮ → Allow restricted
   settings**.

Prefer a cable? `adb install -r dist/AutoClick-1.2.0.apk` does the same thing.

---

## Setting it up

Two permissions are **required**, two are optional, and AutoClick walks you through each with one
button.

| Permission | What it is for | Needed? |
|---|---|---|
| **Accessibility → "AutoClick taps"** | Sends the taps. This is the only way a normal app can press the screen. | yes |
| **Display over other apps** | Shows the floating hand — the way you start and stop without leaving the app you are tapping in. | yes |
| **Notifications** | A status line with a Stop button in the shade. | optional |
| **Battery: unrestricted** | Stops aggressive phone software from killing the service during long runs. | optional |

Until both required switches are on, the Start button stays disabled and an orange button appears
under Status naming exactly the screen it opens. Tap it, flip the switch, come back — the app
re-reads the real state and updates itself.

One detail worth knowing: tapping **Enable Accessibility** opens the long accessibility list and
**scrolls it to AutoClick's own row**, highlighting it. While the list is scrolling you will see two
brief messages at the top of the screen:

> Finding "AutoClick taps" — please wait
> Click the highlighted row

That is by design — the list pauses for a moment before it jumps, and those lines explain the wait.

Android does not let an app grant or revoke any of these itself, so the switches in AutoClick are
remote controls: they open the system screen that owns the setting. The switch always shows the
system's answer, never a guess.

---

## Using it

1. Open AutoClick. Pick a cell in the grid — the middle one is the default — and set the interval.
2. Press **Start tapping**. The hand turns teal and reads **ON**.
3. Leave the app. The hand stays on top of whatever you open.
4. Tap the hand whenever you want: **Play/Stop**, **Settings**, **Minimize**. The settings page also
   has an **Open** button that brings the full app forward.
5. Press **Stop** when you are done. Stopping is immediate and final — no queued tap fires afterwards.

Tips:

- If Start refuses with *"The floating hand covers your target"*, drag the hand to another edge and
  start again.
- If taps land slightly off in an app with its own layout, use **Pick on screen** and aim at the
  actual spot.
- If the hand disappears after a while, the phone's battery optimisation killed the service — set
  AutoClick to **Unrestricted** and allow autostart in the phone's own settings.

---

## Privacy

AutoClick has **no internet permission at all** — it physically cannot send anything anywhere.

- It never reads what is on your screen: the accessibility service is declared without window-content
  access.
- It stores only your chosen cell, interval, two feedback toggles and the hand's position, all in a
  local file on the phone.
- No ads, no analytics, no third-party SDKs beyond Android's own libraries.

---

## Known limits

- **One cell, one interval.** No rotation between cells, no macro recording.
- **One second is the floor.** No sub-second mode.
- **No tapping a locked screen.** Pause-on-sleep is intentional.
- **Updates reset some permissions.** Android unbinds an accessibility service when an app is replaced, and some phones also drop the overlay permission — the app will tell you, and you flip the switches back on.
- **Not on Google Play.** Accessibility-based tappers generally conflict with store policy, so this is distributed as an APK.

---

## More

- [`docs/DESIGN.md`](docs/DESIGN.md) — every screen, dimension, colour and line of copy, including the reasoning behind them
- [`docs/TECH.md`](docs/TECH.md) — architecture, timing engine, overlay windows, permission plumbing, build and verification
- [`docs/PLAN.md`](docs/PLAN.md) — the design record: requirements, decision records, and the round-by-round history of what was tried and rejected
- [`docs/CI.md`](docs/CI.md) — the CI/CD plan: gates, release automation, signing options

Built for one phone, one job, and no surprises.

---

## License

MIT — see [LICENSE](LICENSE). Fork it, change it, ship it; no warranty.
