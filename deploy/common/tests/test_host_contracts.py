import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


class HostDeploymentContractTest(unittest.TestCase):
    def read(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_edge_proxy_verifies_private_tls_and_does_not_expose_management(self):
        config = self.read("deploy/frontend/nginx/code-clash-arena.conf")
        self.assertGreaterEqual(config.count("__CCA_PUBLIC_HOSTNAME__"), 2)
        self.assertIn("if ($host != __CCA_PUBLIC_HOSTNAME__) { return 444; }", config)
        self.assertIn("listen 443 ssl http2;", config)
        self.assertIn("listen [::]:443 ssl http2;", config)
        self.assertNotIn("http2 on;", config)
        self.assertIn("proxy_ssl_verify on;", config)
        self.assertIn("proxy_ssl_name app.cca.internal;", config)
        self.assertRegex(config, r"location ~ \^/actuator[\s\S]*?return 404;")
        self.assertNotIn("127.0.0.1:8081", config)

    def test_application_ingress_and_data_ports_have_no_public_proxy(self):
        config = self.read("deploy/backend/application/nginx/code-clash-arena-application.conf")
        self.assertIn("listen 8443 ssl;", config)
        self.assertIn("proxy_pass http://127.0.0.1:8080;", config)
        self.assertNotRegex(config, r"3306|6379|8081")

    def test_backend_service_is_loopback_prod_and_uses_release_identity(self):
        service = self.read("deploy/backend/application/systemd/code-clash-arena-backend.service")
        for required in (
            "Environment=SPRING_PROFILES_ACTIVE=prod",
            "Environment=SERVER_ADDRESS=127.0.0.1",
            "Environment=MANAGEMENT_PORT=8081",
            "EnvironmentFile=/opt/code-clash-arena/backend/current/runtime.env",
            "NoNewPrivileges=true",
        ):
            self.assertIn(required, service)

    def test_specs_pin_source_sha_and_have_failure_rollback(self):
        for relative in (
            "deploy/frontend/oci-deployment.yaml",
            "deploy/backend/application/oci-deployment.yaml",
        ):
            with self.subTest(relative=relative):
                spec = self.read(relative)
                self.assertIn("SOURCE_SHA: ${sourceSha}", spec)
                self.assertIn("onFailure:", spec)
                self.assertIn("install.sh rollback", spec)
                self.assertNotIn("NOPASSWD:ALL", spec)

    def test_backend_migration_and_backup_fail_closed(self):
        migration = self.read("deploy/backend/application/migrate.sh")
        backup = self.read("deploy/backend/application/backup.sh")
        self.assertIn("@sha256:", migration)
        self.assertIn("sslMode=REQUIRED", migration)
        self.assertIn("mysql-migration-password", migration)
        self.assertIn('"$PYTHON_BIN" "$DATACTL" backup', backup)
        self.assertIn('"$PYTHON_BIN" "$DATACTL" upload', backup)
        self.assertIn("ocid1\\.devopsdeployment", backup)
        self.assertNotIn("--password ", migration)

    def test_host_profile_requires_oracle_linux_9_amd64_and_python_311(self):
        provision = self.read("deploy/host/provision.sh")
        for required in (
            '[[ "$(uname -m)" == "x86_64" ]]',
            '"$OPERATING_SYSTEM_MAJOR" == "9"',
            "/usr/bin/python3.11",
            "sys.version_info[:2] != (3, 11)",
            "openssl x509",
            "CCA_PUBLIC_ORIGIN",
        ):
            self.assertIn(required, provision)
        self.assertIn("systemctl enable nginx.service >/dev/null", provision)
        self.assertIn("systemctl enable docker.service >/dev/null", provision)
        self.assertNotIn("systemctl enable docker.service nginx.service", provision)

    def test_embedded_python_in_host_scripts_compiles(self):
        for relative in (
            "deploy/frontend/install.sh",
            "deploy/backend/application/install.sh",
            "deploy/backend/application/backup.sh",
            "deploy/host/provision.sh",
        ):
            script = self.read(relative)
            blocks = re.findall(r"<<'PY'\n(.*?)\nPY", script, flags=re.DOTALL)
            self.assertTrue(blocks, relative)
            for index, block in enumerate(blocks):
                with self.subTest(relative=relative, block=index):
                    compile(block, f"{relative}:{index}", "exec")

    def test_deployment_specs_only_use_supported_version(self):
        for path in (
            ROOT / "deploy/frontend/oci-deployment.yaml",
            ROOT / "deploy/frontend/oci-rollback.yaml",
            ROOT / "deploy/backend/application/oci-deployment.yaml",
            ROOT / "deploy/backend/application/oci-rollback.yaml",
        ):
            first_lines = path.read_text(encoding="utf-8").splitlines()[:2]
            self.assertEqual(first_lines, ["version: 1.0", "component: deployment"])

    def test_external_actions_are_commit_pinned(self):
        for workflow in (ROOT / ".github" / "workflows").glob("*.yml"):
            for line in workflow.read_text(encoding="utf-8").splitlines():
                match = re.search(r"\buses:\s*([^\s#]+)", line)
                if not match or match.group(1).startswith("./"):
                    continue
                with self.subTest(workflow=workflow.name, action=match.group(1)):
                    self.assertRegex(match.group(1), r"^[^@\s]+@[0-9a-f]{40}$")


if __name__ == "__main__":
    unittest.main()
