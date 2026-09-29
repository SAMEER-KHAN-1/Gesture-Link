"""FastAPI app entry point for the GestureLink PC server.

Boots the app, exposes a health check, and hosts the /ws endpoint the Android
app actually talks to for commands.
"""

import asyncio
import json

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from pydantic import ValidationError

from server.actions import get_handler
from server.auth import is_locked_out, is_token_valid
from server.battery_watch import battery_watch_loop
from server.config import load_config
from server.protocol import CommandRequest, CommandResponse
from server.ws_manager import manager

app = FastAPI(title="GestureLink PC Server")


@app.on_event("startup")
async def on_startup():
    config = load_config()
    print(f"[GestureLink] listening on {config['host']}:{config['port']}")
    print(f"[GestureLink] pairing token: {config['pairing_token']}")
    asyncio.create_task(battery_watch_loop())


@app.get("/health")
def health():
    return {"status": "ok", "service": "GestureLink PC Server"}


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    client_address = websocket.client.host if websocket.client else "unknown"
    try:
        while True:
            raw = await websocket.receive_text()
            response = await handle_message(raw, client_address)
            await websocket.send_text(response.model_dump_json())
    except WebSocketDisconnect:
        manager.disconnect(websocket)


async def handle_message(raw: str, client_address: str) -> CommandResponse:
    try:
        data = json.loads(raw)
        request = CommandRequest(**data)
    except (json.JSONDecodeError, ValidationError):
        return CommandResponse(id="unknown", ok=False, action="unknown", error="malformed request")

    if is_locked_out(client_address):
        return CommandResponse(
            id=request.id, ok=False, action=request.action, error="too many failed attempts, try again shortly"
        )

    if not is_token_valid(request.token, client_address):
        return CommandResponse(id=request.id, ok=False, action=request.action, error="invalid pairing token")

    handler = get_handler(request.action)
    if handler is None:
        return CommandResponse(
            id=request.id, ok=False, action=request.action, error=f"unknown action '{request.action}'"
        )

    try:
        result = await handler(request.params)
        return CommandResponse(id=request.id, ok=True, action=request.action, result=result)
    except Exception as exc:
        return CommandResponse(id=request.id, ok=False, action=request.action, error=str(exc))
