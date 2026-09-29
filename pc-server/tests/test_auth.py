from server import auth


def _mock_config(monkeypatch):
    monkeypatch.setattr(auth, "load_config", lambda: {"pairing_token": "good-token"})


def test_correct_token_is_valid(monkeypatch):
    _mock_config(monkeypatch)
    assert auth.is_token_valid("good-token", "10.0.0.1") is True


def test_wrong_token_is_invalid(monkeypatch):
    _mock_config(monkeypatch)
    assert auth.is_token_valid("wrong", "10.0.0.2") is False


def test_locks_out_after_max_failed_attempts(monkeypatch):
    _mock_config(monkeypatch)
    address = "10.0.0.3"

    for _ in range(auth.MAX_FAILED_ATTEMPTS):
        auth.is_token_valid("wrong", address)

    assert auth.is_locked_out(address) is True


def test_fewer_than_max_failed_attempts_does_not_lock_out(monkeypatch):
    _mock_config(monkeypatch)
    address = "10.0.0.4"

    for _ in range(auth.MAX_FAILED_ATTEMPTS - 1):
        auth.is_token_valid("wrong", address)

    assert auth.is_locked_out(address) is False


def test_successful_auth_resets_failed_attempt_count(monkeypatch):
    _mock_config(monkeypatch)
    address = "10.0.0.5"

    for _ in range(auth.MAX_FAILED_ATTEMPTS - 1):
        auth.is_token_valid("wrong", address)
    auth.is_token_valid("good-token", address)

    for _ in range(auth.MAX_FAILED_ATTEMPTS - 1):
        auth.is_token_valid("wrong", address)

    assert auth.is_locked_out(address) is False


def test_lockout_expires_after_cooldown(monkeypatch):
    _mock_config(monkeypatch)
    address = "10.0.0.6"

    fake_now = [1000.0]
    monkeypatch.setattr(auth.time, "monotonic", lambda: fake_now[0])

    for _ in range(auth.MAX_FAILED_ATTEMPTS):
        auth.is_token_valid("wrong", address)
    assert auth.is_locked_out(address) is True

    fake_now[0] += auth.LOCKOUT_SECONDS + 1
    assert auth.is_locked_out(address) is False
