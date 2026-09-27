# GestureLink PC Server

Small FastAPI service that runs on the Windows PC and does whatever the Android app asks it to.

## Setup

```
cd pc-server
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

## Run

```
python run.py
```

First run creates a config file (host, port, pairing token) in `%LOCALAPPDATA%\GestureLink\config.json`. Nothing about this file is committed to git; it's your machine's local copy.

Running it opens a tray icon (bottom-right, near the clock) instead of a plain console window. Right-click it to:
- see the current pairing token (needed once, in the Android app)
- open the config folder
- quit the server

Once it's running, check it's alive from any browser on the same network:

```
http://<pc-lan-ip>:8765/health
```

## What's implemented so far

- `/ws` — the WebSocket endpoint the Android app connects to, token-authenticated
- Power: `shutdown`, `restart`, `cancel_shutdown`, `sleep`, `lock`
- Network: `wifi_set`, `bluetooth_set`
- Apps: `apps_list`, `app_launch`

Full protocol/action reference lives in [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).
