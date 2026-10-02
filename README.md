# GestureLink

[![PC server tests](https://github.com/SAMEER-KHAN-1/Gesture-Link/actions/workflows/pc-server-tests.yml/badge.svg)](https://github.com/SAMEER-KHAN-1/Gesture-Link/actions/workflows/pc-server-tests.yml)
[![Android build](https://github.com/SAMEER-KHAN-1/Gesture-Link/actions/workflows/android-build.yml/badge.svg)](https://github.com/SAMEER-KHAN-1/Gesture-Link/actions/workflows/android-build.yml)

Control your Windows PC from your Android phone over your local network. A small always-on tray service runs on the PC, and a native Android app is the remote: power, volume and media, a touchpad and keyboard, a file browser, a live view of the screen, and more.

No cloud, no account: the phone talks straight to the PC over your Wi-Fi.

<!-- Screenshots: add images to docs/screenshots/ and link them here, e.g.
![Dashboard](docs/screenshots/dashboard.png)  ![Touchpad](docs/screenshots/touchpad.png) -->

## Features

**Power and connectivity**
- Shut down, restart, sleep or lock the PC, and cancel a pending shutdown
- Turn the Wi-Fi adapter and Bluetooth radio on or off (the switches show their real state)
- Wake a shut-down PC with Wake-on-LAN (see the PC's [setup notes](pc-server/README.md))

**Dashboard**
- Live CPU, RAM, disk, battery and uptime, plus the connection's ping latency
- Built-in screen brightness slider (laptop panels only; hidden if the display doesn't support it)
- Volume, mute, play/pause and next/previous track
- Alerts from the PC (currently a low-battery warning), kept in a history screen

**Remote input**
- Touchpad with adjustable sensitivity: tap, right-click, double-click, scroll, and a drag lock for moving windows and selecting text
- Type text on the PC, or send Enter / Backspace / Escape
- One-tap keyboard shortcuts (copy/paste/undo, Alt+Tab, show desktop, Task Manager, ...)
- A presentation remote with big next/previous buttons

**Screen, apps and processes**
- View the PC's screen (manual refresh or live), with pinch to zoom
- Browse and launch the PC's apps
- See running processes, biggest memory use first, and force-end one (critical Windows processes are protected)

**Clipboard and files**
- Send the phone's clipboard text to the PC, or copy the PC's onto the phone
- Browse the PC's drives and folders, with bookmarks
- Download a file to the phone, or upload one or several to the folder you're viewing (15 MB per file)
- Create folders; rename or delete (to the Recycle Bin) with a long-press

**Pairing**
- Scan the QR code from the PC's tray icon, or type its IP and token
- The phone remembers the PC and reconnects automatically

## Status

Feature-complete for v1 and covered by automated tests (the PC server's pytest suite and the Android app's JVM unit tests, both run by GitHub Actions on every push), but **not yet verified on a real phone**. The Android app was written without an Android SDK to hand, so its first full compile is the Android CI job; expect some rough edges until it has been run on a device. Known limits are listed at the bottom.

## Setup

You need a Windows PC and an Android phone (Android 8.0 or newer) on the same Wi-Fi network.

### 1. PC server

Requires Python 3.12 or newer.

```
cd pc-server
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python run.py
```

A tray icon appears near the clock. Windows Firewall will ask whether to let Python (or `GestureLink.exe`) accept connections: allow it on **private networks**, otherwise the phone can't reach it. To run it without a terminal, build a standalone `GestureLink.exe` (see [pc-server/README.md](pc-server/README.md#packaging-to-a-standalone-exe)) and turn on "Start with Windows" in the tray menu.

To check it's up, open `http://<pc-ip>:8765/health` in a browser on the same network.

### 2. Android app

Open the `android-app/` folder in Android Studio (Koala or newer), let it sync, and run it on your phone with USB debugging enabled, or build the APK with `Build > Build APK(s)`. See [android-app/README.md](android-app/README.md) for the one-time Gradle wrapper note.

### 3. Pair

Right-click the tray icon and choose **Show pairing QR code**, then tap **Scan QR code** in the app and **Connect**. Or enter the PC's IP address and the pairing token from the tray menu by hand (add `:port` if you changed the port from 8765). After the first time, the app reconnects on its own.

## How it works

The PC service (`pc-server/`, Python + FastAPI) exposes a WebSocket endpoint on your network, guarded by a pairing token generated on first run. The app (`android-app/`, Kotlin + Jetpack Compose) connects, and sends small JSON commands (`shutdown`, `mouse_move`, `list_dir`, ...). The PC carries them out through Windows APIs (via `ctypes`) or PowerShell, and replies on the same socket. It can also push messages to the phone, like a low-battery alert.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design, the security model and the full command protocol. If something doesn't work, see [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).

## Security

GestureLink is a remote for your own devices on a trusted home network, not a hardened internet service.

- Every command must carry the pairing token, and 5 wrong tokens from one address locks it out for 30 seconds.
- Traffic is plain `ws://` (not encrypted), so don't use it on a network you don't trust, and never port-forward it to the internet. For access away from home, use your own VPN.
- If the token leaks, use **Regenerate pairing token** in the tray menu.

## Known limits

- Windows PCs only.
- Downloading a file to the phone needs Android 10 or newer.
- Files larger than 15 MB can't be transferred.
- Brightness control works on laptop panels, not most external monitors.
- Wake-on-LAN needs a wired PC with the feature enabled in its BIOS and network adapter settings.
- Uploading a file replaces any existing file with the same name.

## Project structure

```
GestureLink/
├── pc-server/       # Python tray service that runs on the Windows PC
├── android-app/     # Kotlin/Jetpack Compose Android app
├── docs/            # Architecture notes, protocol spec, troubleshooting
└── .github/         # CI workflows
```

## License

MIT — see [LICENSE](LICENSE).
