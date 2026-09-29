"""Pairing token check, with a light brute-force guard.

Every incoming command must carry the token shown on the PC. Since anyone on
the LAN can open a websocket and start guessing, a handful of wrong attempts
from the same address locks it out for a short cooldown instead of allowing
unlimited retries.
"""

import time

from server.config import load_config

MAX_FAILED_ATTEMPTS = 5
LOCKOUT_SECONDS = 30

_failed_attempts: dict[str, int] = {}
_locked_until: dict[str, float] = {}


def is_locked_out(client_address: str) -> bool:
    locked_until = _locked_until.get(client_address)
    if locked_until is None:
        return False
    if time.monotonic() >= locked_until:
        _locked_until.pop(client_address, None)
        _failed_attempts.pop(client_address, None)
        return False
    return True


def is_token_valid(token: str, client_address: str) -> bool:
    config = load_config()
    if token == config.get("pairing_token"):
        _failed_attempts.pop(client_address, None)
        return True

    attempts = _failed_attempts.get(client_address, 0) + 1
    _failed_attempts[client_address] = attempts
    if attempts >= MAX_FAILED_ATTEMPTS:
        _locked_until[client_address] = time.monotonic() + LOCKOUT_SECONDS
    return False
