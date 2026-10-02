import asyncio
import json
from unittest.mock import AsyncMock

from server.protocol import PushMessage
from server.ws_manager import ConnectionManager


def run(coro):
    return asyncio.run(coro)


def _socket(send_error=None):
    socket = AsyncMock()
    if send_error is not None:
        socket.send_text.side_effect = send_error
    return socket


def test_connect_accepts_and_tracks_the_socket():
    manager = ConnectionManager()
    socket = _socket()

    run(manager.connect(socket))

    socket.accept.assert_awaited_once()
    assert manager.active_connections == [socket]


def test_connect_tracks_several_sockets_in_order():
    manager = ConnectionManager()
    first, second = _socket(), _socket()

    run(manager.connect(first))
    run(manager.connect(second))

    assert manager.active_connections == [first, second]


def test_disconnect_forgets_only_that_socket():
    manager = ConnectionManager()
    first, second = _socket(), _socket()
    run(manager.connect(first))
    run(manager.connect(second))

    manager.disconnect(first)

    assert manager.active_connections == [second]


def test_disconnecting_an_unknown_socket_is_harmless():
    manager = ConnectionManager()
    kept = _socket()
    run(manager.connect(kept))

    manager.disconnect(_socket())

    assert manager.active_connections == [kept]


def test_disconnecting_twice_is_harmless():
    manager = ConnectionManager()
    socket = _socket()
    run(manager.connect(socket))

    manager.disconnect(socket)
    manager.disconnect(socket)

    assert manager.active_connections == []


def test_broadcast_sends_the_push_as_json_to_every_socket():
    manager = ConnectionManager()
    first, second = _socket(), _socket()
    run(manager.connect(first))
    run(manager.connect(second))

    run(manager.broadcast(PushMessage(push="battery_low", data={"battery_percent": 12})))

    for socket in (first, second):
        socket.send_text.assert_awaited_once()
        assert json.loads(socket.send_text.call_args[0][0]) == {
            "push": "battery_low",
            "data": {"battery_percent": 12},
        }


def test_broadcast_with_no_connections_does_nothing():
    run(ConnectionManager().broadcast(PushMessage(push="battery_low")))


def test_a_failing_socket_does_not_stop_the_broadcast_to_the_rest():
    manager = ConnectionManager()
    dead = _socket(send_error=RuntimeError("socket closing"))
    alive = _socket()
    run(manager.connect(dead))
    run(manager.connect(alive))

    run(manager.broadcast(PushMessage(push="battery_low")))

    alive.send_text.assert_awaited_once()


def test_broadcast_does_not_drop_a_failing_socket_itself():
    # Cleanup belongs to the receive loop's disconnect handler in main.py.
    manager = ConnectionManager()
    dead = _socket(send_error=RuntimeError("socket closing"))
    run(manager.connect(dead))

    run(manager.broadcast(PushMessage(push="battery_low")))

    assert manager.active_connections == [dead]


def test_a_socket_disconnecting_during_broadcast_does_not_break_iteration():
    manager = ConnectionManager()
    second = _socket()

    async def drop_itself(_payload):
        manager.disconnect(first)

    first = _socket()
    first.send_text.side_effect = drop_itself
    run(manager.connect(first))
    run(manager.connect(second))

    run(manager.broadcast(PushMessage(push="battery_low")))

    second.send_text.assert_awaited_once()
    assert manager.active_connections == [second]
