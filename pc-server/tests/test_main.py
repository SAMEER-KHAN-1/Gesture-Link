import asyncio
import json
from types import SimpleNamespace
from unittest.mock import MagicMock

from fastapi import WebSocketDisconnect

from server import main


def run(coro):
    return asyncio.run(coro)


def test_malformed_json_is_rejected():
    response = run(main.handle_message("not json", "10.0.0.1"))
    assert response.ok is False
    assert response.error == "malformed request"


def test_locked_out_address_is_rejected_before_checking_token(monkeypatch):
    monkeypatch.setattr(main, "is_locked_out", lambda addr: True)
    payload = json.dumps({"id": "1", "token": "whatever", "action": "ping", "params": {}})

    response = run(main.handle_message(payload, "10.0.0.2"))

    assert response.ok is False
    assert "too many failed attempts" in response.error


def test_invalid_token_is_rejected(monkeypatch):
    monkeypatch.setattr(main, "is_locked_out", lambda addr: False)
    monkeypatch.setattr(main, "is_token_valid", lambda token, addr: False)
    payload = json.dumps({"id": "1", "token": "wrong", "action": "ping", "params": {}})

    response = run(main.handle_message(payload, "10.0.0.3"))

    assert response.ok is False
    assert response.error == "invalid pairing token"


def test_unknown_action_is_rejected(monkeypatch):
    monkeypatch.setattr(main, "is_locked_out", lambda addr: False)
    monkeypatch.setattr(main, "is_token_valid", lambda token, addr: True)
    payload = json.dumps({"id": "1", "token": "good", "action": "not_a_real_action", "params": {}})

    response = run(main.handle_message(payload, "10.0.0.4"))

    assert response.ok is False
    assert "unknown action" in response.error


def test_successful_ping(monkeypatch):
    monkeypatch.setattr(main, "is_locked_out", lambda addr: False)
    monkeypatch.setattr(main, "is_token_valid", lambda token, addr: True)
    payload = json.dumps({"id": "abc123", "token": "good", "action": "ping", "params": {}})

    response = run(main.handle_message(payload, "10.0.0.5"))

    assert response.ok is True
    assert response.id == "abc123"
    assert response.result == {"message": "pong"}


def test_handler_exception_is_reported_as_error(monkeypatch):
    monkeypatch.setattr(main, "is_locked_out", lambda addr: False)
    monkeypatch.setattr(main, "is_token_valid", lambda token, addr: True)
    payload = json.dumps({"id": "1", "token": "good", "action": "download_file", "params": {}})

    response = run(main.handle_message(payload, "10.0.0.6"))

    assert response.ok is False
    assert response.error == "path is required"


class _DisconnectingSocket:
    """Accepts the connection, then immediately drops it - a phone that vanishes."""

    client = SimpleNamespace(host="10.0.0.9")

    async def accept(self):
        pass

    async def receive_text(self):
        raise WebSocketDisconnect()


def _connect_and_drop(monkeypatch, other_connections=()):
    release = MagicMock()
    monkeypatch.setattr(main, "release_held_buttons", release)
    monkeypatch.setattr(main.manager, "active_connections", list(other_connections))
    run(main.websocket_endpoint(_DisconnectingSocket()))
    return release


def test_disconnect_releases_held_mouse_buttons_when_no_phone_is_left(monkeypatch):
    release = _connect_and_drop(monkeypatch)
    release.assert_called_once()


def test_disconnect_leaves_mouse_buttons_alone_while_another_phone_is_connected(monkeypatch):
    release = _connect_and_drop(monkeypatch, other_connections=[object()])
    release.assert_not_called()
