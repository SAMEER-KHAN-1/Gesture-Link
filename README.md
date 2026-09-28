# GestureLink

Control your Windows PC from your Android phone over your local network — power controls, wifi/bluetooth toggles, launching apps, and more, with a small always-on tray service on the PC and a native Android app as the remote.

## What it does (planned feature set)

**Power control**
- Shut down the PC
- Restart the PC
- Sleep / lock the workstation

**Connectivity**
- Turn Wifi adapter on/off
- Turn Bluetooth radio on/off

**Apps**
- List installed / running apps
- Launch an app remotely

**Media**
- Volume up/down/mute
- Play/pause, next/previous track

**Dashboard**
- Live CPU / RAM / battery stats

**Pairing**
- Scan a QR code shown on the PC (or enter its IP + token by hand)

**Planned next**
- File browser / quick file transfer
- Mouse/keyboard remote input

## How it works

The PC runs a small Python service (`pc-server/`) in the system tray. It exposes a WebSocket endpoint on your local network, guarded by a pairing token generated on first run. The Android app (`android-app/`) connects to that endpoint, authenticates with the token, and sends small JSON commands ("shutdown", "wifi_off", "launch_app", ...). The PC executes the command using native Windows APIs (via `pywin32`) or PowerShell where a native API doesn't exist (e.g. toggling radios) and reports the result back over the same socket.

Everything is designed to run on your home/local network only — there is no cloud relay, so control only works while both devices are on the same network (or reachable via VPN).

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full design and the command protocol.

## Project structure

```
GestureLink/
├── pc-server/       # Python tray service that runs on the Windows PC
├── android-app/     # Kotlin/Jetpack Compose Android app
└── docs/            # Architecture notes and protocol spec
```

## Status

🚧 Actively under construction, built commit by commit. Not yet ready to run end-to-end — check back as `pc-server/` and `android-app/` fill in.

## License

MIT — see [LICENSE](LICENSE).
