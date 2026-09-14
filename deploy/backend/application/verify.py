#!/usr/bin/env python3
"""Fail-closed smoke checks for the private application release."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

DIGEST_IMAGE_RE = re.compile(r"^[^@\s]+@sha256:[0-9a-f]{64}$")


class VerificationError(RuntimeError):
    pass


def fetch(url: str) -> tuple[int, object, bytes]:
    try:
        response = urlopen(Request(url, headers={"User-Agent": "cca-release-verifier/1"}), timeout=10)
        return response.status, response.headers, response.read(1024 * 1024)
    except HTTPError as error:
        return error.code, error.headers, error.read(1024 * 1024)
    except (OSError, URLError) as error:
        raise VerificationError("local application endpoint could not be reached") from error


def verify(engine_image: str) -> None:
    if not DIGEST_IMAGE_RE.fullmatch(engine_image):
        raise VerificationError("engine image must be digest-pinned")

    status, _, body = fetch("http://127.0.0.1:8081/actuator/health/readiness")
    try:
        health = json.loads(body)
        components = health["components"]
    except (KeyError, TypeError, json.JSONDecodeError, UnicodeDecodeError) as error:
        raise VerificationError("readiness response was not valid health JSON") from error
    required = ("db", "redis", "engineImage")
    if status != 200 or health.get("status") != "UP" or any(components.get(name, {}).get("status") != "UP" for name in required):
        raise VerificationError("database, Redis, or engine readiness is not UP")

    status, headers, _ = fetch("http://127.0.0.1:8080/api/auth/me")
    if status != 401 or not headers.get("X-Correlation-ID"):
        raise VerificationError("local API did not return the authenticated API contract")

    completed = subprocess.run(
        ["docker", "image", "inspect", engine_image, "--format", "{{.Os}}/{{.Architecture}}"],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
        timeout=15,
    )
    if completed.returncode or completed.stdout.strip() != "linux/amd64":
        raise VerificationError("engine image is unavailable or is not native linux/amd64")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--engine-image", required=True)
    args = parser.parse_args()
    try:
        verify(args.engine_image)
    except (VerificationError, subprocess.TimeoutExpired) as error:
        print(f"backend verification failed: {error}", file=sys.stderr)
        return 1
    print("backend verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
