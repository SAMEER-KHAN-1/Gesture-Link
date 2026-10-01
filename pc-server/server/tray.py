"""System tray wrapper so the server runs quietly in the background instead of
sitting in a console window. Right-click the tray icon to see the pairing
token, open the config folder, or quit.
"""

import ctypes
import threading
import webbrowser

import pystray
import uvicorn
from PIL import Image, ImageDraw

from server import startup
from server.config import get_app_data_dir, load_config, regenerate_pairing_token
from server.logging_setup import setup_logging
from server.pairing_qr import show_pairing_qr

ICON_SIZE = 64

# MessageBoxW flags/results
_MB_YESNO = 0x04
_MB_ICONWARNING = 0x30
_MB_TOPMOST = 0x40000
_IDYES = 6


def _confirm(title: str, message: str) -> bool:
    return ctypes.windll.user32.MessageBoxW(0, message, title, _MB_YESNO | _MB_ICONWARNING | _MB_TOPMOST) == _IDYES


def _build_icon_image() -> Image.Image:
    """Generated in code so we don't need to ship a binary icon asset yet."""
    image = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse((4, 4, ICON_SIZE - 4, ICON_SIZE - 4), fill=(0, 122, 255, 255))
    draw.text((ICON_SIZE // 2 - 6, ICON_SIZE // 2 - 10), "GL", fill=(255, 255, 255, 255))
    return image


class ServerTray:
    def __init__(self):
        self.config = load_config()
        self._server: uvicorn.Server | None = None
        self._icon = pystray.Icon(
            "gesturelink",
            icon=_build_icon_image(),
            title="GestureLink",
            menu=pystray.Menu(
                pystray.MenuItem(self._token_label, None, enabled=False),
                pystray.MenuItem("Show pairing QR code", self._show_qr),
                pystray.MenuItem("Regenerate pairing token", self._regenerate_token),
                pystray.MenuItem("Open config folder", self._open_config_folder),
                pystray.MenuItem("Start with Windows", self._toggle_autostart, checked=self._is_autostart_enabled),
                pystray.MenuItem("Quit", self._quit),
            ),
        )

    def _token_label(self, item) -> str:
        return f"Pairing token: {self.config['pairing_token']}"

    def _show_qr(self, icon, item) -> None:
        show_pairing_qr()

    def _regenerate_token(self, icon, item) -> None:
        if not _confirm(
            "Regenerate pairing token?",
            "Every phone paired with this PC will stop working until it's paired again with the new token.",
        ):
            return
        self.config["pairing_token"] = regenerate_pairing_token()
        icon.update_menu()  # refreshes the "Pairing token: ..." line

    def _open_config_folder(self, icon, item) -> None:
        webbrowser.open(str(get_app_data_dir()))

    def _is_autostart_enabled(self, item) -> bool:
        return startup.is_enabled()

    def _toggle_autostart(self, icon, item) -> None:
        startup.set_enabled(not startup.is_enabled())

    def _quit(self, icon, item) -> None:
        if self._server is not None:
            self._server.should_exit = True
        icon.stop()

    def _run_server(self) -> None:
        uvicorn_config = uvicorn.Config(
            "server.main:app",
            host=self.config["host"],
            port=self.config["port"],
            reload=False,
            log_level="info",
            log_config=None,  # our own setup_logging() handles output, incl. no-console exes
        )
        self._server = uvicorn.Server(uvicorn_config)
        self._server.run()

    def start(self) -> None:
        setup_logging()
        server_thread = threading.Thread(target=self._run_server, daemon=True)
        server_thread.start()
        self._icon.run()  # blocks - has to be the main thread on Windows
