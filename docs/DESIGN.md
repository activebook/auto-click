# AutoClick — Design & UX

**Version 1.2.0** · `com.activebook.autoclick.app` · Android 8.0+ · English only

This document is the product and interaction design of AutoClick: what the app is, how each surface
behaves, what every pixel and every word is for. It is written to be sufficient on its own — an
engineer who has never seen the code should be able to rebuild the same experience from it.

Implementation, architecture and platform constraints live in [`TECH.md`](TECH.md). The chronological
record of how this design was arrived at (including every rejected attempt) is in
[`PLAN.md`](PLAN.md).

---

## 1. What AutoClick is

AutoClick taps **one fixed point on the screen, over and over, on a timer you set**. The screen is
divided into **nine cells**; you choose a cell, and every *N* seconds AutoClick presses the centre of
that cell. A small **floating hand** sits above every other app so the loop can be started, retargeted
and stopped without leaving whatever app you are tapping in.

The user it is written for:

> Someone who has to tap the same spot repeatedly in an app they do not control — a counter, a
> "keep-alive" button, a claim button, a game's repeat action — and who does not want to root the
> phone, keep it plugged into a PC, or hand the job to a sketchy ad-supported clicker.

### 1.1 Design pillars

| # | Pillar | What it means in practice |
|---|--------|---------------------------|
| P1 | **Two taps to run** | Open the app, press Start. Never a wizard, never a tour. |
| P2 | **The hand is the app** | Mid-session you should not need the app at all: the hand carries start/stop, retarget and interval. |
| P3 | **Never lie** | Every switch, badge and counter reflects what the system and the engine actually say, even when that is "off" or "error". |
| P4 | **Never swallow its own tap** | A tap that lands on AutoClick's own overlay would be silently wasted; the design refuses or skips instead. |
| P5 | **Quiet chrome** | Translucent glass, one accent colour, no shadows, no gradients, no decoration that competes with the app underneath. |
| P6 | **One idea per control** | One switch per outcome, one button per missing permission, one line per section. |

### 1.2 Non-goals (deliberate, not backlog)

- Multi-cell rotation, sequences, macros, recorded gesture playback.
- Sub-second intervals (floor is **1 s**).
- Conditional automation (screen reading, OCR, "tap when X appears"). AutoClick never reads the screen.
- Cloud, accounts, sync, ads, analytics.
- iOS, desktop, Play Store distribution.
- Any flow that requires the user to run `adb`, grant `WRITE_SECURE_SETTINGS`, or root the phone.

---

## 2. The target model: a 3×3 grid

### 2.1 Why a grid and not a free coordinate

A free "tap here" pin invites two failure modes: the pin is saved as absolute pixels (wrong after
rotation, split-screen or a resolution change), and the user cannot tell a friend "the third cell of
the second row". A nine-cell index is a *logical* position: it is recomputed into pixels from the
current display at every tick, and it is describable in words.

### 2.2 Geometry

The grid is applied to the **usable area**: the full display minus the status bar and the navigation
bar. Each cell is exactly one third of that area; the tap point is the exact centre of the chosen
third.

| Row-major index | Name shown to the user |
|---|---|
| 0 / 1 / 2 | Row 1, column 1 / 2 / 3 |
| 3 / 4 / 5 | Row 2, column 1 / **2 (center)** / 3 |
| 6 / 7 / 8 | Row 3, column 1 / 2 / 3 |

Cell `4` is the default, and the only cell whose name adds `(center)`.

On the reference device (1080 × 2408, usable `(0,108)–(1080,2408)`) the nine centres are
`x ∈ {180, 540, 900}`, `y ∈ {491, 1258, 2025}` — i.e. 1/6, 3/6, 5/6 of the width and the height.

### 2.3 Picking a cell, two ways

**A. The in-app grid (everyday).** A **132 dp** square at the start of a row, nine tappable thirds,
the selected cell outlined with the accent colour and its number kept visible. Beside it: the cell's
name and one line saying what will happen (`Its centre is pressed.`). Compact on purpose — this is an
*indicator of where the tap lands*, not a control surface the size of the screen.

Under the row sit **two entries**, `Pick a cell` and `Pick exact spot`. The mode is decided *before*
the screen is covered; whichever mode is live is the filled one. One generic `Pick on screen` button
plus a switch inside the picker was rejected: a control that changes the meaning of aiming does not
belong on the screen you are aiming at.

