#!/usr/bin/env python3
"""Create deterministic ZIP release archives from verified bundle directories."""

from __future__ import annotations

import argparse
import stat
import sys
import zipfile
from pathlib import Path, PurePosixPath

ZIP_EPOCH = (1980, 1, 1, 0, 0, 0)


class ArchiveError(RuntimeError):
    pass


def create_archive(root: Path, output: Path) -> None:
    root = root.resolve()
    output = output.resolve()
    if not root.is_dir() or root.is_symlink():
        raise ArchiveError("archive root must be a real directory")
    if output.exists() or output == root or root in output.parents:
        raise ArchiveError("archive output must be a new file outside the source root")

    files = []
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ArchiveError("release archives must not contain symbolic links")
        if path.is_dir():
            continue
        if not path.is_file():
            raise ArchiveError("release archives may contain regular files only")
        relative = PurePosixPath(path.relative_to(root).as_posix())
        if any(part in {"", ".", ".."} for part in relative.parts):
            raise ArchiveError("archive path escaped the source root")
        files.append((path, relative.as_posix()))
    if not files:
        raise ArchiveError("release archive cannot be empty")

    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for path, relative in files:
                mode = stat.S_IMODE(path.stat().st_mode)
                info = zipfile.ZipInfo(relative, ZIP_EPOCH)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = (stat.S_IFREG | mode) << 16
                with path.open("rb") as stream:
                    archive.writestr(info, stream.read(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    except Exception:
        output.unlink(missing_ok=True)
        raise


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        create_archive(Path(args.root), Path(args.output))
    except (ArchiveError, OSError, zipfile.BadZipFile) as error:
        print(f"archive: {error}", file=sys.stderr)
        return 1
    print(f"created deterministic archive: {Path(args.output).name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
