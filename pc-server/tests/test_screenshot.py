import asyncio
import base64
import io

import pytest
from PIL import Image

from server.actions import screenshot


def run(coro):
    return asyncio.run(coro)


@pytest.fixture
def fake_screen(monkeypatch):
    """Swap the real screen grab for a solid-colour image of a chosen size."""

    def install(width, height, mode="RGB"):
        monkeypatch.setattr(
            screenshot.ImageGrab, "grab", lambda: Image.new(mode, (width, height), (200, 30, 30))
        )

    return install


def _decode(result: dict) -> Image.Image:
    return Image.open(io.BytesIO(base64.b64decode(result["data_base64"])))


def test_screenshot_downscales_wide_screens_keeping_aspect_ratio(fake_screen):
    fake_screen(2560, 1440)

    result = run(screenshot.handle_screenshot({}))

    assert (result["width"], result["height"]) == (1280, 720)
    image = _decode(result)
    assert image.format == "JPEG"
    assert image.size == (1280, 720)


def test_screenshot_leaves_small_screens_at_their_native_size(fake_screen):
    fake_screen(800, 600)

    result = run(screenshot.handle_screenshot({}))

    assert (result["width"], result["height"]) == (800, 600)


def test_screenshot_honours_a_requested_max_width(fake_screen):
    fake_screen(1920, 1200)

    result = run(screenshot.handle_screenshot({"max_width": 640}))

    assert (result["width"], result["height"]) == (640, 400)


def test_screenshot_clamps_max_width_to_the_allowed_range(fake_screen):
    fake_screen(4000, 2000)

    too_big = run(screenshot.handle_screenshot({"max_width": 99999}))
    too_small = run(screenshot.handle_screenshot({"max_width": 1}))

    assert too_big["width"] == screenshot.MAX_MAX_WIDTH
    assert too_small["width"] == screenshot.MIN_MAX_WIDTH


def test_screenshot_handles_captures_with_an_alpha_channel(fake_screen):
    fake_screen(400, 300, mode="RGBA")

    result = run(screenshot.handle_screenshot({}))

    assert _decode(result).mode == "RGB"


@pytest.mark.parametrize("bad_width", ["wide", 12.5, True, None])
def test_screenshot_rejects_a_non_integer_max_width(fake_screen, bad_width):
    fake_screen(800, 600)

    with pytest.raises(ValueError):
        run(screenshot.handle_screenshot({"max_width": bad_width}))