**A2. Exact spot (precision).** The card can also hold a *point* instead of a cell: the grid then
shows a **pin** at that spot and no cell is highlighted, and the card reads `Exact spot` with
`65% · 34% from the top-left of the screen`. Both entries stay visible, so switching back is just
pressing `Pick a cell`; tapping any cell in the grid does the same thing. A pin is deliberately
*not* snapped to the nearest cell: that would show a cell while the engine pressed a pixel.

**Precise points are fractions of the usable area** (`0..1` in both axes), not screen pixels, and
they are re-resolved on every tick. Rotation, a resolution change or a taller status bar therefore
moves the target with the screen instead of leaving it stranded outside. They are also *fixed screen
coordinates*: the point does not follow a page's scroll, which is why the picker says so in words.

**B. The picker (precision).** Either entry opens the same full-screen activity, already in its
mode, where the nine zones (or the exact-point layer) cover the **real** display edge to edge:

| Element | Spec |
|---|---|
| Zones | The real usable area, `2 dp` inset, nine thirds, light scrim `#99101618` over the app beneath so the target app stays readable |
| Labels | `headlineSmall` numbers; the selected zone keeps its number *under* the crosshair so it is never a blank block |
| Chrome | **One line of text and nothing else**: `Tap a third of the screen · Back to cancel` (cell) or `Tap the exact spot · Back to cancel` (exact), bottom-centre, no panel, no button, no pointer handler of its own — so the tap it explains still reaches the layer underneath. Any bar, however thin, was rejected: it sits on the screen the user is trying to see |
| Selection (Cell) | Tapping a zone writes the cell index and closes the activity immediately |
| Exact mode | The third lines stay as faint guides, the stored spot is ringed faintly, a bright crosshair follows the finger, and releasing commits the touch point as a fraction of the same usable area |
| Mode switch | **Absent inside the picker** — it was chosen by the button that opened it. No snapping either: "near a centre means the cell" would silently rewrite a deliberate pixel the user could neither see nor undo |
| Leaving | System Back. The tip says so in the same line it uses to explain the aim, which is why no `Cancel` button is needed |

Cell mode stores only the **cell index**; exact mode stores a **fraction**, never a raw pixel — so
both survive rotation. The committed touch is mapped with the window's own location on screen, so a
ROM that insets the window instead of drawing behind its bars cannot shift the target by the height
of the status bar.

---

## 3. The time model: seconds

| Property | Value |
|---|---|
| Unit | Whole **seconds**. No milliseconds, no minutes, no unit selector |
| Range | **1 s … 3600 s** (one hour) |
| Default | **1 s** |
| Presets | `1s 2s 5s 10s 30s 60s` chips, horizontally scrollable; the active one uses `primaryContainer` |
| Free entry | A 150 dp `OutlinedTextField`, numeric keyboard, digits only, max 4 characters. `isError` while non-empty and out of range. `Apply` is enabled only when the value parses, is in range, and *differs from the current value* |
| Hint | `1–3600 s` under the field — the whole validation story in five characters |
| Mid-session edits | In the panel's settings page, `−` / `+` step the interval by 1 s. A running session picks up the new value on its next tick; the tap loop keeps running while you edit |

Rationale: the owner's brief was "period magnitude should be second, default 1s", and seconds are
the only unit where an auto-tapper's behaviour is predictable to a human without arithmetic.

---

## 4. The floating hand

The hand is the app's presence. It is **not** a preference with its own switch: it exists when
*Display over other apps* is granted, and it goes away when that permission goes away. Two controls
for one outcome would eventually disagree (this was tried and removed — see PLAN ADR-016).

### 4.1 Form

