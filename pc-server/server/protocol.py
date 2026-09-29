"""Pydantic models for the command protocol described in docs/ARCHITECTURE.md."""

from typing import Any, Optional

from pydantic import BaseModel, Field


class CommandRequest(BaseModel):
    id: str
    token: str
    action: str
    params: dict[str, Any] = Field(default_factory=dict)


class CommandResponse(BaseModel):
    id: str
    ok: bool
    action: str
    result: dict[str, Any] = Field(default_factory=dict)
    error: Optional[str] = None


class PushMessage(BaseModel):
    """Sent unprompted (not in reply to a request) - e.g. a low-battery notice.

    Has no `id` field since nothing on the phone is waiting for a specific
    response; the phone's websocket listener tells this apart from a
    CommandResponse by the presence of the `push` key.
    """

    push: str
    data: dict[str, Any] = Field(default_factory=dict)
