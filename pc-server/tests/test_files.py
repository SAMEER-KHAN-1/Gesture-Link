import asyncio
import base64
import ctypes
from unittest.mock import MagicMock

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


# --- create_folder ---


def test_create_folder_makes_the_folder_and_returns_its_path(tmp_path):
    result = run(files.handle_create_folder({"dir": str(tmp_path), "name": "New Folder"}))

    assert result == {"path": str(tmp_path / "New Folder")}
    assert (tmp_path / "New Folder").is_dir()


@pytest.mark.parametrize("missing_field", ["dir", "name"])
def test_create_folder_requires_all_fields(tmp_path, missing_field):
    params = {"dir": str(tmp_path), "name": "x"}
    del params[missing_field]

    with pytest.raises(ValueError):
        run(files.handle_create_folder(params))


@pytest.mark.parametrize("bad_name", ["../escape", "sub/folder", "..", "."])
def test_create_folder_rejects_names_that_are_not_a_bare_name(tmp_path, bad_name):
    with pytest.raises(ValueError):
        run(files.handle_create_folder({"dir": str(tmp_path), "name": bad_name}))


def test_create_folder_rejects_a_nonexistent_parent(tmp_path):
    with pytest.raises(NotADirectoryError):
        run(files.handle_create_folder({"dir": str(tmp_path / "nope"), "name": "x"}))


def test_create_folder_rejects_a_name_that_already_exists(tmp_path):
    (tmp_path / "taken").mkdir()

    with pytest.raises(FileExistsError):
        run(files.handle_create_folder({"dir": str(tmp_path), "name": "taken"}))


# --- rename_path ---


def test_rename_path_renames_a_file(tmp_path):
    original = tmp_path / "old.txt"
    original.write_text("content")

    result = run(files.handle_rename_path({"path": str(original), "new_name": "new.txt"}))

    assert result == {"path": str(tmp_path / "new.txt")}
    assert not original.exists()
    assert (tmp_path / "new.txt").read_text() == "content"


def test_rename_path_renames_a_folder(tmp_path):
    (tmp_path / "old_dir").mkdir()

    run(files.handle_rename_path({"path": str(tmp_path / "old_dir"), "new_name": "new_dir"}))

    assert (tmp_path / "new_dir").is_dir()
    assert not (tmp_path / "old_dir").exists()


@pytest.mark.parametrize("missing_field", ["path", "new_name"])
def test_rename_path_requires_all_fields(tmp_path, missing_field):
    params = {"path": str(tmp_path / "a.txt"), "new_name": "b.txt"}
    del params[missing_field]

    with pytest.raises(ValueError):
        run(files.handle_rename_path(params))


@pytest.mark.parametrize("bad_name", ["../escape.txt", "sub/b.txt", "..", "."])
def test_rename_path_rejects_new_names_that_are_not_a_bare_name(tmp_path, bad_name):
    original = tmp_path / "a.txt"
    original.write_text("x")

    with pytest.raises(ValueError):
        run(files.handle_rename_path({"path": str(original), "new_name": bad_name}))

    assert original.exists()


def test_rename_path_rejects_a_missing_source(tmp_path):
    with pytest.raises(FileNotFoundError):
        run(files.handle_rename_path({"path": str(tmp_path / "nope.txt"), "new_name": "x.txt"}))


def test_rename_path_will_not_overwrite_an_existing_file(tmp_path):
    (tmp_path / "a.txt").write_text("a")
    (tmp_path / "b.txt").write_text("b")

    with pytest.raises(FileExistsError):
        run(files.handle_rename_path({"path": str(tmp_path / "a.txt"), "new_name": "b.txt"}))

    assert (tmp_path / "b.txt").read_text() == "b"


def test_rename_path_refuses_a_drive_root():
    with pytest.raises(ValueError):
        run(files.handle_rename_path({"path": "C:\\", "new_name": "D"}))


# --- delete_path ---


@pytest.fixture
def recycle_calls(monkeypatch):
    calls = []
    monkeypatch.setattr(files, "_send_to_recycle_bin", calls.append)
    return calls


def test_delete_path_sends_the_path_to_the_recycle_bin(tmp_path, recycle_calls):
    target = tmp_path / "junk.txt"
    target.write_text("x")

    assert run(files.handle_delete_path({"path": str(target)})) == {}

    assert recycle_calls == [str(target)]


def test_delete_path_works_on_folders_too(tmp_path, recycle_calls):
    folder = tmp_path / "old_stuff"
    folder.mkdir()

    run(files.handle_delete_path({"path": str(folder)}))

    assert recycle_calls == [str(folder)]


def test_delete_path_requires_a_path(recycle_calls):
    with pytest.raises(ValueError):
        run(files.handle_delete_path({}))

    assert recycle_calls == []


def test_delete_path_rejects_a_missing_path(tmp_path, recycle_calls):
    with pytest.raises(FileNotFoundError):
        run(files.handle_delete_path({"path": str(tmp_path / "nope.txt")}))

    assert recycle_calls == []


def test_delete_path_refuses_a_drive_root(recycle_calls):
    with pytest.raises(ValueError):
        run(files.handle_delete_path({"path": "C:\\"}))

    assert recycle_calls == []


# --- the actual Recycle Bin call ---


@pytest.fixture
def shell32(monkeypatch):
    """Stand-in for ctypes.windll.shell32 that records the SHFileOperationW request."""
    seen = {}

    def fake_sh_file_operation(byref_arg):
        operation = byref_arg._obj
        seen["func"] = operation.wFunc
        seen["flags"] = operation.fFlags
        # The path list must be double-null terminated: path, \0, \0.
        seen["from"] = ctypes.wstring_at(operation.pFrom, seen["expected_length"])
        return seen.get("result", 0)

    windll = MagicMock()
    windll.shell32.SHFileOperationW.side_effect = fake_sh_file_operation
    monkeypatch.setattr(files.ctypes, "windll", windll, raising=False)
    return seen


def test_recycle_bin_call_asks_for_an_undoable_delete(shell32):
    path = "C:\\Users\\sam\\junk.txt"
    shell32["expected_length"] = len(path) + 2

    files._send_to_recycle_bin(path)

    assert shell32["func"] == files.FO_DELETE
    assert shell32["flags"] & files.FOF_ALLOWUNDO
    assert shell32["from"] == path + "\0\0"


def test_recycle_bin_call_does_not_suppress_the_permanent_delete_confirmation(shell32):
    shell32["expected_length"] = 6

    files._send_to_recycle_bin("C:\\a")

    assert not shell32["flags"] & 0x0010  # FOF_NOCONFIRMATION


def test_recycle_bin_call_raises_when_windows_reports_an_error(shell32):
    shell32["expected_length"] = 6
    shell32["result"] = 0x7C

    with pytest.raises(OSError):
        files._send_to_recycle_bin("C:\\a")
