import logging
from logging.handlers import RotatingFileHandler

import pytest

from server import logging_setup


@pytest.fixture(autouse=True)
def isolated_logging(tmp_path, monkeypatch):
    """Throwaway app data folder, and put the root logger back how we found it."""
    monkeypatch.setenv("LOCALAPPDATA", str(tmp_path))
    root = logging.getLogger()
    original_handlers = list(root.handlers)
    original_level = root.level
    yield
    for handler in list(root.handlers):
        if handler not in original_handlers:
            handler.close()
            root.removeHandler(handler)
    root.setLevel(original_level)


def _file_handlers():
    return [h for h in logging.getLogger().handlers if isinstance(h, RotatingFileHandler)]


def test_log_path_is_inside_a_logs_folder_in_the_app_data_dir(tmp_path):
    path = logging_setup.get_log_path()

    assert path.parent == tmp_path / "GestureLink" / "logs"
    assert path.name == logging_setup.LOG_FILE_NAME
    assert path.parent.is_dir()


def test_setup_adds_a_rotating_file_handler_with_the_size_cap():
    logging_setup.setup_logging()

    (handler,) = _file_handlers()
    assert handler.maxBytes == logging_setup.MAX_LOG_BYTES
    assert handler.backupCount == logging_setup.BACKUP_COUNT


def test_calling_setup_twice_does_not_duplicate_handlers():
    logging_setup.setup_logging()
    logging_setup.setup_logging()

    assert len(_file_handlers()) == 1


def test_messages_end_up_in_the_log_file():
    logging_setup.setup_logging()

    logging.getLogger("gesturelink").info("hello from the test")
    for handler in _file_handlers():
        handler.flush()

    contents = logging_setup.get_log_path().read_text(encoding="utf-8")
    assert "hello from the test" in contents
    assert "INFO" in contents


def test_setup_works_when_there_is_no_console(monkeypatch):
    monkeypatch.setattr(logging_setup.sys, "stdout", None)

    logging_setup.setup_logging()

    assert len(_file_handlers()) == 1
