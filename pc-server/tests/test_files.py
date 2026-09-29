import asyncio
import base64

import pytest

from server.actions import files


def run(coro):
    return asyncio.run(coro)


def test_list_dir_with_empty_path_lists_drives():
    result = run(files.handle_list_dir({}))
    assert result["path"] == ""
    names = [entry["name"] for entry in result["entries"]]
    assert "C:" in names
    assert all(entry["is_dir"] for entry in result["entries"])


def test_list_dir_lists_files_and_folders_sorted_dirs_first(tmp_path):
    (tmp_path / "b_file.txt").write_text("hello")
    (tmp_path / "a_folder").mkdir()

    result = run(files.handle_list_dir({"path": str(tmp_path)}))

    assert result["path"] == str(tmp_path)
    names = [entry["name"] for entry in result["entries"]]
    assert names == ["a_folder", "b_file.txt"]
    file_entry = next(e for e in result["entries"] if e["name"] == "b_file.txt")
    assert file_entry["is_dir"] is False
    assert file_entry["size"] == len("hello")


def test_list_dir_rejects_a_file_path(tmp_path):
    a_file = tmp_path / "not_a_dir.txt"
    a_file.write_text("x")

    with pytest.raises(NotADirectoryError):
        run(files.handle_list_dir({"path": str(a_file)}))


def test_download_file_returns_base64_content(tmp_path):
    a_file = tmp_path / "hello.txt"
    a_file.write_text("hello world")

    result = run(files.handle_download_file({"path": str(a_file)}))

    assert result["name"] == "hello.txt"
    assert result["size"] == len("hello world")
    assert base64.b64decode(result["data_base64"]) == b"hello world"


def test_download_file_requires_path():
    with pytest.raises(ValueError):
        run(files.handle_download_file({}))


def test_download_file_rejects_missing_file(tmp_path):
    with pytest.raises(FileNotFoundError):
        run(files.handle_download_file({"path": str(tmp_path / "nope.txt")}))


def test_download_file_rejects_files_over_the_size_limit(tmp_path, monkeypatch):
    monkeypatch.setattr(files, "MAX_DOWNLOAD_BYTES", 10)
    a_file = tmp_path / "big.txt"
    a_file.write_text("this is way more than ten bytes")

    with pytest.raises(ValueError):
        run(files.handle_download_file({"path": str(a_file)}))
