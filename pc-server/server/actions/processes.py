"""Task-manager style process listing and killing.

Killing is the one action here that can seriously hurt the PC, so it refuses a
short list of Windows-critical processes (ending any of them blue-screens or
force-reboots the machine) and the server's own process, and it can be given the
name the phone saw so a stale list can't kill an unrelated process that happened
to be handed a recycled PID.
"""

import asyncio
import os

import psutil

from server.actions import register

# Cap the reply size: a typical desktop has 200-400 processes, most of them tiny.
MAX_LISTED_PROCESSES = 200

# Ending any of these takes Windows down (or logs you out). Lowercase for comparison.
PROTECTED_NAMES = {
    "system",
    "system idle process",
    "registry",
    "smss.exe",
    "csrss.exe",
    "wininit.exe",
    "winlogon.exe",
    "services.exe",
    "lsass.exe",
}

_BYTES_PER_MB = 1024 * 1024


def _list_processes() -> list[dict]:
    processes = []
    for proc in psutil.process_iter(["pid", "name", "memory_info"]):
        info = proc.info
        memory = info.get("memory_info")  # None when access is denied
        processes.append(
            {
                "pid": info["pid"],
                "name": info.get("name") or "?",
                "memory_mb": round(memory.rss / _BYTES_PER_MB, 1) if memory else 0.0,
            }
        )
    processes.sort(key=lambda p: p["memory_mb"], reverse=True)
    return processes[:MAX_LISTED_PROCESSES]


def _kill_process(pid: int, expected_name: str | None) -> str:
    if pid == os.getpid():
        raise ValueError("refusing to kill the GestureLink server itself")

    try:
        proc = psutil.Process(pid)
        name = proc.name()
        if expected_name is not None and name.lower() != expected_name.lower():
            raise ValueError(f"pid {pid} is now '{name}', not '{expected_name}' - refresh the list")
        if name.lower() in PROTECTED_NAMES:
            raise ValueError(f"'{name}' is a critical Windows process and can't be ended")
        proc.kill()
        proc.wait(timeout=3)
    except psutil.NoSuchProcess:
        raise ValueError(f"no process with pid {pid} (it may have already exited)")
    except psutil.AccessDenied:
        raise PermissionError(f"Windows denied access to pid {pid} - it needs administrator rights to end")
    except psutil.TimeoutExpired:
        raise RuntimeError(f"asked Windows to end pid {pid} but it hasn't exited yet")
    return name


@register("process_list")
async def handle_process_list(params: dict) -> dict:
    # Walking every process can take a few hundred ms; keep it off the event loop.
    return {"processes": await asyncio.to_thread(_list_processes)}


@register("process_kill")
async def handle_process_kill(params: dict) -> dict:
    pid = params.get("pid")
    if not isinstance(pid, int) or isinstance(pid, bool) or pid <= 0:
        raise ValueError("'pid' must be a positive integer")

    expected_name = params.get("name")
    if expected_name is not None and not isinstance(expected_name, str):
        raise ValueError("'name' must be a string")

    name = await asyncio.to_thread(_kill_process, pid, expected_name)
    return {"message": f"ended {name} (pid {pid})"}
