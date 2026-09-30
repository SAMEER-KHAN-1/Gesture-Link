"""Capture the PC's screen so the phone can see what's on it.

Only the primary monitor is captured, downscaled and JPEG-compressed: a raw
full-resolution frame base64'd into a JSON message would be several MB, and a
phone screen can't show that much detail anyway.
"""

import asyncio
import base64
import io

from PIL import Image, ImageGrab

from server.actions import register

DEFAULT_MAX_WIDTH = 1280
MIN_MAX_WIDTH = 320
MAX_MAX_WIDTH = 1920
JPEG_QUALITY = 70


def _capture(max_width: int) -> dict:
    image = ImageGrab.grab().convert("RGB")  # JPEG has no alpha channel
    if image.width > max_width:
        new_height = max(1, round(image.height * max_width / image.width))
        image = image.resize((max_width, new_height), Image.LANCZOS)

    buffer = io.BytesIO()
    image.save(buffer, format="JPEG", quality=JPEG_QUALITY)
    return {
        "width": image.width,
        "height": image.height,
        "data_base64": base64.b64encode(buffer.getvalue()).decode("ascii"),
    }


@register("screenshot")
async def handle_screenshot(params: dict) -> dict:
    max_width = params.get("max_width", DEFAULT_MAX_WIDTH)
    # bool is an int subclass in Python; reject it so `true` isn't read as a width of 1.
    if not isinstance(max_width, int) or isinstance(max_width, bool):
        raise ValueError("'max_width' must be an integer")
    max_width = max(MIN_MAX_WIDTH, min(MAX_MAX_WIDTH, max_width))

    # Grabbing + resizing + encoding a frame takes long enough to stall the event
    # loop (and every other connected client), so do it off-thread.
    return await asyncio.to_thread(_capture, max_width)
