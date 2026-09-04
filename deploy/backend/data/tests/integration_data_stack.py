"""Destructive only to uniquely named temporary Compose projects and directories."""

import hashlib
import json
import os
import secrets
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

DATA_ROOT = Path(__file__).resolve().parents[1]
DATACTL = DATA_ROOT / "datactl.py"


def run(command, *, env=None, input_text=None, expect=0):
    completed = subprocess.run(
        command, env=env, input=input_text, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    if completed.returncode != expect:
        raise AssertionError(
            f"Step failed with code {completed.returncode}: {command[:3]}: {completed.stderr[-500:].strip()}"
        )
    return completed.stdout.strip()


def control(python, arguments, *, env=None):
    return json.loads(run([python, str(DATACTL), *arguments], env=env))


def compose(project, secret_dir, ports, *arguments, purpose="service", expect=0):
    env = os.environ.copy()
    env.update({
        "DATA_PROJECT_NAME": project,
        "DATA_SECRETS_DIR": str(secret_dir),
        "DATA_PURPOSE": purpose,
        "MYSQL_HOST_PORT": str(ports[0]),
        "REDIS_HOST_PORT": str(ports[1]),
    })
    return run(
        ["docker", "compose", "-f", str(DATA_ROOT / "compose.yaml"), "-p", project, *arguments],
        env=env, expect=expect,
    )


def main():
    python = sys.executable
    suffix = secrets.token_hex(4)
    source_project = f"cca-data-it-{suffix}"
    restore_project = f"cca-restore-it-{suffix}"
    ports = (0, 0)
    with tempfile.TemporaryDirectory(prefix="cca-data-it-") as temporary:
        temporary = Path(temporary)
        source_secrets = temporary / "source-secrets"
        restore_secrets = temporary / "restore-secrets"
        cipher = temporary / "snapshot.sql.age"
        identity = temporary / "age-identity.txt"
        control(python, ["init-secrets", "--directory", str(source_secrets)])
        control(python, ["init-secrets", "--directory", str(restore_secrets)])
        control(python, [
            "validate-configuration", "--project", source_project,
            "--secrets", str(source_secrets), "--mysql-port", "0", "--redis-port", "0",
        ])
        try:
            compose(source_project, source_secrets, ports, "up", "-d", "--wait")
            mysql_prefix = [
                "exec", "-T", "mysql", "sh", "/usr/local/bin/cca-mysql-client",
                "mysql", "migration", "code_arena",
            ]
            compose(
                source_project, source_secrets, ports, *mysql_prefix,
                "--execute", "CREATE TABLE backup_contract (id BIGINT PRIMARY KEY, payload VARCHAR(64)); INSERT INTO backup_contract VALUES (1, 'synthetic-sensitive-value')",
            )
            compose(
                source_project, source_secrets, ports, "exec", "-T", "mysql", "sh", "/usr/local/bin/cca-mysql-client",
                "mysql", "app", "code_arena",
                "--execute", "INSERT INTO backup_contract VALUES (2, 'application-write')",
            )
            compose(
                source_project, source_secrets, ports, "exec", "-T", "mysql", "sh", "/usr/local/bin/cca-mysql-client",
                "mysql", "app", "code_arena",
                "--execute", "CREATE TABLE forbidden_ddl (id INT)", expect=1,
            )
            compose(
                source_project, source_secrets, ports, "exec", "-T", "mysql", "sh", "/usr/local/bin/cca-mysql-client",
                "mysql", "backup", "code_arena",
                "--execute", "INSERT INTO backup_contract VALUES (3, 'forbidden')", expect=1,
            )
            compose(
                source_project, source_secrets, ports, "exec", "-T", "redis", "sh", "/usr/local/bin/cca-redis-cli",
                "SET", "match_room:integration", "ok",
            )
            forbidden_key = compose(
                source_project, source_secrets, ports, "exec", "-T", "redis", "sh", "/usr/local/bin/cca-redis-cli",
                "SET", "forbidden:key", "no",
            )
            if "NOPERM" not in forbidden_key:
                raise AssertionError("Redis application account escaped its key prefix allowlist.")
            forbidden_admin = compose(
                source_project, source_secrets, ports, "exec", "-T", "redis", "sh", "/usr/local/bin/cca-redis-cli",
                "CONFIG", "GET", "*",
            )
            if "NOPERM" not in forbidden_admin and "unknown command" not in forbidden_admin.lower():
                raise AssertionError("Redis administrative command was not denied.")
            run(["age-keygen", "--output", str(identity)])
            recipient = run(["age-keygen", "-y", str(identity)])
            backup_report = control(python, [
                "backup", "--project", source_project, "--secrets", str(source_secrets),
                "--mysql-port", "0", "--redis-port", "0",
                "--recipient", recipient, "--output", str(cipher),
            ])
            if b"synthetic-sensitive-value" in cipher.read_bytes():
                raise AssertionError("Encrypted artifact exposed plaintext.")
            restore_report = control(python, [
                "restore", "--project", restore_project, "--secrets", str(restore_secrets),
                "--mysql-port", "0", "--redis-port", "0", "--file", str(cipher),
                "--identity", str(identity), "--sha256", backup_report["sha256"],
            ])
            restored = compose(
                restore_project, restore_secrets, ports, "exec", "-T", "mysql", "sh", "/usr/local/bin/cca-mysql-client",
                "mysql", "backup", "code_arena",
                "--batch", "--skip-column-names", "--execute",
                "SELECT GROUP_CONCAT(payload ORDER BY id) FROM backup_contract", purpose="restore",
            )
            if restored != "synthetic-sensitive-value,application-write":
                raise AssertionError("Restored rows did not match the source snapshot.")
            compose(restore_project, restore_secrets, ports, "up", "-d", "--wait", "redis", purpose="restore")
            # DBSIZE is intentionally not in the application ACL. A fresh allowed key must be absent.
            absent = compose(
                restore_project, restore_secrets, ports, "exec", "-T", "redis", "sh", "/usr/local/bin/cca-redis-cli",
                "EXISTS", "match_room:integration", purpose="restore",
            )
            if absent != "0" or restore_report["redis_recovery"] != "empty-restart":
                raise AssertionError("Redis restored stale transient state.")
            print("DATA-03 disposable MySQL/Redis backup and restore contract: PASS")
        finally:
            for project, directory, purpose in (
                (restore_project, restore_secrets, "restore"),
                (source_project, source_secrets, "service"),
            ):
                try:
                    compose(project, directory, ports, "down", "--volumes", "--remove-orphans", purpose=purpose)
                except Exception:
                    pass


if __name__ == "__main__":
    main()
