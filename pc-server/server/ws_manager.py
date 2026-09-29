"""Tracks the currently connected phone(s) and lets other code push to them."""

from fastapi import WebSocket

from server.protocol import PushMessage


class ConnectionManager:
    def __init__(self):
        self.active_connections: list[WebSocket] = []

    async def connect(self, websocket: WebSocket) -> None:
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket) -> None:
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def broadcast(self, message: PushMessage) -> None:
        payload = message.model_dump_json()
        for connection in list(self.active_connections):
            try:
                await connection.send_text(payload)
            except Exception:
                # A socket that's already closing shouldn't stop the broadcast to
                # everyone else - the receive loop's own disconnect handler in
                # main.py is what actually cleans up a dead connection.
                continue


manager = ConnectionManager()
