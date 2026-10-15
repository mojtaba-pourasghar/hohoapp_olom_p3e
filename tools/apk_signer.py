#!/usr/bin/env python3
"""Which certificate signed an APK.

The one fact that decides whether a new APK installs over the one already on the phone. Android
refuses an update signed by a different key, and the error it shows («نصب نشد») says nothing
about signatures — so the fingerprint is printed on every build instead of being guessed at.

Reads the APK Signing Block directly: no apksigner, no Android SDK, nothing to install.
"""
import hashlib
import struct
import sys
import zipfile

V2 = 0x7109871A
V3 = 0xF05368C0


def certificates(path):
    """Every signer certificate in the APK Signing Block, as DER bytes."""
    with open(path, "rb") as handle:
        data = handle.read()

    end = data.rfind(b"PK\x05\x06")
    if end < 0:
        raise ValueError("این فایل zip نیست")
    directory_at = struct.unpack_from("<I", data, end + 16)[0]
    if data[directory_at - 16:directory_at] != b"APK Sig Block 42":
        return []                                     # v1 only, or not signed at all

    # the block is  [size][pairs…][size][magic], and both sizes count everything but the first
    size = struct.unpack_from("<Q", data, directory_at - 24)[0]
    body = data[directory_at - size:directory_at - 24]

    out = []
    at = 0
    while at + 12 <= len(body):
        pair = struct.unpack_from("<Q", body, at)[0]
        kind = struct.unpack_from("<I", body, at + 8)[0]
        value = body[at + 12:at + 8 + pair]
        if kind in (V2, V3):
            out.extend(_signers(value[4:]))
        at += 8 + pair
    return out


def _signers(blob):
    out = []
    at = 0
    while at + 4 <= len(blob):
        length = struct.unpack_from("<I", blob, at)[0]
        signer = blob[at + 4:at + 4 + length]
        signed_length = struct.unpack_from("<I", signer, 0)[0]
        signed = signer[4:4 + signed_length]
        digests = struct.unpack_from("<I", signed, 0)[0]   # digests come first, then the certs
        certs = signed[4 + digests:]
        certs_length = struct.unpack_from("<I", certs, 0)[0]
        out.extend(_each(certs[4:4 + certs_length]))
        at += 4 + length
    return out


def _each(blob):
    out = []
    at = 0
    while at + 4 <= len(blob):
        length = struct.unpack_from("<I", blob, at)[0]
        out.append(blob[at + 4:at + 4 + length])
        at += 4 + length
    return out


def pretty(digest):
    return ":".join(digest[i:i + 2] for i in range(0, len(digest), 2)).upper()


def main(path):
    certs = certificates(path)
    names = zipfile.ZipFile(path).namelist()
    v1 = any(n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC")) for n in names)

    if not certs:
        print(f"{path}: گواهیِ v2/v3 خوانده نشد"
              + (" — فقط امضای v1 دارد" if v1 else " — این APK امضا نشده است!"))
        return 1
    seen = {hashlib.sha256(cert).hexdigest() for cert in certs}
    for digest in sorted(seen):
        print(f"SHA-256 گواهی: {pretty(digest)}")
    print(f"امضاها: {'v1 + ' if v1 else ''}v2/v3")
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("usage: apk_signer.py <app.apk>", file=sys.stderr)
        raise SystemExit(2)
    raise SystemExit(main(sys.argv[1]))
