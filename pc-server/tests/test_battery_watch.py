import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from server import battery_watch


def run(coro):
    return asyncio.run(coro)


@pytest.fixture(autouse=True)
def reset_latch(monkeypatch):
    # _was_low is module-level state carried between calls by design (that's
    # the whole point - only push once per low-battery episode), so each test
    # needs to start from a known baseline.
    monkeypatch.setattr(battery_watch, "_was_low", False)


@pytest.fixture
def broadcast(monkeypatch):
    mock = AsyncMock()
    monkeypatch.setattr(battery_watch.manager, "broadcast", mock)
    return mock


def _battery(percent, plugged):
    return SimpleNamespace(percent=percent, power_plugged=plugged)


def test_pushes_once_when_crossing_into_low_battery(monkeypatch, broadcast):
    monkeypatch.setattr(battery_watch.psutil, "sensors_battery", lambda: _battery(10, False))

    run(battery_watch._check_once())

    broadcast.assert_called_once()
    message = broadcast.call_args[0][0]
    assert message.push == "battery_low"
    assert message.data == {"battery_percent": 10}


def test_does_not_push_again_while_still_low(monkeypatch, broadcast):
    monkeypatch.setattr(battery_watch.psutil, "sensors_battery", lambda: _battery(10, False))

    run(battery_watch._check_once())
    run(battery_watch._check_once())

    broadcast.assert_called_once()


def test_pushes_again_after_recovering_and_dropping_low_again(monkeypatch, broadcast):
    monkeypatch.setattr(battery_watch.psutil, "sensors_battery", lambda: _battery(10, False))
    run(battery_watch._check_once())

    monkeypatch.setattr(battery_watch.psutil, "sensors_battery", lambda: _battery(50, False))
    run(battery_watch._check_once())

    monkeypatch.setattr(battery_watch.psutil, "sensors_battery", lambda: _battery(10, False))
    run(battery_watch._check_once())

    assert broadcast.call_count == 2


def test_no_push_while_charging_even_if_percent_is_low(monkeypatch, broadcast):
    monkeypatch.setattr(battery_watch.psutil, "sensors_battery", lambda: _battery(10, True))

    run(battery_watch._check_once())

    broadcast.assert_not_called()


def test_no_push_on_a_desktop_with_no_battery(monkeypatch, broadcast):
    monkeypatch.setattr(battery_watch.psutil, "sensors_battery", lambda: None)

    run(battery_watch._check_once())

    broadcast.assert_not_called()
