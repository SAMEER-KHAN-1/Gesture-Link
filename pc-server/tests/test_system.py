import asyncio
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

from server.actions import system

GB = 1024 ** 3


def run(coro):
    return asyncio.run(coro)


@pytest.fixture
def psutil_mock(monkeypatch):
    mock = MagicMock()
    mock.cpu_percent.return_value = 12.5
    mock.virtual_memory.return_value = SimpleNamespace(percent=48.0)
    mock.sensors_battery.return_value = SimpleNamespace(percent=80.0, power_plugged=True)
    mock.disk_usage.return_value = SimpleNamespace(percent=62.5, free=int(120.44 * GB))
    mock.boot_time.return_value = 1_000.0
    monkeypatch.setattr(system, "psutil", mock)
    monkeypatch.setattr(system.time, "time", lambda: 1_000.0 + 3_725)
    return mock


def test_ping_replies_pong():
    assert run(system.handle_ping({})) == {"message": "pong"}


def test_system_stats_reports_cpu_memory_disk_battery_and_uptime(psutil_mock):
    result = run(system.handle_system_stats({}))

    assert result == {
        "cpu_percent": 12.5,
        "memory_percent": 48.0,
        "disk_percent": 62.5,
        "disk_free_gb": 120.4,
        "uptime_seconds": 3725,
        "battery_percent": 80.0,
        "battery_plugged": True,
    }


def test_system_stats_reads_the_disk_the_system_is_installed_on(psutil_mock, monkeypatch):
    monkeypatch.setenv("SystemDrive", "D:")

    run(system.handle_system_stats({}))

    psutil_mock.disk_usage.assert_called_once_with("D:\\")


def test_system_stats_battery_fields_are_none_on_a_desktop(psutil_mock):
    psutil_mock.sensors_battery.return_value = None

    result = run(system.handle_system_stats({}))

    assert result["battery_percent"] is None
    assert result["battery_plugged"] is None


def test_system_stats_disk_fields_are_none_if_the_disk_cannot_be_read(psutil_mock):
    psutil_mock.disk_usage.side_effect = OSError("drive not ready")

    result = run(system.handle_system_stats({}))

    assert result["disk_percent"] is None
    assert result["disk_free_gb"] is None
    assert result["cpu_percent"] == 12.5  # the rest of the stats still come through
