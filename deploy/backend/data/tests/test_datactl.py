import argparse
import contextlib
import io
import json
import os
import stat
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import sys

DATA_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(DATA_ROOT))
import datactl  # noqa: E402


class DataControlTest(unittest.TestCase):
    def test_compose_failure_reports_only_safe_container_state(self):
        failed = mock.Mock(
            returncode=1,
            stdout=b"",
            stderr=b"MYSQL_PASSWORD=must-not-appear",
        )
        status = mock.Mock(
            returncode=0,
            stdout=json.dumps([{
                "Name": "cca-restore-test-mysql-1",
                "Service": "mysql",
                "State": "exited",
                "Health": "",
                "ExitCode": 137,
                "Publishers": [{"URL": "127.0.0.1"}],
            }]).encode(),
            stderr=b"",
        )
        arguments = argparse.Namespace(project="cca-restore-test")
        with mock.patch.object(datactl, "require_binary", return_value="docker"), mock.patch.object(
            datactl, "compose_env", return_value={"SAFE": "value"},
        ), mock.patch.object(datactl.subprocess, "run", side_effect=[failed, status]) as execute:
            with self.assertRaises(datactl.DataOperationError) as raised:
                datactl.compose_command(arguments, "up", "-d")

        message = str(raised.exception)
        self.assertIn('"ExitCode":137', message)
        self.assertIn('"Service":"mysql"', message)
        self.assertNotIn("must-not-appear", message)
        self.assertNotIn("Publishers", message)
        self.assertEqual(execute.call_count, 2)

    def test_disposable_restore_destroys_source_volume_first(self):
        integration = (DATA_ROOT / "tests" / "integration_data_stack.py").read_text(encoding="utf-8")
        source_down = (
            'compose(source_project, source_secrets, ports, "down", '
            '"--volumes", "--remove-orphans")'
        )
        restore = "restore_report = control(python, ["
        self.assertIn(source_down, integration)
        self.assertLess(integration.index(source_down), integration.index(restore))

    def test_generates_distinct_external_credentials_and_restricted_acl(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "secrets"
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                datactl.init_secrets(argparse.Namespace(directory=str(target)))

            report = json.loads(output.getvalue())
            self.assertEqual(report["files"], 15)
            values = {
                name: (target / name).read_text(encoding="utf-8").strip()
                for name in datactl.TOKENS
            }
            self.assertEqual(len(set(values.values())), len(values))
            self.assertTrue(all(len(value) == 64 for value in values.values()))
            self.assertEqual((target / "backend" / "spring.datasource.username").read_text().strip(), "cca_app")
            self.assertEqual((target / "backend" / "spring.data.redis.username").read_text().strip(), "cca_app")
            self.assertFalse((target / "spring.datasource.username").exists())
            self.assertEqual(
                {path.name for path in (target / "backend").iterdir()},
                datactl.BACKEND_SECRET_FILES,
            )

            acl = (target / "redis.acl").read_text(encoding="utf-8")
            self.assertIn("user default off", acl)
            self.assertIn("user cca_app on resetpass", acl)
            self.assertIn("~match_room:*", acl)
            self.assertIn("~websocket_session:*", acl)
            self.assertIn("+eval", acl)
            self.assertNotIn("+@all", acl)
            self.assertNotIn("~*", acl)
            self.assertNotIn("+config", acl)
            self.assertNotIn("+acl", acl)
            self.assertFalse(any(value in output.getvalue() for value in values.values()))
            if os.name != "nt":
                self.assertEqual(stat.S_IMODE(target.stat().st_mode), 0o700)
                self.assertEqual(stat.S_IMODE((target / "backend").stat().st_mode), 0o700)
                for child in [path for path in target.iterdir() if path.is_file()] + list((target / "backend").iterdir()):
                    self.assertEqual(stat.S_IMODE(child.stat().st_mode), 0o600)

    def test_refuses_repository_secret_directory_and_overwrite(self):
        with self.assertRaises(datactl.DataOperationError):
            datactl.init_secrets(argparse.Namespace(directory=str(DATA_ROOT / "secrets")))
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "secrets"
            target.mkdir()
            with self.assertRaises(datactl.DataOperationError):
                datactl.init_secrets(argparse.Namespace(directory=str(target)))

    def test_rejects_tampered_secret_bundle(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "secrets"
            with contextlib.redirect_stdout(io.StringIO()):
                datactl.init_secrets(argparse.Namespace(directory=str(target)))
            (target / "backend" / "spring.datasource.username").write_text("cca_migrator\n", encoding="utf-8")
            with self.assertRaises(datactl.DataOperationError):
                datactl.validate_secret_bundle(target)

    def test_redis_entrypoint_stages_secrets_before_dropping_privileges(self):
        entrypoint = (DATA_ROOT / "redis" / "entrypoint.sh").read_text(encoding="utf-8")
        redis_client = (DATA_ROOT / "health" / "redis-cli.sh").read_text(encoding="utf-8")
        compose = (DATA_ROOT / "compose.yaml").read_text(encoding="utf-8")

        acl_copy = 'cp /run/secrets/redis-acl "$runtime_dir/redis.acl"'
        password_copy = 'cp /run/secrets/redis-app-password "$runtime_dir/redis-app-password"'
        privilege_drop = 'exec /usr/local/bin/docker-entrypoint.sh "$@"'
        self.assertIn(acl_copy, entrypoint)
        self.assertIn(password_copy, entrypoint)
        self.assertLess(entrypoint.index(acl_copy), entrypoint.index(privilege_drop))
        self.assertLess(entrypoint.index(password_copy), entrypoint.index(privilege_drop))
        self.assertIn("chown redis:root", entrypoint)
        self.assertIn("/tmp/cca-redis-secrets/redis-app-password", redis_client)
        self.assertNotIn("/run/secrets/redis-app-password", redis_client)
        self.assertIn('"--aclfile", "/tmp/cca-redis-secrets/redis.acl"', compose)

    def test_mysql_entrypoint_stages_secrets_before_dropping_privileges(self):
        entrypoint = (DATA_ROOT / "mysql" / "entrypoint.sh").read_text(encoding="utf-8")
        bootstrap = (DATA_ROOT / "mysql" / "bootstrap-users.sh").read_text(encoding="utf-8")
        mysql_client = (DATA_ROOT / "health" / "mysql-client.sh").read_text(encoding="utf-8")
        compose = (DATA_ROOT / "compose.yaml").read_text(encoding="utf-8")

        for name in (
            "mysql-root-password", "mysql-app-password", "mysql-migration-password",
            "mysql-backup-password", "mysql-health-password", "mysql-app-client",
            "mysql-migration-client", "mysql-backup-client", "mysql-health-client",
        ):
            self.assertIn(name, entrypoint)
        self.assertLess(
            entrypoint.index('cp "/run/secrets/$name" "$runtime_dir/$name"'),
            entrypoint.index('exec /usr/local/bin/docker-entrypoint.sh "$@"'),
        )
        self.assertIn("chown mysql:root", entrypoint)
        self.assertIn('/tmp/cca-mysql-secrets/$1', bootstrap)
        self.assertNotIn('/run/secrets/$1', bootstrap)
        self.assertIn('/tmp/cca-mysql-secrets/mysql-${role}-client', mysql_client)
        self.assertNotIn('/run/secrets/mysql-${role}-client', mysql_client)
        self.assertIn(
            "MYSQL_ROOT_PASSWORD_FILE: /tmp/cca-mysql-secrets/mysql-root-password",
            compose,
        )

    def test_upload_rejects_plaintext_wrong_region_and_object_name_before_cli(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "backup.sql"
            source.write_text("synthetic", encoding="utf-8")
            arguments = argparse.Namespace(
                file=str(source), region="ap-tokyo-1",
                compartment_id="ocid1.compartment.oc1..placeholder",
                namespace="namespace", bucket="bucket",
                object_name="code-clash-arena/mysql/bad.sql",
            )
            with mock.patch.object(datactl, "require_binary") as binary:
                with self.assertRaises(datactl.DataOperationError):
                    datactl.upload(arguments)
                binary.assert_not_called()

            cipher = Path(temporary) / "backup.sql.age"
            cipher.write_bytes(b"age-encryption.org/v1\nsynthetic")
            arguments.file = str(cipher)
            arguments.region = "us-ashburn-1"
            with mock.patch.object(datactl, "require_binary") as binary:
                with self.assertRaises(datactl.DataOperationError):
                    datactl.upload(arguments)
                binary.assert_not_called()

    def test_upload_checks_private_bucket_and_uses_supported_object_arguments(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "backup.sql.age"
            source.write_bytes(b"age-encryption.org/v1\nsynthetic")
            compartment = "ocid1.compartment.oc1..placeholder"
            arguments = argparse.Namespace(
                file=str(source), region="ap-tokyo-1", compartment_id=compartment,
                namespace="namespace", bucket="bucket",
                object_name="code-clash-arena/mysql/20260904T000000Z-0123456789abcdef.sql.age",
            )
            bucket = mock.Mock(
                returncode=0,
                stdout=json.dumps({"data": {
                    "compartment-id": compartment,
                    "public-access-type": "NoPublicAccess",
                }}).encode(),
                stderr=b"",
            )
            uploaded = mock.Mock(returncode=0, stdout=b'{"etag":"synthetic-etag"}', stderr=b"")
            output = io.StringIO()
            with mock.patch.object(datactl, "require_binary", return_value="oci"), mock.patch.object(
                datactl.subprocess, "run", side_effect=[bucket, uploaded],
            ) as execute, contextlib.redirect_stdout(output):
                datactl.upload(arguments)

            bucket_command, put_command = execute.call_args_list[0].args[0], execute.call_args_list[1].args[0]
            self.assertIn("bucket", bucket_command)
            self.assertIn("get", bucket_command)
            self.assertIn("--no-overwrite", put_command)
            self.assertIn("--verify-checksum", put_command)
            self.assertNotIn("--compartment-id", put_command)
            self.assertEqual(json.loads(output.getvalue())["compartment_id"], compartment)

            public_bucket = mock.Mock(
                returncode=0,
                stdout=json.dumps({"data": {
                    "compartment-id": compartment,
                    "public-access-type": "ObjectRead",
                }}).encode(),
                stderr=b"",
            )
            with mock.patch.object(datactl, "require_binary", return_value="oci"), mock.patch.object(
                datactl.subprocess, "run", return_value=public_bucket,
            ) as execute:
                with self.assertRaises(datactl.DataOperationError):
                    datactl.upload(arguments)
                execute.assert_called_once()

    def test_download_uses_approved_private_bucket_and_verifies_ciphertext(self):
        with tempfile.TemporaryDirectory() as temporary:
            payload = b"age-encryption.org/v1\nsynthetic"
            output_path = Path(temporary) / "downloaded.sql.age"
            compartment = "ocid1.compartment.oc1..placeholder"
            arguments = argparse.Namespace(
                output=str(output_path), sha256=hashlib_bytes(payload),
                region="ap-tokyo-1", compartment_id=compartment,
                namespace="namespace", bucket="bucket",
                object_name="code-clash-arena/mysql/20260904T000000Z-0123456789abcdef.sql.age",
            )
            bucket = mock.Mock(
                returncode=0,
                stdout=json.dumps({"data": {
                    "compartment-id": compartment,
                    "public-access-type": "NoPublicAccess",
                }}).encode(),
                stderr=b"",
            )

            def execute(command, **_kwargs):
                if "object" not in command:
                    return bucket
                Path(command[command.index("--file") + 1]).write_bytes(payload)
                return mock.Mock(returncode=0, stdout=b"", stderr=b"")

            report = io.StringIO()
            with mock.patch.object(datactl, "require_binary", return_value="oci"), mock.patch.object(
                datactl.subprocess, "run", side_effect=execute,
            ) as invoked, contextlib.redirect_stdout(report):
                datactl.download(arguments)

            get_command = invoked.call_args_list[1].args[0]
            self.assertIn("get", get_command)
            self.assertNotIn("--compartment-id", get_command)
            self.assertEqual(output_path.read_bytes(), payload)
            self.assertEqual(json.loads(report.getvalue())["sha256"], hashlib_bytes(payload))

    def test_restore_requires_dedicated_project_checksum_and_identity(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "backup.sql.age"
            identity = Path(temporary) / "identity.txt"
            source.write_bytes(b"age-encryption.org/v1\nsynthetic")
            identity.write_text("synthetic identity", encoding="utf-8")
            arguments = argparse.Namespace(
                project="cca-data-production", file=str(source), identity=str(identity),
                sha256=hashlib_sha(source), secrets=str(Path(temporary) / "none"),
                mysql_port=0, redis_port=0,
            )
            with mock.patch.object(datactl, "compose_command") as compose:
                with self.assertRaises(datactl.DataOperationError):
                    datactl.restore(arguments)
                compose.assert_not_called()

            arguments.project = "cca-restore-test"
            arguments.sha256 = "0" * 64
            with mock.patch.object(datactl, "compose_command") as compose:
                with self.assertRaises(datactl.DataOperationError):
                    datactl.restore(arguments)
                compose.assert_not_called()


def hashlib_sha(path: Path) -> str:
    import hashlib
    return hashlib.sha256(path.read_bytes()).hexdigest()


def hashlib_bytes(value: bytes) -> str:
    import hashlib
    return hashlib.sha256(value).hexdigest()


if __name__ == "__main__":
    unittest.main()
