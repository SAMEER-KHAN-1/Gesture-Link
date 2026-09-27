"""Entry point: `python run.py` starts the GestureLink server + tray icon on this PC."""

from server.tray import ServerTray

if __name__ == "__main__":
    ServerTray().start()