| Property | Spec | Reason |
|---|---|---|
| Size | **56 dp** circle | Big enough to hit with a thumb, small enough to leave the grid usable |
| Fill, idle | `surface` at **α 0.78** | Reads as glass over the host app, not a sticker |
| Fill, running | `primary` at **α 0.80** | The whole disc becomes the state signal |
| Border | 1 dp `outlineVariant` at α 0.55 | Defines the edge on light and dark hosts without a shadow |
| Shadow | **none** | Compose paints a shaped shadow as an elevation halo around the *view*; around a circle that reads as a pale rectangular plate. Removed on purpose |
| Glyph | Hand, 24 dp, in `onPrimary` (running) / `onSurface` (idle) | — |
| Label | Under the glyph, **9 sp** bold, letter spacing 0.6: `ON` / `OFF` / `PAUSED` | A dot alone never told the user whether the loop was live, and vanished when idle. `PAUSED` is `#B45309` |
| Semantics | `AutoClick is tapping. Tap to open controls.` / `AutoClick finished its tap budget. Tap to open controls.` / `AutoClick is idle. Tap to open controls.` | Screen readers get the state *and* the affordance |

### 4.2 Behaviour

| Gesture | Result |
|---|---|
| Tap | Opens the panel (controls page) |
| Drag | Moves the hand 1:1 with the finger. Drag begins only after the system touch slop, so a drag is never mistaken for a tap and vice versa |
| Release | Snaps to the nearer screen edge, **12 dp** margin, **140 ms** animation; vertical position is kept exactly |
| Outside the screen | Clamped to the usable area; the hand can never be parked half off-screen |
| Restart | Position is remembered (DataStore) and restored on the next launch / service restart |
| First appearance | Default is the right edge less 12 dp, at 35 % of the screen height; after that the saved `bubble_x` / `bubble_y` is restored |

### 4.3 What the hand is allowed to do to the tap

| Situation | Behaviour |
|---|---|
| Hand is **not** over the target | Normal ticking |
| Hand **is** over the target when Start is pressed | Start is **refused**, state `Error`, message `The floating hand covers your target. Drag it away, then start again.` plus a tip `Tip: drag the hand to the opposite edge, then press start again.` |
| Hand is dragged onto the target **while running** | That tick is **skipped and counted** (`n skipped`), never dispatched. The gesture is never sent into our own window |

That last row is the difference between an auto-tapper that looks broken and one that behaves:
injecting a touch into a touchable overlay would consume it silently.

---

## 5. The panel

Tapping the hand expands a panel **next to** it (never on top of the target if avoidable). It has two
pages and no other chrome.

### 5.1 Controls page

| Element | Spec |
|---|---|
| Window | 212 dp wide, height wraps content, separate overlay window created only while expanded |
| Card | `RoundedCornerShape(20.dp)`, `surface` at **α 0.90**, 1 dp `outlineVariant` at α 0.70, **no shadow**, no dim-behind |
| Row | Three 46 dp circular buttons, evenly spaced |
| Button 1 | Play / Stop. `primaryContainer` when idle, `errorContainer` when running. Semantics: `Start tapping` / `Stop tapping` |
| Button 2 | Sliders glyph, `surfaceVariant`. Opens the settings page. Semantics: `Settings` |
| Button 3 | **Close glyph (X)**, `surfaceVariant`. Closes the panel back to the hand. An X, not a chevron: a chevron promises "more below", which the panel does not have. Semantics: `Close panel` |
| Error line | **Nothing by default.** The three buttons already say what the panel can do, so a permanent status line was pure repetition and height. A line appears *only* when a failure needs words (`labelSmall`, `error` colour, max 2 lines, ellipsised) |

Auto-minimise: **160 ms after a *successful* Start** the panel collapses itself. A *refused* start
leaves the panel open with the reason on the error line — folding it away would hide the only
explanation.

### 5.2 Settings page (inside the panel)

Settings live in the panel because a background service cannot reliably bring an activity to the
front on several OEM builds, and because retargeting mid-run should not throw the user out of the app
they are tapping.

| Element | Spec |
|---|---|
| Header | `Settings` (titleSmall, semibold) on the left; `Open` and `Done` pills on the right, 6 dp apart |
| `Open` | Outlined pill (`1 dp outlineVariant`, `onSurface` label): collapses the panel, then launches the full app |
| `Done` | Filled `primaryContainer` pill: returns to the controls page |
| Target | A nine-grid filling the panel width, **150 dp** tall. In exact mode it carries the same pin at the same fraction and no cell is highlighted; tapping any cell switches back to coarse aiming |
| Interval | `Every` label, `−` / `+` 38 dp step buttons around a 46 dp read-out (`3s`), clamped at 1 s and 3600 s (disabled, not hidden) |
| Summary | **none** — the grid shows the cell, the stepper shows the seconds; a `Row 3, column 2 · 5s` echo was the third copy of the same two facts |

