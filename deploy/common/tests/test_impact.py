import unittest

from deploy.common.impact import ImpactError, classify


class ImpactTest(unittest.TestCase):

    def test_frontend_change_only(self):
        result = classify(["frontend/src/App.jsx"])
        self.assertTrue(result["frontend"])
        self.assertFalse(result["backend"])

    def test_backend_and_engine_are_one_deployment_unit(self):
        for path in ("backend/code/pom.xml", "engine/Dockerfile", "deploy/backend/data/compose.yaml"):
            with self.subTest(path=path):
                result = classify([path])
                self.assertFalse(result["frontend"])
                self.assertTrue(result["backend"])

    def test_common_and_infrastructure_changes_are_fail_safe(self):
        result = classify([
            "deploy/common/releasectl.py",
            "deploy/host/provision.sh",
            "infra/oci/network/main.tf",
        ])
        self.assertTrue(result["frontend"])
        self.assertTrue(result["backend"])

    def test_documentation_does_not_deploy(self):
        result = classify(["README.md", "doc/10-network-infrastructure.md", "roadmap/README.md"])
        self.assertFalse(result["frontend"])
        self.assertFalse(result["backend"])
        self.assertTrue(result["documentation_only"])

    def test_unknown_change_deploys_both_and_is_reported(self):
        result = classify(["new-runtime-contract.json"])
        self.assertTrue(result["frontend"])
        self.assertTrue(result["backend"])
        self.assertEqual(result["unknown"], ["new-runtime-contract.json"])

    def test_parent_traversal_is_rejected(self):
        with self.assertRaises(ImpactError):
            classify(["../outside"])


if __name__ == "__main__":
    unittest.main()
