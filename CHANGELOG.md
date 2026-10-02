# Changelog

All notable changes to GestureLink. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] - 2026-10-02

First complete version: a Windows PC server and an Android app that controls it over the local network. Built over 2026-09-27 to 2026-10-02.

### PC server

- WebSocket endpoint with pairing-token authentication, a 30-second lockout after 5 wrong tokens from one address, and a rotating log file (the token is never logged)
- System tray icon with the pairing token and port, a pairing QR code, regenerate token, open config folder, and a "Start with Windows" toggle
- Configurable port (`config.json`), carried in the pairing QR code
- Power: shutdown, restart, cancel, sleep, lock
- Wi-Fi and Bluetooth switches that report the radios' real state
- App list and launch, system stats (CPU, RAM, disk, uptime, battery), brightness get/set for built-in displays
- Volume and media keys, mouse (move, click, double-click, press/release for dragging, scroll), keyboard (typing, keys, hotkeys)
- Files: list, download, upload, create folder, rename, delete to the Recycle Bin (15 MB transfer limit)
- Clipboard get/set, screenshot (downscaled JPEG), process list and kill (critical Windows processes protected)
- MAC address lookup for Wake-on-LAN
- Low-battery push notification sent to connected phones
- Packaged as a standalone `GestureLink.exe` with PyInstaller
- pytest suite (249 tests) run by GitHub Actions on Windows

### Android app

- Pairing by QR scan or manual entry (with optional `:port`), automatic reconnect to the last PC, Forget this PC, and a Wake PC button (Wake-on-LAN)
- Dashboard: power controls with confirmation, Wi-Fi/Bluetooth switches, brightness slider, volume and media buttons, clipboard send/get, live system stats with ping latency
- Touchpad with sensitivity slider, tap, double-click, drag lock, scroll, text entry and Enter/Backspace/Escape, with haptic feedback
- Keyboard shortcuts screen and presentation remote
- Screen viewer with live refresh and pinch to zoom
- Task manager (search, end a process with confirmation)
- App browser and launcher
- File browser: bookmarks, download, multi-file upload with a progress indicator, new folder, rename and delete from a long-press menu
- Notification history, settings screen (disconnect, forget, System/Light/Dark theme), connection-loss handling
- JVM unit tests, built and run by GitHub Actions

### Documentation

- README with setup guide, security notes and known limits
- `docs/ARCHITECTURE.md` with the command protocol, security model and module overview
- `docs/TROUBLESHOOTING.md`

### Known limits

- Windows PCs only; the connection is plain `ws://` on a trusted local network
- Not yet verified end to end on a physical phone. The Android app had no Android SDK available during development, so its first compile is the Android CI job
- Downloads to the phone need Android 10 or newer