### 5.3 Anchoring rules (the panel must stay beside the hand)

| Rule | Value |
|---|---|
| Horizontal gap | **8 dp** from the hand's disc edge, flipped to the left side when there is no room on the right |
| Vertical | Panel is vertically **centred on the hand** (`y = handCentre − panelHeight/2`) |
| Clamp | Panel is kept inside the usable area; at the very bottom edge the page is clamped to the screen bottom, which is correct and not a bug |
| Re-anchor triggers | Mode swap (controls ⇄ settings), any measured height change, drag in flight (throttled to **48 ms**), drag release, edge-snap animation end |
| Why a layout listener | A `post {}` fires before the next frame's measure and would read the *old* height; only `OnLayoutChangeListener` is ordered after layout |

Measured on the reference device: 24 px (= 8 dp at density 3.0) horizontal gap and **0 px** vertical
centre delta across controls, settings, drag-with-settings-open, and 10 alternating `Done ⇄ settings`
cycles.

---

## 6. The app screen

One scrolling page, top to bottom, 20 dp horizontal padding, 14 dp between cards. Card style is
uniform: `RoundedCornerShape(20.dp)`, `surface`, 1 dp `outlineVariant`, **0 elevation**, 16 dp inner
padding, title `titleMedium`, subtitle `bodySmall` in `onSurfaceVariant`.

| # | Section | Content |
|---|---|---|
| 1 | Header | 48 dp `primary` disc with the hand glyph + `AutoClick` (`headlineSmall`) + `Nine-grid auto tap` |
| 2 | **Status** | State dot + one of `Tapping` / `Paused` / `Finished` / `Needs attention` / `Idle`; right side `Every 3s`; **no detail paragraph** — the word and the button already say everything, and a failure sentence appears only on error; the primary **Start tapping / Stop tapping** button (52 dp, full width) |
| 3 | Permission CTA (only while something required is missing) | `Tapping needs both of these:` then one orange button per missing requirement, in fixed order: `Enable Accessibility`, `Enable Display over other apps` |
| 4 | **Before you start** *(while incomplete)* | The four permission switches, expanded, directly under Status |
| 5 | **Where to tap** | 132 dp nine-grid + `Row 2, column 2 (center)` + `Its centre is pressed.`, then `Pick a cell` / `Pick exact spot`; in exact mode a pin instead of a highlight and `Exact spot` + `65% · 34% from the top-left of the screen` |
| 6 | **Interval** | Preset chips, seconds field + `Apply`, `1–3600 s`, a divider, then **Stop after** chips `Never / 100 / 500 / 1000` and one explanatory line |
| 7 | **Advanced** | Folded by default; `Vibrate` + `Flash marker` |
| 8 | **Before you start** *(when complete)* | The same card, now collapsed to `All set`, at the bottom of the page |
| 9 | Footer | `Taps are sent through the accessibility service. Nothing is read from the screen and nothing leaves the phone.` |

### 6.1 The moving permission card

The permission block is **one** composable rendered in **one of two places**:

| State | Placement | Presentation |
|---|---|---|
| Any of the four off | Directly under Status | Expanded, badge `n of 4` |
| All four on | Last section, above the footer | Collapsed, badge `All set` |

It re-folds/unfolds automatically the moment the count changes (`remember(allPermissions)`), so the
page always opens with the thing worth acting on at the top, and never nags once there is nothing to
do.

### 6.2 Status dot semantics

| State | Dot | Headline |
|---|---|---|
| Running, not paused | `#15803D` green | `Tapping` |
| Running, paused (screen off) | `#B45309` amber | `Paused` |
| Error | `error` | `Needs attention` |
| Finished | `#15803D` green | `Finished` |
| Idle | `onSurfaceVariant` | `Idle` |

---

## 7. Permissions & first run

Two permissions are **required**; two are **comfort**. Android will not let an app grant or revoke
any of them, so every control is a *remote control for the system screen that owns the switch*, and
the row keeps reporting the system's answer (polled every second).

| # | Row label | Hint (one line) | Class |
|---|---|---|---|
| 1 | `Display over other apps` | `Shows the floating hand.` | **required** |
| 2 | `Accessibility: AutoClick taps` | `Sends the taps.` | **required** |
| 3 | `Notifications` | `Status line with a Stop button.` | optional |
| 4 | `Battery: unrestricted` | `Keeps the service alive.` | optional |

