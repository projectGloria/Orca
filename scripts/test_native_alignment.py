import io
import struct
import unittest
import zipfile

from check_native_alignment import inspect_archive


def elf(alignment):
    payload = bytearray(120)
    payload[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<Q', payload, 32, 64)
    struct.pack_into('<HH', payload, 54, 56, 1)
    struct.pack_into('<I', payload, 64, 1)
    struct.pack_into('<Q', payload, 112, alignment)
    return bytes(payload)


def archive(entries):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w') as output:
        for name, payload in entries.items():
            output.writestr(name, payload)
    stream.seek(0)
    return stream


class NativeAlignmentTests(unittest.TestCase):
    def test_versioned_and_extensionless_elfs_are_inspected(self):
        inner = archive({'usr/lib/libavcodec.so.61.19.101': elf(4096), 'usr/bin/helper': elf(16384)})
        apk = archive({'lib/x86_64/libffmpeg.zip.so': inner.getvalue(), 'assets/readme': b'not ELF'})
        results = inspect_archive(apk)
        self.assertEqual(2, len(results))
        self.assertEqual([4096, 16384], [alignment for _, alignment in results])

    def test_ordinary_shared_library_still_inspected(self):
        self.assertEqual([('lib/arm64-v8a/libengine.so', 16384)], inspect_archive(archive({'lib/arm64-v8a/libengine.so': elf(16384)})))


if __name__ == '__main__':
    unittest.main()
