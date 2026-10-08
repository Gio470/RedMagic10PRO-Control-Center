# 🚀 RedMagic Control Center

Root hardware and system control for the **RedMagic 10 Pro (NX789J)** running RedMagicOS.

Talks to the phone's own sysfs and `/proc` nodes directly — the fan, the LED rings, the shoulder
triggers, the Magic Key, the haptic driver — and pairs that with an optional Xposed module for the
things that live inside other apps.

| | |
|---|---|
| **Package** | `com.redmagic.control` |
| **Version** | 1.0.0 (1) |
| **Min Android** | 9 (API 28) · built against API 36 |
| **Needs** | Root (Magisk / KernelSU). LSPosed is optional — see below. |

---

## ⚠️ Before you start

This writes to hardware nodes as root. It is built and tested against **one** phone, the RedMagic
10 Pro; the node paths are specific to that device and its ROM. On anything else the app will tell
you it is an unsupported device and hold the hardware tabs shut rather than guess.

Nothing here is reversible by uninstalling — settings written to the system (disabled packages,
display density, the immersive state) outlive the app. Every switch that changes something outside
the app can put it back, so use those rather than removing the app and hoping.

---

## 📱 What's in it

### Home

A live dashboard rather than a menu.

- **Status strip** — the three things that have to be true for everything to work: the right phone,
  root, and the Xposed module. Collapses to a single line when all three pass; expands and turns
  red naming what is missing when one does not. Tap to re-check.
- **Temperature and Fan dials** — an arc each, because a temperature is a position in a range
  before it is a number. Temperature colours itself cool / warm / hot. The fan's arc tracks its
  *level* rather than its RPM: nothing on the phone reports a maximum RPM, so an arc scaled to a
  guess would be an arc that lies.
- **Processor** — current load, the last forty readings as a graph (throttling is a shape over time,
  not a number), and a bar per CPU cluster showing its clock against its ceiling.
- **Memory** — RAM and swap.

### Hardware

Each section is gated by its own master switch and folds away when off.

| Section | What it does |
|---|---|
| **Fan** | Speed 0–5, preset curves (Quiet / Balanced / Turbo), and an automatic mode that follows temperature |
| **Magic Key** | Remaps the side slider: camera, Game Space, sound mode, flashlight, voice recorder, or launch any app |
| **Triggers** | Maps the shoulder triggers to quick actions, with an intent-unlock mode |
| **Game trigger mapping** | Per-game L/R touch targets placed over the running game, portrait/landscape layouts, single touch, long press, rapid fire, haptics, and saved targets |
| **Haptics** | Trigger feedback, with a strength slider from off to max |
| **LED zones** | Colour and effect for the fan ring and the back logo, with live preview |
| **Game Mode** | A hardware profile applied automatically while one of your games is in front |
| **Call lighting** | Profiles for incoming and connected calls, optionally pausing the fan |
| **Charging mode** | Fan and LED behaviour while plugged in |
| **Display density** | Screen density override, with a confirmation dialog that reverts itself |

### Software

Everything this app changes about *other* software.

**Needs the Xposed module** (greyed out without it):

- **Floating window tweaks** — allow any app in a floating window, keep minimised windows usable,
  keep windows where you drop them, allow them off screen, stop drag-to-split, and set your own
  window limit
- **Global icon pack** — apply an icon pack everywhere, with a monochrome fallback; a separate
  root-only switch next to it flips the launcher's own mono-icon mode
- **GameAssist / GameSpace** — global Game Mode, hide the Energy Cube, Super Resolution, and more
- **Volume Step Control** — change media volume by more than one level per button press
- **Launcher recents** — hide a third-party launcher's HOME card from recents, and set it as the
  Home app for gesture navigation

**Needs only root:**

- **System theme** — recolours the whole phone's Material You palette from a picked colour or the
  wallpaper, ported from ColorBlendr
- **Performance mods** — a cache cleaner and a GMS Optimizer toggle for Google Play Services
- **Block system updates** — switches off ZTE's ZDM updater stack. An OTA replaces the boot image
  and takes root with it

### Settings

