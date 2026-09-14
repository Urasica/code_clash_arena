#!/usr/bin/env python3
"""Classify monorepo changes without losing changes after a failed deployment."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import PurePosixPath

FRONTEND_PREFIXES = ("frontend/", "deploy/frontend/")
BACKEND_PREFIXES = ("backend/code/", "engine/", "deploy/backend/")
BOTH_PREFIXES = (".github/workflows/", "deploy/common/", "deploy/host/", "infra/oci/")
BOTH_FILES = {".env.example", "compose.yaml"}
DOCUMENTATION_PREFIXES = ("doc/", "docs/", "roadmap/")
DOCUMENTATION_FILES = {"README.md"}


class ImpactError(ValueError):
    pass


def normalize_path(value: str) -> str:
    candidate = value.strip().replace("\\", "/")
    path = PurePosixPath(candidate)
    if not candidate or path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise ImpactError("Changed paths must be relative repository paths.")
    return path.as_posix()


def classify(paths: list[str]) -> dict[str, object]:
    normalized = sorted({normalize_path(path) for path in paths if path.strip()})
    frontend = False
    backend = False
    unknown: list[str] = []

    for path in normalized:
        if path in BOTH_FILES or path.startswith(BOTH_PREFIXES):
            frontend = backend = True
        elif path.startswith(FRONTEND_PREFIXES):
            frontend = True
        elif path.startswith(BACKEND_PREFIXES):
            backend = True
        elif (
            path in DOCUMENTATION_FILES
            or path.startswith(DOCUMENTATION_PREFIXES)
            or path.endswith(".md")
            or path == ".github/dependabot.yml"
        ):
            continue
        else:
            unknown.append(path)
            frontend = backend = True

    return {
        "frontend": frontend,
        "backend": backend,
        "documentation_only": bool(normalized) and not frontend and not backend,
        "unknown": unknown,
        "files": normalized,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--files-from",
        default="-",
        help="newline-delimited changed paths, or - for stdin",
    )
    args = parser.parse_args()
    if args.files_from == "-":
        paths = sys.stdin.read().splitlines()
    else:
        with open(args.files_from, encoding="utf-8") as stream:
            paths = stream.read().splitlines()
    try:
        print(json.dumps(classify(paths), sort_keys=True))
    except ImpactError as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
