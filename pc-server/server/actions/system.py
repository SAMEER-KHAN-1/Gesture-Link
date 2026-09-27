"""Basic actions that aren't tied to power/network/apps - just connectivity checks for now."""

from server.actions import register


@register("ping")
async def handle_ping(params: dict) -> dict:
    return {"message": "pong"}