Card header: `Before you start`; subtitle `Two are required. Tap a switch to open its system screen.`;
badge `n of 4` or `All set`; an `Optional` caption separates rows 3–4 from rows 1–2.

### 7.1 Switch behaviour

| Row | Switch on → | Switch off → |
|---|---|---|
| Display over other apps | `ACTION_MANAGE_OVERLAY_PERMISSION` with `package:` (vivo: "Floating window" list) | Same screen — the only one that can turn it off |
| Accessibility | Accessibility list, scrolled to the `AutoClick taps` row and highlighted | Same highlighted list |
| Notifications | `POST_NOTIFICATIONS` runtime dialog | `ACTION_APP_NOTIFICATION_SETTINGS` for this package |
| Battery | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog | The exemption list — the only place an exemption can be removed |

Both the row *and* the switch are tappable. The switch never optimistically flips: it shows the
system's answer, so a tap leaves the app and returns to the truth.

### 7.2 The orange buttons

Only required permissions produce a button, and the label names the destination:

| Missing | Button | Opens |
|---|---|---|
| Accessibility | `Enable Accessibility` | Accessibility list with the row pre-scrolled and pulsing |
| Display over other apps | `Enable Display over other apps` | Overlay permission screen for this package |
| Both (fresh install) | Both, stacked, under `Tapping needs both of these:` | — |

Colour `#C2410C` (a warning amber read as brown at 15 sp on white), label `labelLarge` white, 12 dp
vertical padding.

### 7.3 The accessibility hand-off (why a Toast)

The accessibility screen is long and the app's own row is far down, so the deep link asks Settings
itself to scroll to and highlight it. Settings deliberately waits ~1.5 s before it does, and during
that gap a user reasonably concludes the tap did nothing. The design answers with **two short
messages**, drawn by the system, at the top of the screen:

| Moment | Text |
|---|---|
| The page opens (our activity goes to the background) | `Finding "AutoClick taps" — please wait` |
| +2.4 s | `Click the highlighted row` |

Why not a nice floating pill: the accessibility screen sets
`PRIVATE_FLAG_FORCE_HIDE_NON_SYSTEM_OVERLAY_WINDOWS`, so every app overlay — pill, and the floating
hand alike — is force-hidden on that screen. A Toast is rendered by the system UI process, so it
survives; and the background-Toast throttle does not apply because this app holds
`SYSTEM_ALERT_WINDOW`.

The second line is skipped if the user already came back. If a ROM ignores the scroll request, the
app falls back to the plain list and says so: `Scroll to "Downloaded services" — first row`.

Two details worth keeping in the UI:

- **Restricted settings.** When Accessibility is off, the card shows one line:
  `Greyed out? App info › Allow restricted settings.` — Android 13+ blocks the accessibility toggle
  for sideloaded apps until that is allowed.
- **The OEM consent sheet.** After flipping the switch, vivo (and others) show their own
  *"is requesting to monitor and control device"* sheet; `Allow` is required before the service binds.
  Nothing the app can pre-empt, so it is documented rather than fought.

### 7.4 First-run sequence, as designed

1. Install → open → `0 of 4`, Start disabled, two orange buttons.
2. `Enable Accessibility` → list scrolls to the row, two Toasts explain the wait → tap the row →
   OEM sheet → `Allow` → back in the app: `1 of 4`, Start still disabled.
3. `Enable Display over other apps` → vivo's Floating window list → flip AutoClick's switch → back:
   `2 of 4`, **Start enabled**, no orange buttons, hand appears showing `OFF`.
4. Start → hand turns accent with `ON`, panel folds away 160 ms later, counters tick.
5. Optionally walk rows 3 and 4; the card drops to the bottom and collapses to `All set`.

---

## 8. Feedback language

### 8.1 What the user sees while it runs

| Surface | Text |
|---|---|
| Hand label | `ON` / `OFF` / `PAUSED` |
| Panel status | **none** while idle, running or finished — the play/stop glyph carries the state; the failure sentence appears only on error |
| App status detail | Same counters; `Waiting for the screen to unlock (n taps so far)` while paused |
| Notification title | `AutoClick — tapping` / `AutoClick — idle` / `AutoClick — stopped` |
| Notification text | `Ready · every 3s` / `Paused while the screen is off · 5 taps so far` |
| Notification action | One contextual action: `Stop` while running, `Start` while idle |

