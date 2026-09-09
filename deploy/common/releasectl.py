#!/usr/bin/env python3
"""Build, verify, activate, and roll back immutable deployment releases."""

from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
import os
import re
import shutil
import sys
from pathlib import Path, PurePosixPath
from urllib.parse import urlsplit

MANIFEST_NAME = "release-manifest.json"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
DIGEST_IMAGE_RE = re.compile(r"^[^@\s]+@sha256:[0-9a-f]{64}$")
SAFE_VALUE_RE = re.compile(r"^[A-Za-z0-9._:/@+-]{1,512}$")
DNS_LABEL_RE = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
TARGET_PLATFORM = "linux/amd64"


class ReleaseError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ReleaseError(message)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_relative(path: Path, root: Path) -> str:
    relative = path.relative_to(root).as_posix()
    candidate = PurePosixPath(relative)
    if candidate.is_absolute() or any(part in {"", ".", ".."} for part in candidate.parts):
        fail("Release paths must remain below the release root.")
    return candidate.as_posix()


def release_files(root: Path) -> list[dict[str, object]]:
    if not root.is_dir() or root.is_symlink():
        fail("Release root must be a real directory.")
    files: list[dict[str, object]] = []
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            fail("Release artifacts must not contain symbolic links.")
        if path.is_dir():
            continue
        if not path.is_file():
            fail("Release artifacts may contain regular files only.")
        relative = safe_relative(path, root)
        if relative == MANIFEST_NAME:
            continue
        files.append({"path": relative, "sha256": sha256_file(path), "size": path.stat().st_size})
    if not files:
        fail("Release artifact is empty.")
    return files


def validate_source_sha(value: str) -> str:
    if not SHA_RE.fullmatch(value):
        fail("Source SHA must be a full lowercase Git commit SHA.")
    return value


def validate_metadata(component: str, metadata: object) -> dict[str, str]:
    if not isinstance(metadata, dict) or not all(
        isinstance(key, str) and isinstance(value, str) for key, value in metadata.items()
    ):
        fail("Release metadata must contain string keys and values only.")
    clean = dict(sorted(metadata.items()))
    if any(not SAFE_VALUE_RE.fullmatch(value) for value in clean.values()):
        fail("Release metadata contains an unsafe value.")
    if component == "frontend":
        origin = clean.get("public_origin", "")
        parsed = urlsplit(origin)
        hostname = parsed.hostname or ""
        try:
            port = parsed.port
            ipaddress.ip_address(hostname)
            is_ip = True
        except ValueError:
            try:
                port = parsed.port
            except ValueError:
                fail("Frontend release requires a bare HTTPS public_origin.")
            is_ip = False
        labels = hostname.split(".")
        if (
            parsed.scheme != "https"
            or not hostname
            or parsed.username is not None
            or parsed.password is not None
            or port is not None
            or parsed.netloc.lower() != hostname.lower()
            or "." not in hostname
            or is_ip
            or len(hostname) > 253
            or not all(DNS_LABEL_RE.fullmatch(label) for label in labels)
            or parsed.path not in {"", "/"}
            or parsed.query
            or parsed.fragment
        ):
            fail("Frontend release requires a bare HTTPS public_origin.")
    elif component == "backend":
        for key in ("engine_image", "migration_image", "data_mysql_image", "data_redis_image"):
            if not DIGEST_IMAGE_RE.fullmatch(clean.get(key, "")):
                fail(f"Backend release requires digest-pinned {key} metadata.")
        if not SAFE_VALUE_RE.fullmatch(clean.get("engine_policy_version", "")):
            fail("Backend release requires an engine policy version.")
    else:
        fail("Component must be frontend or backend.")
    return clean


def create_manifest(root: Path, component: str, source_sha: str, metadata: dict[str, str]) -> dict[str, object]:
    validate_source_sha(source_sha)
    manifest = {
        "schema": 1,
        "component": component,
        "source_sha": source_sha,
        "target": TARGET_PLATFORM,
        "metadata": validate_metadata(component, metadata),
        "files": release_files(root),
    }
    output = root / MANIFEST_NAME
    if output.is_symlink() or (output.exists() and not output.is_file()):
        fail("Manifest output path is invalid.")
    output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    return manifest


def load_manifest(root: Path) -> dict[str, object]:
    path = root / MANIFEST_NAME
    if path.is_symlink() or not path.is_file():
        fail("Release manifest is missing or linked.")
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ReleaseError("Release manifest is unreadable.") from error
    if not isinstance(manifest, dict):
        fail("Release manifest root must be an object.")
    return manifest


def verify_release(root: Path, component: str, source_sha: str) -> dict[str, object]:
    validate_source_sha(source_sha)
    manifest = load_manifest(root)
    if (
        manifest.get("schema") != 1
        or manifest.get("component") != component
        or manifest.get("source_sha") != source_sha
        or manifest.get("target") != TARGET_PLATFORM
    ):
        fail("Release identity does not match the requested deployment.")
    validate_metadata(component, manifest.get("metadata"))
    recorded = manifest.get("files")
    if not isinstance(recorded, list) or recorded != release_files(root):
        fail("Release contents do not match the signed manifest.")
    return manifest


