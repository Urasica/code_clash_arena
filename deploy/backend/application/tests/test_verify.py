import importlib.util
import json
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

MODULE_PATH = Path(__file__).resolve().parents[1] / "verify.py"
SPEC = importlib.util.spec_from_file_location("backend_release_verify", MODULE_PATH)
verify_module = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(verify_module)

ENGINE = "registry.example/engine@sha256:" + "a" * 64


class BackendVerificationTest(unittest.TestCase):
    def readiness(self, engine_status="UP"):
        payload = {
            "status": "UP",
            "components": {
                "db": {"status": "UP"},
                "redis": {"status": "UP"},
                "engineImage": {"status": engine_status},
            },
        }
        return 200, {}, json.dumps(payload).encode()

    @patch.object(verify_module.subprocess, "run")
    @patch.object(verify_module, "fetch")
    def test_requires_up_dependencies_api_contract_and_native_amd64(self, fetch, run):
        fetch.side_effect = [self.readiness(), (401, {"X-Correlation-ID": "request-id"}, b"{}")]
        run.return_value = SimpleNamespace(returncode=0, stdout="linux/amd64\n")

        verify_module.verify(ENGINE)

    def test_rejects_mutable_engine_tag(self):
        with self.assertRaises(verify_module.VerificationError):
            verify_module.verify("registry.example/engine:latest")

    @patch.object(verify_module.subprocess, "run")
    @patch.object(verify_module, "fetch")
    def test_rejects_degraded_readiness(self, fetch, run):
        fetch.side_effect = [self.readiness("DOWN")]
        run.return_value = SimpleNamespace(returncode=0, stdout="linux/amd64\n")
        with self.assertRaises(verify_module.VerificationError):
            verify_module.verify(ENGINE)

    @patch.object(verify_module.subprocess, "run")
    @patch.object(verify_module, "fetch")
    def test_rejects_non_amd64_engine(self, fetch, run):
        fetch.side_effect = [self.readiness(), (401, {"X-Correlation-ID": "request-id"}, b"{}")]
        run.return_value = SimpleNamespace(returncode=0, stdout="linux/arm64\n")
        with self.assertRaises(verify_module.VerificationError):
            verify_module.verify(ENGINE)


if __name__ == "__main__":
    unittest.main()
