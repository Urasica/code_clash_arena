import tempfile
import unittest
import zipfile
from pathlib import Path

from deploy.common.archive import ArchiveError, create_archive


class DeterministicArchiveTest(unittest.TestCase):
    def test_same_content_produces_identical_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            root = parent / "bundle"
            root.mkdir()
            (root / "b.txt").write_text("b", encoding="utf-8")
            (root / "nested").mkdir()
            (root / "nested" / "a.txt").write_text("a", encoding="utf-8")
            first = parent / "first.zip"
            second = parent / "second.zip"
            create_archive(root, first)
            (root / "b.txt").touch()
            create_archive(root, second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with zipfile.ZipFile(first) as archive:
                self.assertEqual(archive.namelist(), ["b.txt", "nested/a.txt"])

    def test_output_inside_source_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "file").write_text("x", encoding="utf-8")
            with self.assertRaises(ArchiveError):
                create_archive(root, root / "bundle.zip")


if __name__ == "__main__":
    unittest.main()
