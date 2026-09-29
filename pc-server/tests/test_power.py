import asyncio
from unittest.mock import MagicMock

import pytest

from server.actions import power


def run(coro):
    return asyncio.run(coro)


@pytest.fixture
def subprocess_run(monkeypatch):
    mock = MagicMock()
    monkeypatch.setattr(power.subprocess, "run", mock)
    return mock


def test_shutdown_calls_shutdown_exe_with_delay(subprocess_run):
    result = run(power.handle_shutdown({"delay_seconds": 30}))
    subprocess_run.assert_called_once_with(["shutdown", "/s", "/t", "30"], check=True)
    assert result == {"message": "shutting down in 30s"}


def test_shutdown_defaults_to_no_delay(subprocess_run):
    run(power.handle_shutdown({}))
    subprocess_run.assert_called_once_with(["shutdown", "/s", "/t", "0"], check=True)


def test_restart_calls_shutdown_exe_with_restart_flag(subprocess_run):
    result = run(power.handle_restart({"delay_seconds": 5}))
    subprocess_run.assert_called_once_with(["shutdown", "/r", "/t", "5"], check=True)
    assert result == {"message": "restarting in 5s"}


def test_cancel_shutdown_does_not_raise_on_failure(subprocess_run):
    result = run(power.handle_cancel_shutdown({}))
    subprocess_run.assert_called_once_with(["shutdown", "/a"], check=False)
    assert result == {"message": "cancelled pending shutdown/restart"}


def test_sleep_calls_set_suspend_state(monkeypatch):
    mock = MagicMock()
    monkeypatch.setattr(power.ctypes.windll.powrprof, "SetSuspendState", mock)

    result = run(power.handle_sleep({}))

    mock.assert_called_once_with(0, 1, 0)
    assert result == {"message": "sleeping"}


def test_lock_calls_lock_work_station(monkeypatch):
    mock = MagicMock()
    monkeypatch.setattr(power.ctypes.windll.user32, "LockWorkStation", mock)

    result = run(power.handle_lock({}))

    mock.assert_called_once_with()
    assert result == {"message": "locked"}
