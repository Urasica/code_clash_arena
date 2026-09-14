import json
import os
import tempfile
import unittest
from pathlib import Path

from deploy.common.releasectl import (
    MANIFEST_NAME,
    ReleaseError,
    create_manifest,
    install_release,
    rollback_release,
    verify_release,
)

SOURCE_A = "a" * 40
SOURCE_B = "b" * 40
IMAGE = "nrt.ocir.io/example/engine@sha256:" + "1" * 64
MIGRATION_IMAGE = "nrt.ocir.io/example/flyway@sha256:" + "2" * 64
MYSQL_IMAGE = "docker.io/library/mysql@sha256:" + "3" * 64
REDIS_IMAGE = "docker.io/library/redis@sha256:" + "4" * 64


def backend_metadata():
    return {
        "data_mysql_image": MYSQL_IMAGE,
        "data_redis_image": REDIS_IMAGE,
        "engine_image": IMAGE,
        "engine_policy_version": "m4-v1",
        "migration_image": MIGRATION_IMAGE,
    }


class ReleaseControlTest(unittest.TestCase):

    def make_release(self, parent: Path, name: str, source_sha: str) -> Path:
        root = parent / name
        root.mkdir()
        (root / "app.jar").write_bytes(b"jar-" + source_sha.encode("ascii"))
        (root / "scripts").mkdir()
        (root / "scripts" / "install.sh").write_text("#!/bin/sh\n", encoding="utf-8")
        create_manifest(root, "backend", source_sha, backend_metadata())
        return root

    def test_create_and_verify_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.make_release(Path(directory), "staging", SOURCE_A)
            manifest = verify_release(root, "backend", SOURCE_A)
            self.assertEqual(manifest["target"], "linux/amd64")
            self.assertEqual([item["path"] for item in manifest["files"]], ["app.jar", "scripts/install.sh"])

    def test_content_tamper_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.make_release(Path(directory), "staging", SOURCE_A)
            (root / "app.jar").write_bytes(b"tampered")
            with self.assertRaises(ReleaseError):
                verify_release(root, "backend", SOURCE_A)

    def test_extra_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.make_release(Path(directory), "staging", SOURCE_A)
            (root / "unexpected").write_text("x", encoding="utf-8")
            with self.assertRaises(ReleaseError):
                verify_release(root, "backend", SOURCE_A)

    def test_manifest_identity_is_enforced(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.make_release(Path(directory), "staging", SOURCE_A)
            with self.assertRaises(ReleaseError):
                verify_release(root, "frontend", SOURCE_A)
            with self.assertRaises(ReleaseError):
                verify_release(root, "backend", SOURCE_B)

    def test_plain_image_tags_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "app.jar").write_bytes(b"jar")
            metadata = backend_metadata()
            metadata["engine_image"] = "nrt.ocir.io/example/engine:latest"
            with self.assertRaises(ReleaseError):
                create_manifest(root, "backend", SOURCE_A, metadata)

    def test_frontend_requires_https_origin(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "index.html").write_text("ok", encoding="utf-8")
            with self.assertRaises(ReleaseError):
                create_manifest(root, "frontend", SOURCE_A, {"public_origin": "http://example.com"})

    def test_frontend_rejects_ip_origin(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "index.html").write_text("ok", encoding="utf-8")
            with self.assertRaises(ReleaseError):
                create_manifest(root, "frontend", SOURCE_A, {"public_origin": "https://203.0.113.10"})

    @unittest.skipIf(os.name == "nt", "Windows symlink creation is not available on every runner")
    def test_install_and_rollback_use_immutable_release_links(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            release_root = parent / "installed"
            first = self.make_release(parent, "first", SOURCE_A)
            second = self.make_release(parent, "second", SOURCE_B)

            install_release(first, release_root, "backend", SOURCE_A)
            install_release(second, release_root, "backend", SOURCE_B)
            self.assertEqual(os.readlink(release_root / "current").replace("\\", "/"), f"releases/{SOURCE_B}")
            self.assertEqual(os.readlink(release_root / "previous").replace("\\", "/"), f"releases/{SOURCE_A}")

            result = rollback_release(release_root, "backend")
            self.assertEqual(result["source_sha"], SOURCE_A)
            self.assertEqual(os.readlink(release_root / "current").replace("\\", "/"), f"releases/{SOURCE_A}")

    def test_manifest_itself_is_not_self_hashed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.make_release(Path(directory), "staging", SOURCE_A)
            manifest = json.loads((root / MANIFEST_NAME).read_text(encoding="utf-8"))
            self.assertNotIn(MANIFEST_NAME, {item["path"] for item in manifest["files"]})


if __name__ == "__main__":
    unittest.main()
