"""FastAPI app entry point for the GestureLink PC server.

For now this just boots the app and exposes a health check so we can confirm
the service is reachable on the LAN before wiring up real commands.
"""

from fastapi import FastAPI

from server.config import load_config

app = FastAPI(title="GestureLink PC Server")


@app.on_event("startup")
def on_startup():
    config = load_config()
    print(f"[GestureLink] listening on {config['host']}:{config['port']}")
    print(f"[GestureLink] pairing token: {config['pairing_token']}")


@app.get("/health")
def health():
    return {"status": "ok", "service": "GestureLink PC Server"}
