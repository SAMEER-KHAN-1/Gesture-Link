import asyncio
import subprocess
from unittest.mock import MagicMock

import pytest

from server.actions import network


def run(coro):
    return asyncio.run(coro)


def _completed(returncode=0, stdout="", stderr=""):
    return subprocess.CompletedProcess(args=[], returncode=returncode, stdout=stdout, stderr=stderr)


@pytest.fixture
def subprocess_run(monkeypatch):
    mock = MagicMock(return_value=_completed())
    monkeypatch.setattr(network.subprocess, "run", mock)
    return mock


def test_wifi_set_enabled_runs_toggle_script_with_on(subprocess_run):
    result = run(network.handle_wifi_set({"enabled": True}))

    args = subprocess_run.call_args[0][0]
    assert args[:4] == ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass"]
    assert str(network.SCRIPT_PATH) in args
    assert "-Kind" in args and args[args.index("-Kind") + 1] == "WiFi"
    assert "-State" in args and args[args.index("-State") + 1] == "On"
    assert result == {"kind": "WiFi", "enabled": True}


def test_bluetooth_set_disabled_runs_toggle_script_with_off(subprocess_run):
    result = run(network.handle_bluetooth_set({"enabled": False}))

    args = subprocess_run.call_args[0][0]
    assert args[args.index("-Kind") + 1] == "Bluetooth"
    assert args[args.index("-State") + 1] == "Off"
    assert result == {"kind": "Bluetooth", "enabled": False}


def test_set_radio_state_raises_on_nonzero_exit(monkeypatch):
    monkeypatch.setattr(network.subprocess, "run", MagicMock(return_value=_completed(returncode=1, stderr="boom")))

    with pytest.raises(RuntimeError, match="boom"):
        run(network.handle_wifi_set({"enabled": True}))


def test_radio_status_parses_json_output(monkeypatch):
    stdout = '{"WiFi": "On", "Bluetooth": "Off"}'
    monkeypatch.setattr(network.subprocess, "run", MagicMock(return_value=_completed(stdout=stdout)))

    result = run(network.handle_radio_status({}))

    assert result == {"wifi_enabled": True, "bluetooth_enabled": False}


def test_radio_status_raises_on_nonzero_exit(monkeypatch):
    monkeypatch.setattr(network.subprocess, "run", MagicMock(return_value=_completed(returncode=1, stderr="no radios")))

    with pytest.raises(RuntimeError, match="no radios"):
        run(network.handle_radio_status({}))
