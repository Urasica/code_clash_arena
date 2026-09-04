#!/usr/bin/env python3
"""Fail-closed DATA-03 operations without printing secret values."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPOSITORY_ROOT = ROOT.parents[2]
COMPOSE = ROOT / "compose.yaml"
PROJECT_RE = re.compile(r"^cca-(?:data|restore)-[a-z0-9][a-z0-9-]{0,30}$")
OCID_RE = re.compile(r"^ocid1\.compartment\.[a-z0-9-]+\.\.[a-z0-9]+$")
OBJECT_RE = re.compile(r"^code-clash-arena/mysql/[0-9]{8}T[0-9]{6}Z-[0-9a-f]{16}\.sql\.age$")
RECIPIENT_RE = re.compile(r"^age1[023456789acdefghjklmnpqrstuvwxyz]{58}$")
PASSWORD_RE = re.compile(r"^[0-9a-f]{64}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
NAMESPACE_RE = re.compile(r"^[a-zA-Z0-9_-]{1,100}$")
BUCKET_RE = re.compile(r"^[a-zA-Z0-9_.-]{1,256}$")
AGE_HEADER = b"age-encryption.org/v1\n"
TOKENS = {
    "mysql-root-password": None,
    "mysql-app-password": "cca_app",
    "mysql-migration-password": "cca_migrator",
    "mysql-backup-password": "cca_backup",
    "mysql-health-password": "cca_health",
    "redis-app-password": None,
}
BACKEND_SECRET_FILES = {
    "spring.datasource.username", "spring.datasource.password",
    "spring.data.redis.username", "spring.data.redis.password",
}
REDIS_PATTERNS = [
    "match_queue:*", "match_reservation:*", "match_room:*",
    "user_session:*", "socket_game:*", "match_sockets:*",
    "websocket_session:*", "user_sockets:*", "ai_workspace:*", "rate_limit:*",
]
REDIS_COMMANDS = [
    "ping", "hello", "auth", "select", "client|setinfo", "client|setname", "client|id", "info",
    "get", "set", "del", "exists", "expire", "ttl", "incr",
    "hget", "hset", "hgetall", "hlen", "hexists",
    "zadd", "zrange", "zrem", "zcard", "sadd", "srem", "smembers", "scard",
    "eval", "evalsha", "script|load", "publish",
]


class DataOperationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise DataOperationError(message)


def require_binary(name: str) -> str:
    binary = shutil.which(name)
    if not binary:
        fail(f"Required executable is unavailable: {name}")
    return binary


def is_repository_path(path: Path) -> bool:
    return path == REPOSITORY_ROOT or REPOSITORY_ROOT in path.parents


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def has_age_header(path: Path) -> bool:
    with path.open("rb") as stream:
        return stream.read(len(AGE_HEADER)) == AGE_HEADER


def mysql_client_content(user: str, password: str) -> str:
    return (
        "[client]\n"
        f"user={user}\npassword={password}\n"
        "protocol=TCP\nhost=127.0.0.1\nssl-mode=REQUIRED\n"
    )


def redis_acl_content(password: str) -> str:
    acl = "user default off\nuser cca_app on resetpass >" + password
    acl += " " + " ".join("~" + pattern for pattern in REDIS_PATTERNS)
    acl += " &* " + " ".join("+" + command for command in REDIS_COMMANDS) + "\n"
    return acl


def secret_dir(path: str) -> Path:
    directory = Path(path).resolve()
    if is_repository_path(directory):
        fail("The secret directory must stay outside the repository.")
    return directory


def validate_secret_bundle(directory: Path) -> None:
    expected_names = set(TOKENS) | {
        "mysql-app.cnf", "mysql-migration.cnf", "mysql-backup.cnf", "mysql-health.cnf", "redis.acl",
    }
    backend_directory = directory / "backend"
    if not directory.is_dir() or backend_directory.is_symlink() or not backend_directory.is_dir():
        fail("The secret directory is incomplete. Run init-secrets in a new external directory.")
    if os.name != "nt" and any(
        stat.S_IMODE(path.stat().st_mode) & 0o077 for path in (directory, backend_directory)
    ):
        fail("Secret directories must not be accessible by group/other users.")
    paths = [directory / name for name in expected_names]
    paths.extend(backend_directory / name for name in BACKEND_SECRET_FILES)
    for path in paths:
        if path.is_symlink() or not path.is_file():
            fail("The secret directory contains a missing or linked file.")
        if os.name != "nt" and stat.S_IMODE(path.stat().st_mode) & 0o077:
            fail("Secret files must not be readable or writable by group/other users.")
    try:
        values = {name: (directory / name).read_text(encoding="utf-8").strip() for name in TOKENS}
    except (OSError, UnicodeError):
        fail("Credential files must be readable UTF-8 text.")
    if (
        any(not PASSWORD_RE.fullmatch(value) for value in values.values())
        or len(set(values.values())) != len(values)
    ):
        fail("Generated credentials must be distinct 64-character lowercase hex values.")
    if (backend_directory / "spring.datasource.username").read_text(encoding="utf-8") != "cca_app\n":
        fail("Spring datasource username secret is invalid.")
    if (backend_directory / "spring.datasource.password").read_text(encoding="utf-8") != values["mysql-app-password"] + "\n":
        fail("Spring datasource password does not match the MySQL application credential.")
    if (backend_directory / "spring.data.redis.username").read_text(encoding="utf-8") != "cca_app\n":
        fail("Spring Redis username secret is invalid.")
    if (backend_directory / "spring.data.redis.password").read_text(encoding="utf-8") != values["redis-app-password"] + "\n":
        fail("Spring Redis password does not match the Redis application credential.")
    for name, user in TOKENS.items():
        if user and (directory / name.replace("-password", ".cnf")).read_text(encoding="utf-8") != mysql_client_content(user, values[name]):
            fail("A MySQL role client file does not match its generated credential.")
    if (directory / "redis.acl").read_text(encoding="utf-8") != redis_acl_content(values["redis-app-password"]):
        fail("Redis ACL does not match the approved application key/command policy.")


def write_private(path: Path, content: str) -> None:
    if path.exists():
        fail(f"Refusing to overwrite existing secret file: {path.name}")
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
        stream.write(content)
    if os.name != "nt":
        path.chmod(stat.S_IRUSR | stat.S_IWUSR)


def init_secrets(args: argparse.Namespace) -> None:
    directory = secret_dir(args.directory)
    try:
        directory.mkdir(mode=0o700, parents=True, exist_ok=False)
    except FileExistsError:
        fail("Refusing to reuse an existing secret directory.")
    generated = {name: secrets.token_hex(32) for name in TOKENS}
    try:
        backend_directory = directory / "backend"
        backend_directory.mkdir(mode=0o700)
        for name, value in generated.items():
            write_private(directory / name, value + "\n")
        write_private(backend_directory / "spring.datasource.username", "cca_app\n")
        write_private(backend_directory / "spring.datasource.password", generated["mysql-app-password"] + "\n")
        write_private(backend_directory / "spring.data.redis.username", "cca_app\n")
        write_private(backend_directory / "spring.data.redis.password", generated["redis-app-password"] + "\n")
        for name, user in TOKENS.items():
            if not user:
                continue
            write_private(
                directory / name.replace("-password", ".cnf"),
                mysql_client_content(user, generated[name]),
            )
        write_private(directory / "redis.acl", redis_acl_content(generated["redis-app-password"]))
    except Exception:
        for child in sorted(directory.rglob("*"), key=lambda path: len(path.parts), reverse=True):
            if child.is_dir() and not child.is_symlink():
                child.rmdir()
            else:
                child.unlink(missing_ok=True)
        directory.rmdir()
        raise
    finally:
        for key in list(generated):
            generated[key] = ""
    print(json.dumps({"result": "created", "directory": str(directory), "files": 15}))


def compose_env(args: argparse.Namespace, purpose: str = "service") -> dict[str, str]:
    if not PROJECT_RE.fullmatch(args.project):
        fail("Project name must be an explicit cca-data-* or cca-restore-* identifier.")
    directory = secret_dir(args.secrets)
    validate_secret_bundle(directory)
    env = os.environ.copy()
    env.update({
        "DATA_PROJECT_NAME": args.project,
        "DATA_SECRETS_DIR": str(directory),
        "DATA_PURPOSE": purpose,
        "MYSQL_HOST_PORT": str(args.mysql_port),
        "REDIS_HOST_PORT": str(args.redis_port),
    })
    return env


def compose_command(args: argparse.Namespace, *tail: str, purpose: str = "service", input_file=None):
    command = [require_binary("docker"), "compose", "-f", str(COMPOSE), "-p", args.project, *tail]
    completed = subprocess.run(
        command, env=compose_env(args, purpose), stdin=input_file,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    if completed.returncode:
        fail(f"Docker data operation failed at step: {' '.join(tail[:2])}")
    return completed.stdout


def validate_configuration(args: argparse.Namespace, *, emit: bool = True) -> None:
    rendered = json.loads(compose_command(args, "config", "--format", "json"))
    services = rendered.get("services", {})
    if set(services) != {"mysql", "redis"}:
        fail("Only MySQL and Redis are allowed in the data stack.")
    network = next(iter(rendered.get("networks", {}).values()), {})
    if not network.get("internal") or network.get("attachable"):
        fail("The data network must be internal and non-attachable.")
    for service, target in (("mysql", 3306), ("redis", 6379)):
        ports = services[service].get("ports", [])
        if len(ports) != 1 or ports[0].get("host_ip") != "127.0.0.1" or ports[0].get("target") != target:
            fail(f"{service} must publish exactly one loopback-only port.")
        if set(services[service].get("networks", {})) != set(rendered["networks"]):
            fail(f"{service} must only join the internal data network.")
    redis_volumes = services["redis"].get("volumes", [])
    if services["redis"].get("read_only") is not True or any(
        volume.get("type") == "volume" for volume in redis_volumes
    ):
        fail("Redis must remain ephemeral with a read-only root filesystem and no volume.")
    if emit:
        print(json.dumps({"result": "valid", "project": args.project, "redis_recovery": "empty-restart"}))


def start(args: argparse.Namespace) -> None:
    validate_configuration(args, emit=False)
    compose_command(args, "up", "-d", "--wait")
    print(json.dumps({"result": "started", "project": args.project, "published_host": "127.0.0.1"}))


def stop(args: argparse.Namespace) -> None:
    compose_command(args, "stop")
    print(json.dumps({"result": "stopped", "project": args.project, "volumes_removed": False}))


def backup(args: argparse.Namespace) -> None:
    if not RECIPIENT_RE.fullmatch(args.recipient):
        fail("A single valid age X25519 recipient is required.")
    output = Path(args.output).resolve()
    if is_repository_path(output):
        fail("Backup artifacts must stay outside the repository.")
    if output.suffixes[-2:] != [".sql", ".age"] or output.exists():
        fail("Backup output must be a new .sql.age file.")
    output.parent.mkdir(parents=True, exist_ok=True)
    dump = [
        require_binary("docker"), "compose", "-f", str(COMPOSE), "-p", args.project,
        "exec", "-T", "mysql", "sh", "/usr/local/bin/cca-mysql-client", "mysqldump", "backup",
        "--single-transaction", "--quick", "--no-tablespaces", "--set-gtid-purged=OFF",
        "--skip-lock-tables", "--skip-add-locks", "--skip-add-drop-table", "--hex-blob", "code_arena",
    ]
    age = [require_binary("age"), "--encrypt", "--recipient", args.recipient, "--output", str(output)]
    dump_process = subprocess.Popen(dump, env=compose_env(args), stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    age_process = subprocess.Popen(age, stdin=dump_process.stdout, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    assert dump_process.stdout is not None
    dump_process.stdout.close()
    age_process.wait()
    dump_process.wait()
    if dump_process.returncode or age_process.returncode:
        output.unlink(missing_ok=True)
        fail(
            "Encrypted MySQL backup pipeline failed; no usable artifact was retained "
            f"(dump={dump_process.returncode}, encrypt={age_process.returncode})."
        )
    if os.name != "nt":
        output.chmod(stat.S_IRUSR | stat.S_IWUSR)
    if not has_age_header(output):
        output.unlink(missing_ok=True)
        fail("Backup artifact is not an age-encrypted file.")
    digest = sha256_file(output)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    object_name = f"code-clash-arena/mysql/{timestamp}-{secrets.token_hex(8)}.sql.age"
    print(json.dumps({"result": "encrypted", "file": str(output), "sha256": digest, "object_name": object_name}))


def approved_oci_location(args: argparse.Namespace) -> list[str]:
    if (
        args.region != "ap-tokyo-1"
        or not OCID_RE.fullmatch(args.compartment_id)
        or not NAMESPACE_RE.fullmatch(args.namespace)
        or not BUCKET_RE.fullmatch(args.bucket)
        or not OBJECT_RE.fullmatch(args.object_name)
    ):
        fail("Use the approved Tokyo region, compartment, namespace, bucket, and generated object name.")
    oci = require_binary("oci")
    common = [
        oci, "--auth", "instance_principal", "--region", args.region, "--output", "json",
        "os",
    ]
    bucket_result = subprocess.run(
        [*common, "bucket", "get", "--namespace-name", args.namespace, "--bucket-name", args.bucket],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    try:
        bucket_response = json.loads(bucket_result.stdout)
        bucket = bucket_response.get("data", {}) if isinstance(bucket_response, dict) else {}
    except (json.JSONDecodeError, UnicodeDecodeError):
        bucket = {}
    if (
        bucket_result.returncode
        or bucket.get("compartment-id") != args.compartment_id
        or bucket.get("public-access-type") != "NoPublicAccess"
    ):
        fail("Backup bucket lookup failed or the bucket is public/outside the approved compartment.")
    return common


def upload(args: argparse.Namespace) -> None:
    source = Path(args.file).resolve()
    if (
        is_repository_path(source)
        or source.suffixes[-2:] != [".sql", ".age"]
        or not source.is_file()
        or source.stat().st_size == 0
        or not has_age_header(source)
    ):
        fail("Upload source must be a non-empty age-encrypted .sql.age file outside the repository.")
    digest = sha256_file(source)
    common = approved_oci_location(args)
    command = [
        *common, "object", "put",
        "--namespace-name", args.namespace, "--bucket-name", args.bucket,
        "--name", args.object_name, "--file", str(source), "--no-overwrite",
        "--verify-checksum",
        "--metadata", json.dumps({"sha256": digest}, separators=(",", ":")),
    ]
    completed = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    try:
        receipt = json.loads(completed.stdout)
    except (json.JSONDecodeError, UnicodeDecodeError):
        receipt = {}
    if completed.returncode or not isinstance(receipt, dict) or not receipt.get("etag"):
        fail("OCI Object Storage upload failed; verify instance-principal IAM and object uniqueness.")
    print(json.dumps({
        "result": "uploaded", "region": args.region, "compartment_id": args.compartment_id,
        "bucket": args.bucket, "object_name": args.object_name, "sha256": digest,
    }))


def download(args: argparse.Namespace) -> None:
    output = Path(args.output).resolve()
    if is_repository_path(output) or output.suffixes[-2:] != [".sql", ".age"] or output.exists():
        fail("Download output must be a new .sql.age file outside the repository.")
    if not SHA256_RE.fullmatch(args.sha256):
        fail("A recorded lowercase SHA-256 is required before download.")
    common = approved_oci_location(args)
    output.parent.mkdir(parents=True, exist_ok=True)
    completed = subprocess.run(
        [
            *common, "object", "get",
            "--namespace-name", args.namespace, "--bucket-name", args.bucket,
            "--name", args.object_name, "--file", str(output),
        ],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=False,
    )
    if (
        completed.returncode
        or not output.is_file()
        or output.stat().st_size == 0
        or not has_age_header(output)
        or sha256_file(output) != args.sha256
    ):
        output.unlink(missing_ok=True)
        fail("OCI Object Storage download or recorded SHA-256 verification failed.")
    if os.name != "nt":
        output.chmod(stat.S_IRUSR | stat.S_IWUSR)
    print(json.dumps({
        "result": "downloaded", "region": args.region, "compartment_id": args.compartment_id,
        "bucket": args.bucket, "object_name": args.object_name,
        "file": str(output), "sha256": args.sha256,
    }))


def restore(args: argparse.Namespace) -> None:
    if not args.project.startswith("cca-restore-"):
        fail("Restore is allowed only in a dedicated cca-restore-* project.")
    source = Path(args.file).resolve()
    identity = Path(args.identity).resolve()
    if is_repository_path(source) or is_repository_path(identity):
        fail("The encrypted backup and age identity must stay outside the repository.")
    if (
        not source.is_file() or not has_age_header(source)
        or not identity.is_file() or not SHA256_RE.fullmatch(args.sha256)
        or sha256_file(source) != args.sha256
    ):
        fail("Ciphertext SHA-256 or restore identity input is invalid.")
    if os.name != "nt" and stat.S_IMODE(identity.stat().st_mode) & 0o077:
        fail("The age identity must not be accessible by group/other users.")
    compose_command(args, "up", "-d", "--wait", "mysql", purpose="restore")
    count = compose_command(
        args, "exec", "-T", "mysql", "sh", "/usr/local/bin/cca-mysql-client", "mysql", "migration",
        "--batch", "--skip-column-names", "--execute",
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='code_arena'", purpose="restore",
    ).decode().strip()
    if count != "0":
        fail("Restore target is not an empty dedicated schema; refusing to import.")
    descriptor, plaintext_name = tempfile.mkstemp(prefix="cca-restore-", suffix=".sql")
    os.close(descriptor)
    plaintext = Path(plaintext_name)
    try:
        plaintext.chmod(stat.S_IRUSR | stat.S_IWUSR)
        completed = subprocess.run(
            [require_binary("age"), "--decrypt", "--identity", str(identity), "--output", str(plaintext), str(source)],
            stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=False,
        )
        if completed.returncode or plaintext.stat().st_size == 0:
            fail("Backup decryption failed before the database was modified.")
        with plaintext.open("rb") as sql:
            compose_command(
                args, "exec", "-T", "mysql", "sh", "/usr/local/bin/cca-mysql-client", "mysql", "migration",
                "--binary-mode=1", "code_arena",
                purpose="restore", input_file=sql,
            )
        restored = compose_command(
            args, "exec", "-T", "mysql", "sh", "/usr/local/bin/cca-mysql-client", "mysql", "backup",
            "--batch", "--skip-column-names", "--execute",
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='code_arena'", purpose="restore",
        ).decode().strip()
        if not restored.isdigit() or int(restored) < 1:
            fail("Restore completed without any application tables.")
        print(json.dumps({"result": "restored", "project": args.project, "tables": int(restored), "redis_recovery": "empty-restart"}))
    finally:
        try:
            if plaintext.exists():
                with plaintext.open("r+b") as stream:
                    stream.write(b"\0" * min(plaintext.stat().st_size, 1024 * 1024))
                    stream.flush()
                    os.fsync(stream.fileno())
                plaintext.unlink()
        except OSError:
            print("WARNING: remove the restore temporary file from the system temp directory.", file=sys.stderr)


def add_runtime_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--project", required=True)
    parser.add_argument("--secrets", required=True)
    parser.add_argument("--mysql-port", type=int, default=3306)
    parser.add_argument("--redis-port", type=int, default=6379)


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)
    init = commands.add_parser("init-secrets")
    init.add_argument("--directory", required=True)
    init.set_defaults(handler=init_secrets)
    validate = commands.add_parser("validate-configuration")
    add_runtime_arguments(validate)
    validate.set_defaults(handler=validate_configuration)
    launch = commands.add_parser("start")
    add_runtime_arguments(launch)
    launch.set_defaults(handler=start)
    halt = commands.add_parser("stop")
    add_runtime_arguments(halt)
    halt.set_defaults(handler=stop)
    create = commands.add_parser("backup")
    add_runtime_arguments(create)
    create.add_argument("--recipient", required=True)
    create.add_argument("--output", required=True)
    create.set_defaults(handler=backup)
    put = commands.add_parser("upload")
    put.add_argument("--file", required=True)
    put.add_argument("--region", required=True)
    put.add_argument("--compartment-id", required=True)
    put.add_argument("--namespace", required=True)
    put.add_argument("--bucket", required=True)
    put.add_argument("--object-name", required=True)
    put.set_defaults(handler=upload)
    get = commands.add_parser("download")
    get.add_argument("--output", required=True)
    get.add_argument("--sha256", required=True)
    get.add_argument("--region", required=True)
    get.add_argument("--compartment-id", required=True)
    get.add_argument("--namespace", required=True)
    get.add_argument("--bucket", required=True)
    get.add_argument("--object-name", required=True)
    get.set_defaults(handler=download)
    recover = commands.add_parser("restore")
    add_runtime_arguments(recover)
    recover.add_argument("--file", required=True)
    recover.add_argument("--identity", required=True)
    recover.add_argument("--sha256", required=True)
    recover.set_defaults(handler=restore)
    return root


def main() -> int:
    try:
        arguments = parser().parse_args()
        if hasattr(arguments, "mysql_port"):
            ports_valid = 0 <= arguments.mysql_port <= 65535 and 0 <= arguments.redis_port <= 65535
            disposable = arguments.project.startswith(("cca-data-it-", "cca-restore-it-"))
            if not ports_valid or (0 in (arguments.mysql_port, arguments.redis_port) and not disposable):
                fail("Host ports must be 1-65535; port 0 is reserved for cca-*-it-* disposable tests.")
        arguments.handler(arguments)
        return 0
    except DataOperationError as exception:
        print(f"ERROR: {exception}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