Pluralisation is explicit: `1 tap` / `n taps`. No `1 taps`, anywhere.

### 8.2 Failure copy (one source, used by hand, panel, app and notification)

| Reason | Sentence |
|---|---|
| `NO_ACCESSIBILITY` | `Accessibility is off. Enable Settings › Accessibility › AutoClick taps.` |
| `NO_OVERLAY_PERMISSION` | `Floating window permission is off. Allow "Display over other apps".` |
| `SERVICE_DISCONNECTED` | `The accessibility service disconnected. Re-enable it to keep tapping.` |
| `DISPLAY_UNAVAILABLE` | `Screen size could not be read. Turn the screen on and try again.` |
| `TARGET_COVERED_BY_BUBBLE` | `The floating hand covers your target. Drag it away, then start again.` |

### 8.3 Touch feedback (optional, both on by default)

| Toggle | Effect | Spec |
|---|---|---|
| `Vibrate` — "A short buzz on every tap." | 30 ms one-shot at default amplitude | Only if the device has a vibrator |
| `Flash marker` — "A ring at the point you just tapped." | A 30 dp crosshair ring appears at the exact tap point and fades out | Teal ring `#2DD4BF`, white dot, four teal ticks; fades α 1 → 0 over **260 ms**; never touchable, never focusable |

The marker exists because a tap somewhere else on the screen is otherwise invisible: it is the user's
proof the loop is landing where they think.

---

## 9. Visual system

### 9.1 Colour

Light theme (dark theme mirrors the same roles):

| Token | Value | Use |
|---|---|---|
| `primary` | `#0F766E` | Accent, running states, selected chips |
| `onPrimary` | `#FFFFFF` | Glyphs on accent |
| `primaryContainer` | `#CCFBF1` | Start button, `Done` pill, active preset chip |
| `onPrimaryContainer` | `#042F2E` | Text on that container |
| `secondary` / `secondaryContainer` | `#475569` / `#E2E8F0` | Neutral supporting roles |
| `background` | `#F4F7F6` | App page |
| `surface` | `#FFFFFF` | Cards, panel, hand (idle) |
| `surfaceVariant` | `#E6EFEC` | Panel's secondary buttons, step buttons |
| `onSurface` / `onSurfaceVariant` | `#111817` / `#445250` | Text / secondary text |
| `outline` / `outlineVariant` | `#9AA9A5` / `#CBD9D5` | Hairlines and card borders |
| `error` / `errorContainer` | `#B3261E` / `#F9DEDC` | Errors, Stop button |

Fixed accents outside the scheme: `#C2410C` (enable-permission button), `#B45309` (paused),
`#15803D` (running dot), `#D9D9D9` (system switch track), `#2DD4BF` (crosshair ring),
`#18717A` (app icon plate).

### 9.2 Type

Material 3 defaults, no custom font. Roles actually used: `headlineSmall` (app title), `titleMedium`
(card titles, status headline), `titleSmall` (panel settings header, cell name), `bodyMedium`
(counters, row labels), `bodySmall` (subtitles, hints, footer), `labelMedium` (badges, pills),
`labelSmall` (panel status, section labels), plus the hand's `9 sp` label.

### 9.3 Shape, elevation, glass

| Surface | Shape | Elevation | Fill |
|---|---|---|---|
| App cards | 20 dp | **0** | `surface`, 1 dp `outlineVariant` border |
| Panel / settings sheet | 20 dp | **0** | `surface` α 0.90 + 1 dp `outlineVariant` α 0.70 |
| Hand | circle | **0** | see §4.1 |
| Pills (`Open`, `Done`) | 12 dp | 0 | outlined / `primaryContainer` |
| Picker hint bar | 16 dp | 0 | `#F2101A1C` on a `#99101618` scrim |

