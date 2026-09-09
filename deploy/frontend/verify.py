#!/usr/bin/env python3
"""Fail-closed smoke checks for the public frontend release."""

from __future__ import annotations

import argparse
import ipaddress
import re
import ssl
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlsplit
from urllib.request import HTTPRedirectHandler, HTTPSHandler, Request, build_opener


class VerificationError(RuntimeError):
    pass


DNS_LABEL_RE = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        return None


def validate_origin(value: str) -> str:
    parsed = urlsplit(value)
    hostname = parsed.hostname or ""
    try:
        port = parsed.port
        ipaddress.ip_address(hostname)
        is_ip = True
    except ValueError:
        try:
            port = parsed.port
        except ValueError as error:
            raise VerificationError("public origin must be a bare HTTPS DNS origin") from error
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
        raise VerificationError("public origin must be a bare HTTPS DNS origin")
    return value.rstrip("/") + "/"


def request(opener, url: str) -> tuple[int, object, bytes]:
    try:
        response = opener.open(Request(url, headers={"User-Agent": "cca-release-verifier/1"}), timeout=10)
        return response.status, response.headers, response.read(1024 * 1024)
    except HTTPError as error:
        return error.code, error.headers, error.read(1024 * 1024)
    except (OSError, URLError) as error:
        raise VerificationError("public endpoint could not be reached") from error


def verify(origin: str, cafile: str | None = None) -> None:
    origin = validate_origin(origin)
    context = ssl.create_default_context(cafile=cafile)
    opener = build_opener(NoRedirect(), HTTPSHandler(context=context))

    status, _, body = request(opener, origin)
    if status != 200 or b'id="root"' not in body:
        raise VerificationError("frontend root did not return the expected release document")

    status, headers, _ = request(opener, urljoin(origin, "api/auth/me"))
    if status != 401 or not headers.get("X-Correlation-ID"):
        raise VerificationError("public API proxy did not return the authenticated API contract")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--origin", required=True)
    parser.add_argument("--ca-file")
    args = parser.parse_args()
    try:
        verify(args.origin, args.ca_file)
    except VerificationError as error:
        print(f"frontend verification failed: {error}", file=sys.stderr)
        return 1
    print("frontend verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