Theme mode, Material You dynamic colour (or a picked seed colour), pure black, docked vs. floating
navigation bar, the liquid-glass bottom bar with a live **Glass playground** to tune its blur,
tint, refraction and depth by eye, card appearance (a blur-behind-cards slider and a toggle to
connect each group of settings into one continuous card), background animations, °C/°F, and three
separate refresh intervals (hardware status, processor, memory) since they cost very different
amounts to poll.

**Diagnostics** lives here too: a crash report and a log of every hardware write with the call stack
behind it, both copyable to the clipboard without adb or a terminal on the phone.

---

## 🧩 The Xposed module

Root alone cannot reach inside other apps, so the floating-window patches, the icon pack and the
GameAssist tweaks run as an LSPosed module shipped inside this same APK.

1. Install the app
2. Enable **Redmagic Control Center** in LSPosed
3. Leave the default scope — it already asks for System Framework, the launcher, SystemUI and
   Settings
4. **Reboot** — hooks are installed once at boot and cannot be added later

Home's status strip reports whether the module actually loaded *this* boot, not whether it once did:
the report carries the kernel's boot id, so a module switched off in LSPosed reads as off rather
than showing you last week's success. Settings changes apply to the next window without another
reboot; only enabling the module needs one.

---

## 🔨 Building

```bash
./gradlew :app:assembleDebug
```

For a small, installable phone-test APK with code and resource shrinking:

```bash
./gradlew :app:assemblePreview
```

The APK is written to `app/build/outputs/apk/preview/app-preview.apk`. CI uploads it as
`redmagic-optimized-apk` before running regression tests. For a production release:

```bash
./gradlew :app:assembleRelease
zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
apksigner sign --ks <your.keystore> --out RedMagicControl.apk aligned.apk
```

`assembleRelease` signs automatically instead when `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` are set. CI uses exactly this: pushing a `vX.Y.Z` tag
builds a signed release APK from those same secrets and attaches it to a GitHub Release automatically.

The Kotlin namespace is `com.elitedarkkaiser.redmagic` while the application id is
`com.redmagic.control`. That is deliberate: the namespace is where every source file lives and what
`R` and `BuildConfig` generate into, and it has no bearing on what the package manager calls the app.

---

## 💬 Discussion

- Telegram: [@RedMagic10Pro](https://t.me/redmagic10prochat)

---

## 🙏 Credits

This app stands on a lot of other people's work.

| Project | What came from it |
|---|---|
| [austineyoung2000/Redmagic-Control-Center](https://github.com/austineyoung2000/Redmagic-Control-Center) | The OG project that made this possible |
| [austineyoung2000/Redmagic-11-Toolbox](https://github.com/austineyoung2000/Redmagic-11-Toolbox) | Native Touch-Game-Key mapping, profile storage, and documented vendor behavior; firmware transaction IDs are discovered on the installed phone |
| [Gio470/FixRedMagicWindow](https://github.com/Gio470/FixRedMagicWindow) | The floating-window hooks |
| [RichardLuo0/global-icon-pack-android](https://github.com/RichardLuo0/global-icon-pack-android) | The global icon pack, id-rewriting trick and all |
| [RohitKushvaha01/TaskManager](https://github.com/RohitKushvaha01/TaskManager) | The processor and memory readings |
| [Mahmud0808/ColorBlendr](https://github.com/Mahmud0808/ColorBlendr) | System theme's Material You recolouring |
| [khanhnguyen9872/NubiaToolkit](https://github.com/khanhnguyen9872/NubiaToolkit) | Hardware node reference |
| [Gio470/NPatch](https://github.com/Gio470/NPatch) · [MorpheApp/morphe-manager](https://github.com/MorpheApp/morphe-manager) · [alesimula/Murine-launcher](https://github.com/alesimula/Murine-launcher) | UI patterns the screens are modelled on |
| [Font Awesome Free](https://github.com/FortAwesome/Font-Awesome) | Every icon in the app. Icons CC BY 4.0, font SIL OFL 1.1 — full licence ships in the APK's assets |

Where a port deviates from its upstream, the reason is written down at the top of the file that does
it — usually because this app has root and the original had to work without it, or the other way
round.
