"""Audit every ELF by magic bytes in an APK and its bundled runtime ZIP archives."""
import io
import json
from pathlib import Path
import struct
import sys
import zipfile


def inspect(archive, prefix=""):
    results = []
    for entry in archive.infolist():
        if entry.is_dir():
            continue
        with archive.open(entry) as stream:
            magic = stream.read(4)
        if magic == b"\x7fELF":
            payload = archive.read(entry)
            endian = "<" if payload[5] == 1 else ">"
            if payload[4] == 2:
                offset = struct.unpack_from(endian + "Q", payload, 32)[0]
                entry_size, count = struct.unpack_from(endian + "HH", payload, 54)
                alignment_offset, alignment_format = 48, "Q"
            else:
                offset = struct.unpack_from(endian + "I", payload, 28)[0]
                entry_size, count = struct.unpack_from(endian + "HH", payload, 42)
                alignment_offset, alignment_format = 28, "I"
            alignments = [
                struct.unpack_from(endian + alignment_format, payload, offset + index * entry_size + alignment_offset)[0]
                for index in range(count)
                if struct.unpack_from(endian + "I", payload, offset + index * entry_size)[0] == 1
            ]
            results.append({"path": prefix + entry.filename, "minimumAlignment": min(alignments, default=0),
                            "coveredByExistingScript": entry.filename.endswith(".so")})
        elif magic == b"PK\x03\x04" and entry.filename.endswith(".zip.so"):
            with zipfile.ZipFile(io.BytesIO(archive.read(entry))) as nested:
                results.extend(inspect(nested, prefix + entry.filename + "!/"))
    return results


if __name__ == "__main__":
    with zipfile.ZipFile(sys.argv[1]) as archive:
        results = inspect(archive)
    if len(sys.argv) > 2:
        Path(sys.argv[2]).write_text(json.dumps(results, indent=2), encoding="utf-8")
    missed = sum(not result["coveredByExistingScript"] for result in results)
    below = sum(result["minimumAlignment"] < 16384 for result in results)
    print(f"{len(results)} ELF files; {missed} missed by filename filter; {below} below 16 KB")
    sys.exit(not results or below > 0)