def atomic_link(link: Path, target: str) -> None:
    temporary = link.with_name(f".{link.name}.tmp-{os.getpid()}")
    if os.path.lexists(temporary):
        fail("Temporary release link already exists.")
    try:
        os.symlink(target, temporary, target_is_directory=True)
        os.replace(temporary, link)
    finally:
        if os.path.lexists(temporary):
            temporary.unlink()


def read_release_link(link: Path) -> str | None:
    if not os.path.lexists(link):
        return None
    if not link.is_symlink():
        fail(f"{link.name} must be a symbolic link.")
    target = os.readlink(link).replace("\\", "/")
    parts = PurePosixPath(target).parts
    if len(parts) != 2 or parts[0] != "releases" or not SHA_RE.fullmatch(parts[1]):
        fail(f"{link.name} points outside the immutable release set.")
    return PurePosixPath(*parts).as_posix()


def install_release(staging: Path, release_root: Path, component: str, source_sha: str) -> dict[str, object]:
    manifest = verify_release(staging, component, source_sha)
    release_root = release_root.resolve()
    releases = release_root / "releases"
    releases.mkdir(parents=True, exist_ok=True)
    destination = releases / source_sha
    if destination.exists():
        verify_release(destination, component, source_sha)
    else:
        pending = releases / f".{source_sha}.pending-{os.getpid()}"
        if pending.exists():
            fail("Pending release directory already exists.")
        try:
            shutil.copytree(staging, pending, copy_function=shutil.copy2)
            verify_release(pending, component, source_sha)
            os.replace(pending, destination)
        finally:
            if pending.exists():
                shutil.rmtree(pending)

    current = release_root / "current"
    previous = release_root / "previous"
    current_target = read_release_link(current)
    new_target = f"releases/{source_sha}"
    if current_target and current_target != new_target:
        atomic_link(previous, current_target)
    atomic_link(current, new_target)
    return {"result": "activated", "component": component, "source_sha": source_sha, "metadata": manifest["metadata"]}


def rollback_release(release_root: Path, component: str) -> dict[str, object]:
    release_root = release_root.resolve()
    current = release_root / "current"
    previous = release_root / "previous"
    current_target = read_release_link(current)
    previous_target = read_release_link(previous)
    if previous_target is None:
        fail("No previous release is available for rollback.")
    previous_sha = PurePosixPath(previous_target).parts[1]
    verify_release(release_root / previous_target, component, previous_sha)
    atomic_link(current, previous_target)
    if current_target:
        atomic_link(previous, current_target)
    return {"result": "rolled_back", "component": component, "source_sha": previous_sha}


def read_metadata(root: Path, component: str, source_sha: str, key: str) -> str:
    manifest = verify_release(root, component, source_sha)
    metadata = manifest["metadata"]
    assert isinstance(metadata, dict)
    value = metadata.get(key)
    if not isinstance(value, str):
        fail("Requested release metadata is missing.")
    return value


def read_metadata_file(path: str | None) -> dict[str, str]:
    if path is None:
        return {}
    try:
        value = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ReleaseError("Metadata file is unreadable.") from error
    if not isinstance(value, dict):
        fail("Metadata file must contain an object.")
    return value


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    create = subparsers.add_parser("create")
    create.add_argument("--root", required=True)
    create.add_argument("--component", choices=("frontend", "backend"), required=True)
    create.add_argument("--source-sha", required=True)
    create.add_argument("--metadata-file")

    verify = subparsers.add_parser("verify")
    verify.add_argument("--root", required=True)
    verify.add_argument("--component", choices=("frontend", "backend"), required=True)
    verify.add_argument("--source-sha", required=True)

    install = subparsers.add_parser("install")
    install.add_argument("--staging", required=True)
    install.add_argument("--release-root", required=True)
    install.add_argument("--component", choices=("frontend", "backend"), required=True)
    install.add_argument("--source-sha", required=True)

    rollback = subparsers.add_parser("rollback")
    rollback.add_argument("--release-root", required=True)
    rollback.add_argument("--component", choices=("frontend", "backend"), required=True)

    metadata = subparsers.add_parser("metadata")
    metadata.add_argument("--root", required=True)
    metadata.add_argument("--component", choices=("frontend", "backend"), required=True)
    metadata.add_argument("--source-sha", required=True)
    metadata.add_argument("--key", required=True)

    args = parser.parse_args()
    try:
        if args.command == "create":
            result = create_manifest(
                Path(args.root).resolve(), args.component, args.source_sha, read_metadata_file(args.metadata_file)
            )
            print(json.dumps({"result": "created", "files": len(result["files"]), "source_sha": args.source_sha}))
        elif args.command == "verify":
            result = verify_release(Path(args.root).resolve(), args.component, args.source_sha)
            print(json.dumps({"result": "verified", "files": len(result["files"]), "source_sha": args.source_sha}))
        elif args.command == "install":
            print(json.dumps(install_release(
                Path(args.staging).resolve(), Path(args.release_root), args.component, args.source_sha
            ), sort_keys=True))
        elif args.command == "rollback":
            print(json.dumps(rollback_release(Path(args.release_root), args.component), sort_keys=True))
        else:
            print(read_metadata(Path(args.root).resolve(), args.component, args.source_sha, args.key))
    except ReleaseError as error:
        print(f"releasectl: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
