import json

import pytest
from pydantic import ValidationError

from server.protocol import CommandRequest, CommandResponse, PushMessage


def test_request_parses_all_fields():
    request = CommandRequest.model_validate_json(
        '{"id": "a1", "token": "t", "action": "mouse_move", "params": {"dx": 3, "dy": -4}}'
    )
    assert (request.id, request.token, request.action) == ("a1", "t", "mouse_move")
    assert request.params == {"dx": 3, "dy": -4}


def test_request_params_default_to_empty_and_are_not_shared():
    first = CommandRequest(id="1", token="t", action="ping")
    second = CommandRequest(id="2", token="t", action="ping")

    first.params["x"] = 1

    assert second.params == {}


@pytest.mark.parametrize("missing", ["id", "token", "action"])
def test_request_requires_id_token_and_action(missing):
    data = {"id": "1", "token": "t", "action": "ping"}
    del data[missing]

    with pytest.raises(ValidationError):
        CommandRequest.model_validate(data)


def test_request_rejects_non_object_params():
    with pytest.raises(ValidationError):
        CommandRequest.model_validate({"id": "1", "token": "t", "action": "ping", "params": [1, 2]})


def test_request_ignores_unknown_fields():
    request = CommandRequest.model_validate(
        {"id": "1", "token": "t", "action": "ping", "future_field": True}
    )
    assert request.action == "ping"


def test_successful_response_serializes_with_a_null_error():
    response = CommandResponse(id="1", ok=True, action="ping", result={"pong": True})
    assert json.loads(response.model_dump_json()) == {
        "id": "1",
        "ok": True,
        "action": "ping",
        "result": {"pong": True},
        "error": None,
    }


def test_failed_response_carries_the_error_and_an_empty_result():
    response = CommandResponse(id="1", ok=False, action="ping", error="boom")
    assert response.result == {}
    assert json.loads(response.model_dump_json())["error"] == "boom"


def test_response_requires_id_ok_and_action():
    with pytest.raises(ValidationError):
        CommandResponse.model_validate({"id": "1", "ok": True})


def test_push_has_no_id_so_the_phone_can_tell_it_from_a_response():
    payload = json.loads(PushMessage(push="battery_low", data={"battery_percent": 9}).model_dump_json())
    assert "id" not in payload
    assert payload == {"push": "battery_low", "data": {"battery_percent": 9}}


def test_push_data_defaults_to_empty():
    assert PushMessage(push="battery_low").data == {}


def test_push_requires_a_name():
    with pytest.raises(ValidationError):
        PushMessage.model_validate({"data": {}})


def test_request_survives_a_json_round_trip():
    original = CommandRequest(id="9", token="t", action="keyboard_type", params={"text": "héllo ✓"})
    assert CommandRequest.model_validate_json(original.model_dump_json()) == original
