import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from peregrine import winsetup as win


class WindowsInstallTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.dest = self.root / "Light Client"
        self.dest.mkdir()
        (self.dest / win.EXE).write_bytes(b"old")
        (self.dest / "_internal").mkdir()
        (self.dest / "_internal" / "runtime").write_bytes(b"old")
        (self.dest / "my notes.txt").write_bytes(b"keep")

    def payload(self, runtime=True):
        file = self.root / "payload.zip"
        with zipfile.ZipFile(file, "w") as z:
            z.writestr(win.EXE, b"new")
            if runtime:
                z.writestr("_internal/runtime", b"new")
        return file

    def assert_version(self, version):
        for path in [win.EXE, "_internal/runtime"]:
            self.assertEqual((self.dest / path).read_bytes(), version)
        self.assertEqual((self.dest / "my notes.txt").read_bytes(), b"keep")

    def test_incomplete_update_preserves_install(self):
        with self.assertRaises(RuntimeError):
            win.install(self.payload(False), self.dest, "test")
        self.assert_version(b"old")

    def test_activation(self):
        stage = self.root / "staging"
        stage.mkdir()
        win._extract_payload(self.payload(), stage, lambda *_: None)
        win._activate_payload(stage, self.dest)
        self.assert_version(b"new")

    def test_activation_rolls_back_locked_exe(self):
        stage = self.root / "staging"
        stage.mkdir()
        win._extract_payload(self.payload(), stage, lambda *_: None)
        replace = os.replace

        def locked(src, dst):
            if src == stage / win.EXE:
                raise PermissionError("file in use")
            return replace(src, dst)

        with patch.object(win.os, "replace", side_effect=locked), self.assertRaises(PermissionError):
            win._activate_payload(stage, self.dest)
        self.assert_version(b"old")
