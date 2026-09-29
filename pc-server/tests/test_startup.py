import pytest

from server import startup


class _FakeKey:
    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        return False


@pytest.fixture
def fake_registry(monkeypatch):
    """A tiny in-memory stand-in for the Run key, so tests never touch the
    real Windows registry."""
    store: dict[str, str] = {}

    def fake_open_key(hive, path, *args, **kwargs):
        return _FakeKey()

    def fake_query_value_ex(key, name):
        if name not in store:
            raise FileNotFoundError()
        return (store[name], 1)

    def fake_set_value_ex(key, name, reserved, value_type, value):
        store[name] = value

    def fake_delete_value(key, name):
        if name not in store:
            raise FileNotFoundError()
        del store[name]

    monkeypatch.setattr(startup.winreg, "OpenKey", fake_open_key)
    monkeypatch.setattr(startup.winreg, "QueryValueEx", fake_query_value_ex)
    monkeypatch.setattr(startup.winreg, "SetValueEx", fake_set_value_ex)
    monkeypatch.setattr(startup.winreg, "DeleteValue", fake_delete_value)
    return store


def test_is_enabled_false_when_no_value_is_set(fake_registry):
    assert startup.is_enabled() is False


def test_set_enabled_true_writes_the_registry_value(fake_registry):
    startup.set_enabled(True)

    assert startup.RUN_VALUE_NAME in fake_registry
    assert startup.is_enabled() is True


def test_set_enabled_false_removes_the_value(fake_registry):
    startup.set_enabled(True)
    startup.set_enabled(False)

    assert startup.is_enabled() is False


def test_set_enabled_false_when_already_disabled_does_not_raise(fake_registry):
    startup.set_enabled(False)


def test_startup_command_uses_the_frozen_executable_path(monkeypatch):
    monkeypatch.setattr(startup.sys, "frozen", True, raising=False)
    monkeypatch.setattr(startup.sys, "executable", r"C:\Program Files\GestureLink\GestureLink.exe")

    assert startup._startup_command() == '"C:\\Program Files\\GestureLink\\GestureLink.exe"'


def test_startup_command_uses_interpreter_and_script_when_not_frozen(monkeypatch):
    monkeypatch.setattr(startup.sys, "frozen", False, raising=False)
    monkeypatch.setattr(startup.sys, "executable", r"C:\Python\python.exe")
    monkeypatch.setattr(startup.sys, "argv", [r"C:\GestureLink\pc-server\run.py"])

    assert startup._startup_command() == '"C:\\Python\\python.exe" "C:\\GestureLink\\pc-server\\run.py"'
