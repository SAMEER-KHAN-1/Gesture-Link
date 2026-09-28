"""Registry of action handlers, keyed by the `action` name in the command protocol.

Each action module (system.py, power.py, network.py, ...) registers its handlers
with @register(name) so the websocket loop in server/main.py doesn't need to know
about every action individually — it just looks the name up here.
"""

from typing import Any, Awaitable, Callable

ActionHandler = Callable[[dict[str, Any]], Awaitable[dict[str, Any]]]

_registry: dict[str, ActionHandler] = {}


def register(action_name: str):
    def decorator(func: ActionHandler) -> ActionHandler:
        _registry[action_name] = func
        return func

    return decorator


def get_handler(action_name: str) -> ActionHandler | None:
    return _registry.get(action_name)


# Import submodules so their @register(...) calls actually run and populate the registry.
from . import apps, files, keyboard, media, mouse, network, power, system  # noqa: E402,F401
