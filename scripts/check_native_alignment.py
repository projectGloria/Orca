"""Inspect ELF PT_LOAD alignment in an APK, including bundled runtime archives."""
import io
import struct
import sys
import zipfile

def inspect_archive(data, prefix=""):
    result = []
    with zipfile.ZipFile(data) as archive:
        for name in archive.namelist():
            if name.endswith("/"):
                continue
            with archive.open(name) as stream:
                magic = stream.read(4)
            if magic != b"\x7fELF" and not (magic.startswith(b"PK") and name.endswith(".zip.so")):
                continue
            payload = archive.read(name)
            label = prefix + name
            if payload.startswith(b"PK"):
                result += inspect_archive(io.BytesIO(payload), label + "!/")
            elif payload.startswith(b"\x7fELF"):
                bits = payload[4]
                endian = "<" if payload[5] == 1 else ">"
                if bits == 2:
                    offset = struct.unpack_from(endian + "Q", payload, 32)[0]
                    entry_size, count = struct.unpack_from(endian + "HH", payload, 54)
                    align_offset, align_format = 48, "Q"
                else:
                    offset = struct.unpack_from(endian + "I", payload, 28)[0]
                    entry_size, count = struct.unpack_from(endian + "HH", payload, 42)
                    align_offset, align_format = 28, "I"
                alignments = [struct.unpack_from(endian + align_format, payload, offset + i * entry_size + align_offset)[0]
                              for i in range(count) if struct.unpack_from(endian + "I", payload, offset + i * entry_size)[0] == 1]
                minimum = min(alignments, default=0)
                result.append((label, minimum))
    return result

if __name__ == "__main__":
    results = inspect_archive(sys.argv[1])
    for name, alignment in results:
        print(f"{'OK' if alignment >= 16384 else 'CHECK'}\t{alignment}\t{name}")
    print(f"{len(results)} ELF files; {sum(a < 16384 for _, a in results)} below 16 KB alignment")
    sys.exit(not results or any(a < 16384 for _, a in results))
