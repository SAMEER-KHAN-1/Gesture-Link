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
