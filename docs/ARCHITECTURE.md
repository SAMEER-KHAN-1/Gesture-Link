# Architecture

## Overview

GestureLink has two halves that talk to each other over a plain local-network WebSocket connection — no cloud, no external server.

```
 ┌─────────────────────┐        LAN Wifi         ┌──────────────────────┐
 │   Android App        │  <──── WebSocket ────>  │   PC Tray Service      │
 │  (Kotlin + Compose)   │      (JSON messages)    │  (Python + FastAPI)    │
 └─────────────────────┘                          └──────────────────────┘
                                                              │
                                                    pywin32 / PowerShell
                                                              │
                                                     Windows OS actions
                                              (power, radios, processes, apps)
```

- The **PC service** starts with Windows (eventually), sits in the system tray, and hosts a WebSocket server bound to the machine's LAN IP on a fixed port.
- On first run it generates a random **pairing token** and shows it (tray notification / window), so only a phone that has seen the token can issue commands.
- The **Android app** stores the PC's IP + token after the first successful pairing and reconnects automatically after that.
- Every command from phone → PC is a small JSON object; every reply PC → phone is a small JSON object. See the protocol below.

## Why WebSocket over plain HTTP

Commands are one-off, but we want the PC to be able to push things back to the phone unprompted later (e.g. "battery low", "someone is trying to pair"), and a persistent socket makes the connection status ("PC online/offline") trivial to show in the app without polling.

## Security model (v1)

- LAN-only. The server binds to the local network interface, not a public address.
- Token-based auth: every message must include the token; the server drops/rejects any socket that hasn't authenticated within a few seconds of connecting.
- The token is generated locally on the PC (not hardcoded), stored in a git-ignored local config file, and shown to the user once so they can enter it in the app.
- No remote/internet relay in v1. If remote control off-LAN is wanted later, that's a deliberate opt-in addition (e.g. via the user's own VPN), not a default.

This is intentionally a "trusted home network" threat model, not a hardened public-internet service — it's a personal remote for your own devices.

## Command protocol (v1)

All messages are JSON over the single WebSocket connection.

### Phone → PC (request)

```json
{
  "id": "3f9b2e",
  "token": "<pairing token>",
  "action": "shutdown",
  "params": {}
}
```

- `id`: random string set by the client, echoed back in the response so replies can be matched to requests.
- `token`: pairing token, required on every message in v1 (simplest to implement first; can move to "auth once per connection" later).
- `action`: one of the actions below.
- `params`: action-specific arguments (empty object if none).

### PC → Phone (response)

```json
{
  "id": "3f9b2e",
  "ok": true,
  "action": "shutdown",
  "result": {},
  "error": null
}
```

- `ok`: whether the action succeeded.
- `error`: human-readable message when `ok` is `false`, otherwise `null`.

### Actions (v1 target set)

| action           | params                     | description                                   |
|------------------|----------------------------|------------------------------------------------|
| `ping`           | –                          | connectivity check, PC replies with `pong`      |
| `shutdown`       | `{ "delay_seconds": 0 }`   | shuts the PC down                               |
| `restart`        | `{ "delay_seconds": 0 }`   | restarts the PC                                 |
| `cancel_shutdown`| –                          | cancels a pending shutdown/restart               |
| `sleep`          | –                          | puts the PC to sleep                            |
| `lock`           | –                          | locks the current session                       |
| `wifi_set`       | `{ "enabled": true }`      | turns the wifi radio on/off                     |
| `bluetooth_set`  | `{ "enabled": true }`      | turns the bluetooth radio on/off                |
| `radio_status`   | –                          | returns the wifi/bluetooth radios' actual on/off state |
| `apps_list`      | –                          | returns installed/known-launchable apps         |
| `app_launch`     | `{ "app_id": "..." }`      | launches an app returned by `apps_list`         |
| `system_stats`   | –                          | returns CPU %, RAM %, and battery info          |
| `volume_up`      | –                          | presses the volume-up media key                 |
| `volume_down`    | –                          | presses the volume-down media key                |
| `volume_mute_toggle` | –                      | presses the mute media key                       |
| `media_play_pause` | –                        | presses the play/pause media key                 |
| `media_next`     | –                          | presses the next-track media key                 |
| `media_previous` | –                          | presses the previous-track media key             |
| `list_dir`       | `{ "path": "" }`           | lists a directory's contents (empty path = drives) |
| `download_file`  | `{ "path": "..." }`        | returns a file's bytes, base64-encoded (15MB limit) |
| `mouse_move`     | `{ "dx": 0, "dy": 0 }`     | moves the cursor relative to its current position |
| `mouse_click`    | `{ "button": "left" }`     | clicks the left or right mouse button           |
| `mouse_scroll`   | `{ "ticks": 0 }`           | scrolls the wheel (positive = down, negative = up) |
| `keyboard_type`  | `{ "text": "..." }`        | types the given text (layout-independent)       |
| `keyboard_key`   | `{ "key": "enter" }`       | presses a named key (enter, backspace, tab, escape, space) |

More actions will be added the same way as the project grows — this table is the contract both sides code against, so it's kept up to date whenever an action is added or changed.

## Repo layout

```
GestureLink/
├── pc-server/       # Python service: FastAPI app, command handlers, tray icon, packaging
├── android-app/     # Kotlin/Compose app: UI, WebSocket client, pairing/storage
└── docs/            # this file, plus any future design notes
```
