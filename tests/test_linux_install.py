import os
import tempfile
import unittest
from pathlib import Path

from peregrine import linuxsetup


class LinuxInstallTests(unittest.TestCase):
    def test_install_then_uninstall_keeps_data(self):
        with tempfile.TemporaryDirectory() as home:
            os.environ.update(HOME=home, XDG_DATA_HOME=f"{home}/data", XDG_CONFIG_HOME=f"{home}/cfg")
            payload = Path(home) / "payload"
            payload.write_bytes(b"x")
            target = Path(home) / "Applications"
            linuxsetup.install(payload, target, "9.9.9", False, False)
            self.assertTrue((target / linuxsetup.IMAGE_NAME).is_file())
            entry = Path(home, "data/applications/peregrine.desktop").read_text()
            self.assertIn("Desktop Action uninstall", entry)
            keep = Path(home, "data/peregrine/instances")
            keep.mkdir(parents=True)
            linuxsetup.uninstall(target, False, log=lambda *_: None)
            self.assertFalse((target / linuxsetup.IMAGE_NAME).exists())
            self.assertFalse(Path(home, "data/applications/peregrine.desktop").exists())
            self.assertTrue(keep.is_dir())


if __name__ == "__main__":
    unittest.main()
