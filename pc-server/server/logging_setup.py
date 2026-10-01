"""Log to a rotating file in the app data folder.

The packaged exe is a windowed app with no console, so without a file there'd be
nowhere to look when something goes wrong on someone's PC. The file is capped
(a few small chunks) so it can never grow without bound.
"""

import logging
import sys
from logging.handlers import RotatingFileHandler
from pathlib import Path

from server.config import get_app_data_dir

LOG_FILE_NAME = "gesturelink.log"
MAX_LOG_BYTES = 512 * 1024
BACKUP_COUNT = 3  # gesturelink.log.1 .. .3 are the older chunks

_FORMAT = "%(asctime)s %(levelname)s %(name)s: %(message)s"


def get_log_path() -> Path:
    log_dir = get_app_data_dir() / "logs"
    log_dir.mkdir(exist_ok=True)
    return log_dir / LOG_FILE_NAME


def setup_logging() -> None:
    """Send everything (including uvicorn's own logs) to the rotating file, and to the
    console too when there is one. Safe to call more than once."""
    root = logging.getLogger()
    if any(isinstance(handler, RotatingFileHandler) for handler in root.handlers):
        return

    root.setLevel(logging.INFO)
    formatter = logging.Formatter(_FORMAT)

    file_handler = RotatingFileHandler(
        get_log_path(), maxBytes=MAX_LOG_BYTES, backupCount=BACKUP_COUNT, encoding="utf-8"
    )
    file_handler.setFormatter(formatter)
    root.addHandler(file_handler)

    # A windowed exe has no stdout at all.
    if sys.stdout is not None:
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setFormatter(formatter)
        root.addHandler(console_handler)
