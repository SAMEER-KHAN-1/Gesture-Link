"""Enables/disables starting the server automatically when the user logs in,
via the same HKCU "Run" registry key Windows' own startup apps use.
"""

import sys
import winreg

RUN_KEY_PATH = r"Software\Microsoft\Windows\CurrentVersion\Run"
RUN_VALUE_NAME = "GestureLink"


def _startup_command() -> str:
    if getattr(sys, "frozen", False):
        # Running as the PyInstaller-built .exe - it *is* the whole program.
        return f'"{sys.executable}"'
    # Running from source (`python run.py`) - point at the same interpreter
    # and script so this still works the same way in a dev checkout.
    return f'"{sys.executable}" "{sys.argv[0]}"'


def is_enabled() -> bool:
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY_PATH) as key:
        try:
            winreg.QueryValueEx(key, RUN_VALUE_NAME)
            return True
        except FileNotFoundError:
            return False


def set_enabled(enabled: bool) -> None:
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY_PATH, 0, winreg.KEY_SET_VALUE) as key:
        if enabled:
            winreg.SetValueEx(key, RUN_VALUE_NAME, 0, winreg.REG_SZ, _startup_command())
        else:
            try:
                winreg.DeleteValue(key, RUN_VALUE_NAME)
            except FileNotFoundError:
                pass
