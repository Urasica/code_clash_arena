#!/usr/bin/env python3
"""Publish immutable OCI artifacts and wait for an OCI DevOps deployment."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

OCID_RE = re.compile(r"^ocid1\.[a-z0-9-]+\.[a-z0-9-]+\.[a-z0-9-]*\.[a-z0-9]+$")
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
ARGUMENT_NAME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*$")
ARTIFACT_PATH_RE = re.compile(r"^[A-Za-z0-9._/-]{1,512}$")
TERMINAL_STATES = {"SUCCEEDED", "FAILED", "CANCELED"}


class OciDeployError(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class OciClient:
    def __init__(self, region: str, config_file: str | None = None):
        if not re.fullmatch(r"[a-z]{2}-[a-z]+-[0-9]", region):
            raise OciDeployError("OCI region is invalid.")
        self.base = ["oci"]
        if config_file:
            self.base.extend(["--config-file", config_file])
        self.base.extend(["--region", region, "--output", "json"])

    def run(self, arguments: list[str], *, raw: bool = False) -> subprocess.CompletedProcess[str]:
        command = [*self.base, *arguments]
        if raw:
            command.extend(["--raw-output"])
        return subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)


def validate_ocid(value: str, expected_type: str) -> str:
    if not OCID_RE.fullmatch(value) or not value.startswith(f"ocid1.{expected_type}."):
        raise OciDeployError(f"Expected a valid {expected_type} OCID.")
    return value


def verify_remote_artifact(
    client: OciClient, repository_id: str, artifact_path: str, version: str, expected: Path
) -> None:
    with tempfile.TemporaryDirectory(prefix="cca-oci-artifact-") as directory:
        downloaded = Path(directory) / "artifact"
        result = client.run([
            "artifacts", "generic", "artifact", "download-by-path",
            "--repository-id", repository_id,
            "--artifact-path", artifact_path,
            "--artifact-version", version,
            "--file", str(downloaded),
        ])
        if result.returncode or not downloaded.is_file():
            raise OciDeployError("OCI artifact could not be downloaded for integrity verification.")
        if sha256_file(downloaded) != sha256_file(expected):
            raise OciDeployError("OCI artifact content differs from the tested local artifact.")


def publish_artifact(
    client: OciClient, repository_id: str, artifact_path: str, version: str, source: Path
) -> dict[str, str]:
    validate_ocid(repository_id, "artifactrepository")
    if not ARTIFACT_PATH_RE.fullmatch(artifact_path) or ".." in artifact_path.split("/"):
        raise OciDeployError("Artifact path is invalid.")
    if not SHA_RE.fullmatch(version):
        raise OciDeployError("Artifact version must be a full Git commit SHA.")
    if source.is_symlink() or not source.is_file() or source.stat().st_size == 0:
        raise OciDeployError("Artifact source must be a non-empty regular file.")

    upload = client.run([
        "artifacts", "generic", "artifact", "upload-by-path",
        "--repository-id", repository_id,
        "--artifact-path", artifact_path,
        "--artifact-version", version,
        "--content-body", str(source),
    ])
    # Immutable repositories reject a retry. Reuse is allowed only after downloading
    # the existing object and proving that it is byte-identical.
    if upload.returncode:
        verify_remote_artifact(client, repository_id, artifact_path, version, source)
        result = "reused"
    else:
        verify_remote_artifact(client, repository_id, artifact_path, version, source)
        result = "uploaded"
    return {"result": result, "sha256": sha256_file(source), "version": version}


def parse_argument(value: str) -> tuple[str, str]:
    if "=" not in value:
        raise OciDeployError("Deployment arguments must use NAME=VALUE.")
    name, argument_value = value.split("=", 1)
    if (
        not ARGUMENT_NAME_RE.fullmatch(name)
        or any(word in name.lower() for word in ("password", "secret", "token", "key"))
        or not argument_value
        or any(ord(character) < 32 for character in argument_value)
    ):
        raise OciDeployError("Deployment argument is invalid or appears to contain secret material.")
    return name, argument_value


def write_receipt(path: Path | None, payload: dict[str, str]) -> None:
    if path is None:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")


def run_deployment(
    client: OciClient,
    pipeline_id: str,
    arguments: list[tuple[str, str]],
    timeout_seconds: int,
    poll_seconds: int,
    receipt: Path | None,
) -> dict[str, str]:
    validate_ocid(pipeline_id, "devopsdeploypipeline")
    collection = {"items": [{"name": name, "value": value} for name, value in arguments]}
    started = client.run([
        "devops", "deployment", "create-pipeline-deployment",
        "--pipeline-id", pipeline_id,
        "--deployment-arguments", json.dumps(collection, separators=(",", ":")),
        "--query", "data.id",
    ], raw=True)
    deployment_id = started.stdout.strip().strip('"')
    if started.returncode or not OCID_RE.fullmatch(deployment_id) or not deployment_id.startswith("ocid1.devopsdeployment."):
        raise OciDeployError("OCI DevOps deployment could not be started.")

    deadline = time.monotonic() + timeout_seconds
    payload = {"deployment_id": deployment_id, "state": "ACCEPTED"}
    write_receipt(receipt, payload)
    while time.monotonic() < deadline:
        status = client.run([
            "devops", "deployment", "get",
            "--deployment-id", deployment_id,
            "--query", "data.lifecycle-state",
        ], raw=True)
        state = status.stdout.strip().strip('"').upper()
        if status.returncode or not re.fullmatch(r"[A-Z_]+", state):
            raise OciDeployError("OCI DevOps deployment state could not be read.")
        payload = {"deployment_id": deployment_id, "state": state}
        write_receipt(receipt, payload)
        if state in TERMINAL_STATES:
            if state != "SUCCEEDED":
                raise OciDeployError(f"OCI DevOps deployment ended in state {state}.")
            return payload
        time.sleep(poll_seconds)
    raise OciDeployError("OCI DevOps deployment did not finish before the timeout.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default=os.environ.get("OCI_REGION", "ap-tokyo-1"))
    parser.add_argument("--config-file", default=os.environ.get("OCI_CLI_CONFIG_FILE"))
    subparsers = parser.add_subparsers(dest="command", required=True)

    publish = subparsers.add_parser("publish")
    publish.add_argument("--repository-id", required=True)
    publish.add_argument("--artifact-path", required=True)
    publish.add_argument("--version", required=True)
    publish.add_argument("--file", required=True)

    deploy = subparsers.add_parser("deploy")
    deploy.add_argument("--pipeline-id", required=True)
    deploy.add_argument("--argument", action="append", default=[])
    deploy.add_argument("--timeout-seconds", type=int, default=2700)
    deploy.add_argument("--poll-seconds", type=int, default=15)
    deploy.add_argument("--receipt")

    args = parser.parse_args()
    try:
        client = OciClient(args.region, args.config_file)
        if args.command == "publish":
            result = publish_artifact(
                client, args.repository_id, args.artifact_path, args.version, Path(args.file).resolve()
            )
        else:
            if not 60 <= args.timeout_seconds <= 7200 or not 5 <= args.poll_seconds <= 60:
                raise OciDeployError("Deployment wait bounds are invalid.")
            result = run_deployment(
                client,
                args.pipeline_id,
                [parse_argument(value) for value in args.argument],
                args.timeout_seconds,
                args.poll_seconds,
                Path(args.receipt).resolve() if args.receipt else None,
            )
        print(json.dumps(result, sort_keys=True))
    except OciDeployError as error:
        print(f"oci_deploy: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
