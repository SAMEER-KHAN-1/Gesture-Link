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

First run creates a config file (host, port, pairing token) in `%LOCALAPPDATA%\GestureLink\config.json` and prints the pairing token to the console — you'll need that token in the Android app to connect. Nothing about this file is committed to git; it's your machine's local copy.

Once it's running, check it's alive from any browser on the same network:

```
http://<pc-lan-ip>:8765/health
```

This is just the skeleton for now (health check only) — the actual command handling (shutdown, wifi/bluetooth, app launching) is being added on top of this in the following commits.
