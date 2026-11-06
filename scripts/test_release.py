import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import release


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.root_patch = patch.object(release, "ROOT", self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)
        self.env_patch = patch.dict(release.os.environ, {}, clear=True)
        self.env_patch.start()
        self.addCleanup(self.env_patch.stop)
        (self.root / "version.properties").write_text("versionName=0.1.1\nversionCode=2\n")

    def prepare(self):
        output = self.root / "app/build/outputs/apk/release"
        output.mkdir(parents=True)
        for abi in ["arm64-v8a", "x86_64"]:
            (output / f"app-{abi}-release.apk").write_bytes(f"signed fixture {abi}".encode())
        return release.package()

    def test_package_uses_one_version_and_both_architectures(self):
        metadata = self.prepare()
        self.assertEqual(2, metadata["versionCode"])
        self.assertEqual("v0.1.1", metadata["tag"])
        self.assertEqual({"arm64-v8a", "x86_64"}, set(metadata["assets"]))
        for item in metadata["assets"].values():
            payload = (self.root / "dist" / item["name"]).read_bytes()
            self.assertEqual(release.hashlib.sha256(payload).hexdigest(), item["sha256"])

    def test_release_tag_must_match_source_version(self):
        with patch.dict(release.os.environ, {"GITHUB_REF_TYPE": "tag", "GITHUB_REF_NAME": "v8.0.0"}):
            with self.assertRaises(ValueError):
                release.version()

    def test_invalid_version_is_rejected(self):
        (self.root / "version.properties").write_text("versionName=../../bad\nversionCode=0\n")
        with self.assertRaises(ValueError):
            release.version()

    def test_version_code_must_increase_before_upload(self):
        self.prepare()
        missing = subprocess.CompletedProcess([], 1, "", "release not found")
        previous = {"draft": False, "prerelease": False, "tag_name": "v0.1.0", "assets": [{"name": "orca-update.json", "url": "https://api.github.com/asset/1"}]}
        with patch.object(release, "gh", side_effect=[missing, subprocess.CompletedProcess([], 0, json.dumps([previous]), ""), subprocess.CompletedProcess([], 0, '{"versionCode":3}', "")]) as api:
            with self.assertRaises(ValueError):
                release.publish("projectGloria/Orca")
            self.assertFalse(any(call.args[:2] == ("release", "upload") for call in api.call_args_list))

    def test_completed_release_is_not_overwritten(self):
        self.prepare()
        current = {"isDraft": False, "tagName": "v0.1.1", "assets": [{"name": "orca-update.json", "apiUrl": "https://api.github.com/asset/2"}]}
        with patch.object(release, "gh", side_effect=[subprocess.CompletedProcess([], 0, json.dumps(current), ""), subprocess.CompletedProcess([], 0, '{"versionCode":2}', "")]) as api:
            release.publish("projectGloria/Orca")
            self.assertEqual(2, api.call_count)


if __name__ == "__main__":
    unittest.main()
