import importlib.util
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

MODULE_PATH = Path(__file__).resolve().parents[1] / "verify.py"
SPEC = importlib.util.spec_from_file_location("frontend_release_verify", MODULE_PATH)
verify_module = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(verify_module)


class FrontendVerificationTest(unittest.TestCase):
    def test_rejects_non_https_and_non_origin_values(self):
        for value in (
            "http://example.com",
            "https://203.0.113.10",
            "https://localhost",
            "https://bad_.example.com",
            "https://example.com:443",
            "https://user@example.com",
            "https://example.com/path",
            "https://example.com?query=1",
        ):
            with self.subTest(value=value), self.assertRaises(verify_module.VerificationError):
                verify_module.validate_origin(value)

    def test_requires_document_and_proxied_api_contract(self):
        opener = Mock()
        with patch.object(verify_module, "build_opener", return_value=opener), patch.object(
            verify_module,
            "request",
            side_effect=[
                (200, {}, b'<div id="root"></div>'),
                (401, {"X-Correlation-ID": "request-id"}, b"{}"),
            ],
        ):
            verify_module.verify("https://arena.example")

    def test_fails_when_api_does_not_cross_proxy(self):
        with patch.object(verify_module, "build_opener", return_value=Mock()), patch.object(
            verify_module,
            "request",
            side_effect=[
                (200, {}, b'<div id="root"></div>'),
                (404, {}, b""),
            ],
        ), self.assertRaises(verify_module.VerificationError):
            verify_module.verify("https://arena.example")


if __name__ == "__main__":
    unittest.main()
