"""Pairing token check. Every incoming command must carry the token shown on the PC."""

from server.config import load_config


def is_token_valid(token: str) -> bool:
    config = load_config()
    return token == config.get("pairing_token")
