"""Shows a QR code encoding the pairing info (LAN IP, port, token) so the phone
can scan it in the app instead of typing an IP address and an 8-character
token by hand.
"""

import threading
import tkinter as tk
from tkinter import ttk

import qrcode
from PIL import ImageTk

from server.config import load_config, resolve_port
from server.net_utils import get_lan_ip


def build_pairing_payload() -> str:
    config = load_config()
    host = get_lan_ip()
    return f"gesturelink://{host}:{resolve_port(config)}?token={config['pairing_token']}"


def _show_window() -> None:
    payload = build_pairing_payload()
    image = qrcode.make(payload).convert("RGB")

    root = tk.Tk()
    root.title("GestureLink - Pair with your phone")
    root.resizable(False, False)

    photo = ImageTk.PhotoImage(image, master=root)
    image_label = ttk.Label(root, image=photo)
    image_label.image = photo  # keep a reference, otherwise it can get garbage collected
    image_label.pack(padx=16, pady=16)

    ttk.Label(root, text="Scan this in the GestureLink app", wraplength=300, justify="center").pack(
        padx=16, pady=(0, 16)
    )

    root.mainloop()


def show_pairing_qr() -> None:
    """Opens the QR window on its own thread so it doesn't block the tray icon's loop."""
    threading.Thread(target=_show_window, daemon=True).start()
