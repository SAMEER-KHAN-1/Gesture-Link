import json

import pytest

from server import auth, config


@pytest.fixture(autouse=True)
def isolated_app_data(tmp_path, monkeypatch):
    """Point the config at a throwaway folder so tests never touch the real one."""
    monkeypatch.setenv("LOCALAPPDATA", str(tmp_path))
    auth._failed_attempts.clear()
    auth._locked_until.clear()


def test_load_config_creates_a_config_with_a_token_on_first_run():
    created = config.load_config()

    assert len(created["pairing_token"]) == 8
    assert config.get_config_path().exists()


def test_load_config_returns_the_same_token_on_later_calls():
    assert config.load_config()["pairing_token"] == config.load_config()["pairing_token"]


def test_regenerate_returns_a_different_token():
    old_token = config.load_config()["pairing_token"]

    new_token = config.regenerate_pairing_token()

    assert new_token != old_token


def test_regenerate_persists_the_new_token():
    new_token = config.regenerate_pairing_token()

    assert config.load_config()["pairing_token"] == new_token
    on_disk = json.loads(config.get_config_path().read_text(encoding="utf-8"))
    assert on_disk["pairing_token"] == new_token


def test_regenerate_keeps_the_other_settings():
    config.save_config({"host": "192.168.1.5", "port": 9999, "pairing_token": "oldtoken"})

    config.regenerate_pairing_token()

    reloaded = config.load_config()
    assert reloaded["host"] == "192.168.1.5"
    assert reloaded["port"] == 9999


def test_resolve_port_uses_the_configured_port():
    assert config.resolve_port({"port": 9000}) == 9000


def test_resolve_port_defaults_when_the_key_is_missing():
    assert config.resolve_port({}) == config.DEFAULT_PORT


@pytest.mark.parametrize("bad_port", ["9000", 0, -1, 65536, None, 80.5, True])
def test_resolve_port_falls_back_to_the_default_for_unusable_values(bad_port):
    assert config.resolve_port({"port": bad_port}) == config.DEFAULT_PORT


def test_resolve_port_accepts_the_edges_of_the_valid_range():
    assert config.resolve_port({"port": 1}) == 1
    assert config.resolve_port({"port": 65535}) == 65535


def test_old_token_stops_working_and_new_one_works_after_regenerating():
    old_token = config.load_config()["pairing_token"]
    assert auth.is_token_valid(old_token, "10.0.0.1") is True

    new_token = config.regenerate_pairing_token()

    assert auth.is_token_valid(old_token, "10.0.0.1") is False
    assert auth.is_token_valid(new_token, "10.0.0.1") is True
