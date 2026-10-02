from unittest.mock import MagicMock

import pytest

from server import tray


class FakeServer:
    """Stands in for uvicorn.Server: run() does whatever the test says."""

    def __init__(self, run_behaviour):
        self._run_behaviour = run_behaviour
        self.should_exit = False

    def run(self):
        self._run_behaviour(self)


@pytest.fixture
def server_tray(monkeypatch):
    config = {"host": "127.0.0.1", "port": 9123, "pairing_token": "abc12345"}
    monkeypatch.setattr(tray, "load_config", lambda: config)
    monkeypatch.setattr(tray.pystray, "Icon", MagicMock())
    monkeypatch.setattr(tray.uvicorn, "Config", MagicMock())
    return tray.ServerTray()


@pytest.fixture
def error_box(monkeypatch):
    box = MagicMock()
    monkeypatch.setattr(tray, "_show_error", box)
    return box


def _use_server(monkeypatch, run_behaviour):
    monkeypatch.setattr(tray.uvicorn, "Server", lambda config: FakeServer(run_behaviour))


def _exit_like_a_failed_bind(_server):
    raise SystemExit(1)  # what uvicorn does when the port is already taken


def test_a_failed_bind_shows_the_port_and_stops_the_tray_icon(monkeypatch, server_tray, error_box):
    _use_server(monkeypatch, _exit_like_a_failed_bind)

    server_tray._run_server()

    error_box.assert_called_once()
    message = error_box.call_args[0][1]
    assert "9123" in message
    assert "config.json" in message
    server_tray._icon.stop.assert_called_once()


def test_a_crashing_server_is_reported_the_same_way(monkeypatch, server_tray, error_box):
    def crash(_server):
        raise RuntimeError("boom")

    _use_server(monkeypatch, crash)

    server_tray._run_server()

    error_box.assert_called_once()
    server_tray._icon.stop.assert_called_once()


def test_a_server_that_returns_without_being_asked_to_quit_is_reported(monkeypatch, server_tray, error_box):
    _use_server(monkeypatch, lambda _server: None)

    server_tray._run_server()

    error_box.assert_called_once()
    server_tray._icon.stop.assert_called_once()


def test_quitting_from_the_menu_is_not_reported_as_a_failure(monkeypatch, server_tray, error_box):
    def run_until_quit(server):
        server_tray._quit(server_tray._icon, None)  # the user picks Quit while it's running

    _use_server(monkeypatch, run_until_quit)

    server_tray._run_server()

    error_box.assert_not_called()
    # Only Quit's own stop() - the server finishing afterwards must not stop it a second time.
    server_tray._icon.stop.assert_called_once()


def test_quit_asks_the_running_server_to_exit(monkeypatch, server_tray):
    server = FakeServer(lambda _server: None)
    server_tray._server = server

    server_tray._quit(server_tray._icon, None)

    assert server.should_exit is True


def test_the_server_starts_only_once_the_icon_is_ready(monkeypatch, server_tray):
    started = []
    monkeypatch.setattr(tray.threading, "Thread", lambda target, daemon: MagicMock(start=lambda: started.append(target)))
    icon = MagicMock()

    server_tray._on_icon_ready(icon)

    assert icon.visible is True
    assert started == [server_tray._run_server]
