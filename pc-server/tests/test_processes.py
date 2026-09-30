import asyncio
import os
from types import SimpleNamespace
from unittest.mock import MagicMock

import psutil
import pytest

from server.actions import processes


def run(coro):
    return asyncio.run(coro)


def fake_proc(pid, name, rss_bytes):
    memory = SimpleNamespace(rss=rss_bytes) if rss_bytes is not None else None
    return SimpleNamespace(info={"pid": pid, "name": name, "memory_info": memory})


@pytest.fixture
def process_class(monkeypatch):
    """Replaces psutil.Process; each test sets what the 'process' reports."""
    proc = MagicMock()
    proc.name.return_value = "notepad.exe"
    factory = MagicMock(return_value=proc)
    monkeypatch.setattr(processes.psutil, "Process", factory)
    return factory, proc


def test_process_list_returns_processes_biggest_memory_first(monkeypatch):
    mb = 1024 * 1024
    monkeypatch.setattr(
        processes.psutil,
        "process_iter",
        lambda attrs: iter(
            [fake_proc(1, "small.exe", 10 * mb), fake_proc(2, "big.exe", 500 * mb), fake_proc(3, "mid.exe", 50 * mb)]
        ),
    )

    result = run(processes.handle_process_list({}))

    assert [p["name"] for p in result["processes"]] == ["big.exe", "mid.exe", "small.exe"]
    assert result["processes"][0] == {"pid": 2, "name": "big.exe", "memory_mb": 500.0}


def test_process_list_tolerates_processes_that_hide_their_details(monkeypatch):
    monkeypatch.setattr(
        processes.psutil,
        "process_iter",
        lambda attrs: iter([fake_proc(4, None, None)]),
    )

    result = run(processes.handle_process_list({}))

    assert result["processes"] == [{"pid": 4, "name": "?", "memory_mb": 0.0}]


def test_process_list_is_capped(monkeypatch):
    monkeypatch.setattr(processes, "MAX_LISTED_PROCESSES", 2)
    monkeypatch.setattr(
        processes.psutil,
        "process_iter",
        lambda attrs: iter([fake_proc(i, f"p{i}.exe", i * 1024 * 1024) for i in range(1, 6)]),
    )

    result = run(processes.handle_process_list({}))

    assert [p["pid"] for p in result["processes"]] == [5, 4]


def test_process_kill_ends_the_process_and_waits_for_it(process_class):
    factory, proc = process_class

    result = run(processes.handle_process_kill({"pid": 1234, "name": "notepad.exe"}))

    factory.assert_called_once_with(1234)
    proc.kill.assert_called_once()
    proc.wait.assert_called_once()
    assert "notepad.exe" in result["message"]


def test_process_kill_name_check_is_case_insensitive(process_class):
    _factory, proc = process_class

    run(processes.handle_process_kill({"pid": 1234, "name": "NOTEPAD.EXE"}))

    proc.kill.assert_called_once()


def test_process_kill_works_without_a_name(process_class):
    _factory, proc = process_class

    run(processes.handle_process_kill({"pid": 1234}))

    proc.kill.assert_called_once()


def test_process_kill_refuses_when_the_pid_now_belongs_to_a_different_process(process_class):
    _factory, proc = process_class  # pid 1234 is "notepad.exe"

    with pytest.raises(ValueError, match="refresh the list"):
        run(processes.handle_process_kill({"pid": 1234, "name": "chrome.exe"}))

    proc.kill.assert_not_called()


@pytest.mark.parametrize("name", ["csrss.exe", "WinLogon.exe", "lsass.exe", "System", "wininit.exe"])
def test_process_kill_refuses_critical_windows_processes(process_class, name):
    _factory, proc = process_class
    proc.name.return_value = name

    with pytest.raises(ValueError, match="critical"):
        run(processes.handle_process_kill({"pid": 8}))

    proc.kill.assert_not_called()


def test_process_kill_refuses_to_kill_the_server_itself(process_class):
    factory, _proc = process_class

    with pytest.raises(ValueError, match="server itself"):
        run(processes.handle_process_kill({"pid": os.getpid()}))

    factory.assert_not_called()


@pytest.mark.parametrize("bad_pid", [None, "12", 1.5, True, 0, -4])
def test_process_kill_rejects_a_bad_pid(process_class, bad_pid):
    factory, _proc = process_class

    with pytest.raises(ValueError):
        run(processes.handle_process_kill({"pid": bad_pid}))

    factory.assert_not_called()


def test_process_kill_rejects_a_non_string_name(process_class):
    with pytest.raises(ValueError):
        run(processes.handle_process_kill({"pid": 5, "name": 7}))


def test_process_kill_reports_a_process_that_no_longer_exists(process_class):
    factory, _proc = process_class
    factory.side_effect = psutil.NoSuchProcess(1234)

    with pytest.raises(ValueError, match="no process"):
        run(processes.handle_process_kill({"pid": 1234}))


def test_process_kill_reports_access_denied_as_a_permission_error(process_class):
    _factory, proc = process_class
    proc.kill.side_effect = psutil.AccessDenied(1234)

    with pytest.raises(PermissionError, match="administrator"):
        run(processes.handle_process_kill({"pid": 1234}))


def test_process_kill_reports_a_process_that_will_not_die(process_class):
    _factory, proc = process_class
    proc.wait.side_effect = psutil.TimeoutExpired(3)

    with pytest.raises(RuntimeError, match="hasn't exited"):
        run(processes.handle_process_kill({"pid": 1234}))
