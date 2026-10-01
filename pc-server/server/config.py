"""Local config + pairing token handling for the GestureLink PC server.

The config file lives outside the repo (in the user's local app data folder)
so the pairing token never ends up committed to git by accident.
"""

import json
import logging
import os
import secrets
from pathlib import Path

APP_FOLDER_NAME = "GestureLink"
CONFIG_FILE_NAME = "config.json"

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 8765

logger = logging.getLogger("gesturelink")


def get_app_data_dir() -> Path:
    """Return the folder we're allowed to store local config/state in, creating it if needed."""
    base = os.environ.get("LOCALAPPDATA") or str(Path.home() / "AppData" / "Local")
    app_dir = Path(base) / APP_FOLDER_NAME
    app_dir.mkdir(parents=True, exist_ok=True)
    return app_dir


def get_config_path() -> Path:
    return get_app_data_dir() / CONFIG_FILE_NAME


def generate_pairing_token() -> str:
    """A short, easy-to-type-on-a-phone-once token, not meant to be a long-lived secret."""
    return secrets.token_hex(4)  # 8 hex chars, e.g. "a13f9c02"


def load_config() -> dict:
    """Load the existing config, or create a fresh one (with a new pairing token) on first run."""
    config_path = get_config_path()

    if config_path.exists():
        with open(config_path, "r", encoding="utf-8") as f:
            return json.load(f)

    config = {
        "host": DEFAULT_HOST,
        "port": DEFAULT_PORT,
        "pairing_token": generate_pairing_token(),
    }
    save_config(config)
    return config


def save_config(config: dict) -> None:
    with open(get_config_path(), "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2)


def resolve_port(config: dict) -> int:
    """The port from config.json, or the default if it's missing or not a usable port number
    (a hand-edited typo shouldn't stop the server from starting)."""
    port = config.get("port", DEFAULT_PORT)
    if isinstance(port, int) and not isinstance(port, bool) and 1 <= port <= 65535:
        return port
    logger.warning("ignoring invalid port %r in config.json, using %d", port, DEFAULT_PORT)
    return DEFAULT_PORT


def regenerate_pairing_token() -> str:
    """Invalidate the old token and hand back a new one, e.g. if it was leaked."""
    config = load_config()
    config["pairing_token"] = generate_pairing_token()
    save_config(config)
    return config["pairing_token"]
