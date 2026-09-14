import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from deploy.common.oci_deploy import (
    OciDeployError,
    parse_argument,
    publish_artifact,
    run_deployment,
)

REPOSITORY_ID = "ocid1.artifactrepository.oc1.ap-tokyo-1.example"
PIPELINE_ID = "ocid1.devopsdeploypipeline.oc1.ap-tokyo-1.example"
DEPLOYMENT_ID = "ocid1.devopsdeployment.oc1.ap-tokyo-1.example"
VERSION = "a" * 40


class FakeClient:
    def __init__(self, remote: bytes | None = None, states: list[str] | None = None):
        self.remote = remote
        self.states = list(states or [])
        self.calls: list[list[str]] = []

    def run(self, arguments, *, raw=False):
        self.calls.append(arguments)
        if "upload-by-path" in arguments:
            source = Path(arguments[arguments.index("--content-body") + 1])
            if self.remote is None:
                self.remote = source.read_bytes()
                return subprocess.CompletedProcess(arguments, 0, "{}", "")
            return subprocess.CompletedProcess(arguments, 1, "", "immutable")
        if "download-by-path" in arguments:
            if self.remote is None:
                return subprocess.CompletedProcess(arguments, 1, "", "missing")
            destination = Path(arguments[arguments.index("--file") + 1])
            destination.write_bytes(self.remote)
            return subprocess.CompletedProcess(arguments, 0, "{}", "")
        if "create-pipeline-deployment" in arguments:
            return subprocess.CompletedProcess(arguments, 0, DEPLOYMENT_ID, "")
        if arguments[:3] == ["devops", "deployment", "get"]:
            return subprocess.CompletedProcess(arguments, 0, self.states.pop(0), "")
        raise AssertionError(arguments)


class OciDeployTest(unittest.TestCase):

    def test_publish_uploads_and_verifies_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "artifact.tar.gz"
            source.write_bytes(b"artifact")
            result = publish_artifact(FakeClient(), REPOSITORY_ID, "code-clash-arena/backend", VERSION, source)
            self.assertEqual(result["result"], "uploaded")

    def test_immutable_retry_reuses_only_identical_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "artifact.tar.gz"
            source.write_bytes(b"artifact")
            result = publish_artifact(
                FakeClient(remote=b"artifact"), REPOSITORY_ID, "code-clash-arena/backend", VERSION, source
            )
            self.assertEqual(result["result"], "reused")

    def test_immutable_retry_rejects_different_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "artifact.tar.gz"
            source.write_bytes(b"artifact")
            with self.assertRaises(OciDeployError):
                publish_artifact(
                    FakeClient(remote=b"different"), REPOSITORY_ID, "code-clash-arena/backend", VERSION, source
                )

    def test_secret_named_deployment_argument_is_rejected(self):
        with self.assertRaises(OciDeployError):
            parse_argument("registrySecret=value")

    def test_deployment_waits_for_success_and_writes_receipt(self):
        with tempfile.TemporaryDirectory() as directory, patch("deploy.common.oci_deploy.time.sleep"):
            receipt = Path(directory) / "receipt.json"
            result = run_deployment(
                FakeClient(states=["IN_PROGRESS", "SUCCEEDED"]),
                PIPELINE_ID,
                [("sourceSha", VERSION)],
                timeout_seconds=60,
                poll_seconds=5,
                receipt=receipt,
            )
            self.assertEqual(result["state"], "SUCCEEDED")
            self.assertIn(DEPLOYMENT_ID, receipt.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
