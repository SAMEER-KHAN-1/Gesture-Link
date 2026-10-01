from server import pairing_qr


def test_payload_carries_the_lan_ip_the_port_and_the_token(monkeypatch):
    monkeypatch.setattr(pairing_qr, "get_lan_ip", lambda: "192.168.1.20")
    monkeypatch.setattr(pairing_qr, "load_config", lambda: {"port": 9000, "pairing_token": "abc12345"})

    assert pairing_qr.build_pairing_payload() == "gesturelink://192.168.1.20:9000?token=abc12345"


def test_payload_uses_the_default_port_when_the_configured_one_is_invalid(monkeypatch):
    monkeypatch.setattr(pairing_qr, "get_lan_ip", lambda: "192.168.1.20")
    monkeypatch.setattr(pairing_qr, "load_config", lambda: {"port": "nope", "pairing_token": "abc12345"})

    assert pairing_qr.build_pairing_payload() == "gesturelink://192.168.1.20:8765?token=abc12345"
