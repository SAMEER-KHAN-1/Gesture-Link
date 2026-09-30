import asyncio
import subprocess
from unittest.mock import MagicMock

import pytest

from server.actions import brightness


def run(coro):
    return asyncio.run(coro)


def _completed(returncode=0, stdout="", stderr=""):
    return subprocess.CompletedProcess(args=[], returncode=returncode, stdout=stdout, stderr=stderr)


@pytest.fixture
def subprocess_run(monkeypatch):
    mock = MagicMock(return_value=_completed(stdout="65\r\n"))
    monkeypatch.setattr(brightness.subprocess, "run", mock)
    return mock


def test_brightness_get_returns_the_scripts_value_as_an_int(subprocess_run):
    result = run(brightness.handle_brightness_get({}))

    assert result == {"brightness": 65}
    args = subprocess_run.call_args[0][0]
    assert str(brightness.SCRIPT_PATH) in args
    assert args[args.index("-Action") + 1] == "Get"


def test_brightness_get_raises_the_scripts_error_message(monkeypatch):
    monkeypatch.setattr(
        brightness.subprocess,
        "run",
        MagicMock(return_value=_completed(returncode=1, stderr="not supported\r\n")),
    )

    with pytest.raises(RuntimeError, match="not supported"):
        run(brightness.handle_brightness_get({}))


def test_brightness_get_rejects_garbage_output(monkeypatch):
    monkeypatch.setattr(brightness.subprocess, "run", MagicMock(return_value=_completed(stdout="hello")))

    with pytest.raises(RuntimeError, match="unexpected"):
        run(brightness.handle_brightness_get({}))


def test_brightness_set_runs_the_script_with_the_level(subprocess_run):
    result = run(brightness.handle_brightness_set({"level": 40}))

    assert result == {"brightness": 40}
    args = subprocess_run.call_args[0][0]
    assert args[args.index("-Action") + 1] == "Set"
    assert args[args.index("-Level") + 1] == "40"


@pytest.mark.parametrize("level", [0, 100])
def test_brightness_set_accepts_the_boundary_levels(subprocess_run, level):
    assert run(brightness.handle_brightness_set({"level": level})) == {"brightness": level}


@pytest.mark.parametrize("bad_level", [None, "50", 50.5, True, -1, 101])
def test_brightness_set_rejects_bad_levels_without_running_anything(subprocess_run, bad_level):
    with pytest.raises(ValueError):
        run(brightness.handle_brightness_set({"level": bad_level}))

    subprocess_run.assert_not_called()


def test_brightness_set_raises_the_scripts_error_message(monkeypatch):
    monkeypatch.setattr(
        brightness.subprocess,
        "run",
        MagicMock(return_value=_completed(returncode=1, stderr="not supported")),
    )

    with pytest.raises(RuntimeError, match="not supported"):
        run(brightness.handle_brightness_set({"level": 50}))
