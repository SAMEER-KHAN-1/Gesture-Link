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
    monkeypatch.setattr(files, "MAX_TRANSFER_BYTES", 10)
    a_file = tmp_path / "big.txt"
    a_file.write_text("this is way more than ten bytes")

    with pytest.raises(ValueError):
        run(files.handle_download_file({"path": str(a_file)}))


def test_upload_file_writes_decoded_bytes_into_the_target_dir(tmp_path):
    data = base64.b64encode(b"uploaded content").decode("ascii")

    result = run(files.handle_upload_file({"dir": str(tmp_path), "name": "new.txt", "data_base64": data}))

    assert result == {"name": "new.txt", "size": len(b"uploaded content")}
    assert (tmp_path / "new.txt").read_bytes() == b"uploaded content"


@pytest.mark.parametrize("missing_field", ["dir", "name", "data_base64"])
def test_upload_file_requires_all_fields(tmp_path, missing_field):
    params = {"dir": str(tmp_path), "name": "new.txt", "data_base64": base64.b64encode(b"x").decode("ascii")}
    del params[missing_field]

    with pytest.raises(ValueError):
        run(files.handle_upload_file(params))


@pytest.mark.parametrize("bad_name", ["../escape.txt", "sub/escape.txt", "..", "."])
def test_upload_file_rejects_names_that_are_not_a_bare_file_name(tmp_path, bad_name):
    data = base64.b64encode(b"x").decode("ascii")

    with pytest.raises(ValueError):
        run(files.handle_upload_file({"dir": str(tmp_path), "name": bad_name, "data_base64": data}))


def test_upload_file_rejects_a_nonexistent_directory(tmp_path):
    data = base64.b64encode(b"x").decode("ascii")

    with pytest.raises(NotADirectoryError):
        run(files.handle_upload_file({"dir": str(tmp_path / "nope"), "name": "new.txt", "data_base64": data}))


def test_upload_file_rejects_data_over_the_size_limit(tmp_path, monkeypatch):
    monkeypatch.setattr(files, "MAX_TRANSFER_BYTES", 10)
    data = base64.b64encode(b"this is way more than ten bytes").decode("ascii")

    with pytest.raises(ValueError):
        run(files.handle_upload_file({"dir": str(tmp_path), "name": "big.txt", "data_base64": data}))