Two rejected looks are recorded so they are not reintroduced: α 0.95 read as solid white ("still
opaque"), and α 0.78 let the host app's text ghost through the card ("looks like a rendering fault").
0.90 is the value that reads as glass without ghosting. Real backdrop blur is unavailable on the
reference ROM (`mBlurEnabled=false`), and a `FLAG_DIM_BEHIND` scrim was removed because the user read
it as "a shadow on the background".

### 9.4 The app icon

| Layer | Spec |
|---|---|
| Plate | Squircle, `#18717A` |
| Glyph | White tap-hand, index finger extended, a ripple ring + dot on the fingertip |
| Rotation | Glyph rotated **22° clockwise** — a vertical hand read as a "stop" gesture, the tilt reads as a tap |
| Scale | Glyph ≈ **50 %** of the legacy plate; adaptive foreground **44 %** of the 108 dp canvas, inside the 72 dp safe circle |
| Density assets | `mipmap-{mdpi..xxxhdpi}/ic_launcher.png` (48–192 px) for launchers that ignore adaptive icons |
| Adaptive | `mipmap-anydpi-v26/ic_launcher.xml` → `background` = `@color/ic_launcher_background`, `foreground`/`monochrome` = the same hand bitmap |
| Legacy plate colour | `#18717A`, defined in `values/ic_launcher_background.xml` |

---

## 10. Copy inventory

All user-facing strings, in one place, so new copy does not drift in tone.

**App**

- `AutoClick` · `Nine-grid auto tap`
- `Status` · `Tapping` · `Paused` · `Finished` · `Needs attention` · `Idle` · `Every 3s`
- `Start tapping` · `Stop tapping`
- `Tapping needs both of these:` · `Enable Accessibility` · `Enable Display over other apps`
- `Before you start` · `Two are required. Tap a switch to open its system screen.` · `n of 4` · `All set`
- `Optional` · `Display over other apps` · `Accessibility: AutoClick taps` · `Notifications` · `Battery: unrestricted`
- `Shows the floating hand.` · `Sends the taps.` · `Status line with a Stop button.` · `Keeps the service alive.`
- `Greyed out? App info › Allow restricted settings.`
- `Where to tap` · `A cell centre, or any spot you point at.` · `Pick a cell` · `Pick exact spot` · `Exact spot` · `65% · 34% from the top-left of the screen`
- `Interval` · `Seconds to wait between two taps.` · `Seconds` · `Apply` · `1–3600 s` · `1s 2s 5s 10s 30s 60s`
- `Stop after` · `Never` · `100` · `500` · `1000` · `Runs until you stop it.` · `Stops by itself after 500 taps.`
- `Advanced` · `Feedback while it runs. Both on by default.` · `All on` / `Some off`
- `Vibrate` · `A short buzz on every tap.` · `Flash marker` · `A ring at the point you just tapped.`
- `Taps are sent through the accessibility service. Nothing is read from the screen and nothing leaves the phone.`
- `Waiting for the screen to unlock (4 taps so far)` · `51 taps delivered · 0 skipped`
- `Tip: drag the hand to the opposite edge, then press start again.`

**Panel**

- `Settings` · `Open` · `Done` · `Target cell` · `Every`
- Status detail: **absent** by design; only a failure sentence is rendered (`Needs attention` state)
- Content descriptions: `Start tapping` / `Stop tapping`, `Settings`, `Close panel`,
  `Open the app`, `Close settings`, `Decrease interval`, `Increase interval`

**Picker**

- `Tap a third of the screen · Back to cancel` · `Tap the exact spot · Back to cancel`
- nothing else — no chips, no bar, no background: the picker is the screen

**System surfaces**

- Accessibility service label `AutoClick taps`; description: `AutoClick uses this service to send a
  single tap at the screen position you chose, repeating on the interval you set. It never reads what
  is shown on your screen and never sends your data anywhere.`
- Notification channel `AutoClick status` — `Shows whether AutoClick is tapping, with stop controls.`
- Notification titles `AutoClick — tapping` / `AutoClick — idle` / `AutoClick — stopped`; actions `Stop` / `Start`
- Hand-off Toasts `Finding "AutoClick taps" — please wait` · `Click the highlighted row`
- Fallback Toast `Scroll to "Downloaded services" — first row`

**Rules the copy follows**

1. One line per section, one line per permission. Nothing repeats the README.
2. A sentence either says what a thing *is* or what to *do*, never both.
3. No exclamation marks, no "simply", no "just".
4. Failures name the fix, not the mechanism.
5. Numbers are concrete (`1–3600 s`, `n of 4`), never "a few".

---

## 11. States and transitions (as the user experiences them)

| From | Event | To | What the user sees |
|---|---|---|---|
| Idle | Start (gates pass) | Running | Hand → accent + `ON`, panel folds after 160 ms, counters start |
| Idle | Start (gate missing) | Error | Hand stays `OFF`, message names the missing permission, orange button visible |
| Idle | Start (hand over target) | Error | Message + tip, hand unchanged, no tap sent |
| Running | Stop (panel, app or notification) | Idle | Hand → `OFF`, crosshair removed, counters frozen for good |
| Running | Screen off | Running/paused | Hand shows `PAUSED`, app says `Waiting for the screen to unlock`, notification says `Paused while the screen is off` |
| Paused | Unlock | Running | Resumes on the same interval; no burst of catch-up taps |
| Running | Hand dragged onto target | Running (skipping) | `n skipped` climbs, taps keep going elsewhere |
| Running | Accessibility revoked / service killed | Error | `Needs attention`, scheduler stopped, sentence names the fix |
| Running | Delivered tap reaches the budget | Finished | Hand → `DONE` (green), scheduler stopped, counters frozen, `Finished · n taps delivered · press Start to run again` |
| Finished | Start | Running | Fresh counters, budget re-armed |
| Error | User fixes it and presses Start | Running | Normal run |

Every surface renders from one state object, so the hand, the panel, the notification and the app can
never disagree.

---

## 12. Edge behaviour that is designed, not accidental

| Situation | Designed behaviour |
|---|---|
| Screen off / locked | Pause. AutoClick does not tap a black screen |
| Unlock | Resume on the original interval, no catch-up burst |
| Rotation while running | The same *logical* cell is re-solved from the new metrics on the next tick |
| Split-screen / resized window | The grid follows the display, because the grid is a screen grid, not an app layout |
| Stop | Immediate and final: schedule cancelled, in-flight gesture result ignored by generation, crosshair dismissed |
| Process frozen longer than one interval | The schedule re-bases; missed ticks are dropped and counted, never replayed |
| Reinstall / update | Accessibility unbinds (system behaviour) and, on some ROMs, the overlay permission resets; the app reports `n of 4`, never a mystery |
| Overlay permission missing but Accessibility granted | The loop still runs headless, with the notification as the only status surface; no hand, no lie |
| Language | English only, by decision. No `values-zh`, no locale switcher |

---

## 13. Accessibility of AutoClick's own UI

| Element | Provision |
|---|---|
| Hand | `contentDescription` carries the run state + the tap affordance |
| Panel buttons | Every icon button has a `contentDescription` (`Start tapping`, `Settings`, `Close panel`) |
| Pills / step buttons | `Open the app`, `Close settings`, `Decrease interval`, `Increase interval` |
| Cells | Number labels are always drawn; the selected cell keeps its number under the crosshair |
| Contrast | Accent `#0F766E` on white and white on accent both clear 4.5:1; panel text sits on α 0.90 surface, which keeps it above 4.5:1 over busy hosts |
| Font scale | Cards and panel wrap; the panel re-anchors on any height change, which also covers a larger font |

---

## 14. Design decisions worth knowing before changing anything

1. **The hand is not a setting.** It follows the overlay permission and nothing else. Adding a "show
   hand" switch reintroduces the state that can disagree with the permission.
2. **Settings are in the panel.** Not because a modal is nicer, but because a background service
   cannot reliably raise an activity, and because retargeting should not interrupt the tapped app.
   The `Open` pill is the escape hatch to the full app.
3. **No shadow, ever.** Compose's `shadowElevation` inside a WindowManager window paints a plate, not
   a shadow. It was read as "a non-transparent background" twice.
4. **No dim-behind scrim.** It was read as "a shadow on the background".
5. **The panel re-anchors on layout, not on intent.** Anything that repositions from a `post {}` will
   read stale height and drift by half the delta.
6. **Start refuses; it never "tries anyway".** A refused start is a sentence the user can act on; a
   start that silently swallows its taps is a bug report.
7. **Two Toasts, not one.** One Toast ends at ~3.8 s; the accessibility scroll lands at ~2.5 s. A
   single line would end before the thing it announced.
8. **The nine-grid in the app is small.** It is an indicator. The full-screen picker is where aiming
   happens.
