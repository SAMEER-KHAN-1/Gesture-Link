"""System tray wrapper so the server runs quietly in the background instead of
sitting in a console window. Right-click the tray icon to see the pairing
token, open the config folder, or quit.
"""

import threading
import webbrowser

import pystray
import uvicorn
from PIL import Image, ImageDraw

from server.config import get_app_data_dir, load_config
from server.pairing_qr import show_pairing_qr

ICON_SIZE = 64


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
                pystray.MenuItem("Open config folder", self._open_config_folder),
                pystray.MenuItem("Quit", self._quit),
            ),
        )

    def _token_label(self, item) -> str:
        return f"Pairing token: {self.config['pairing_token']}"

    def _show_qr(self, icon, item) -> None:
        show_pairing_qr()

    def _open_config_folder(self, icon, item) -> None:
        webbrowser.open(str(get_app_data_dir()))

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
        )
        self._server = uvicorn.Server(uvicorn_config)
        self._server.run()

    def start(self) -> None:
        server_thread = threading.Thread(target=self._run_server, daemon=True)
        server_thread.start()
        self._icon.run()  # blocks - has to be the main thread on Windows
