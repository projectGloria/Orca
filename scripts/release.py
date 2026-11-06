"""Version, package and publish Orca releases using only Python's standard library + gh."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def version():
    fields = dict(line.strip().split("=", 1) for line in (ROOT / "version.properties").read_text().splitlines()
                  if line.strip() and not line.lstrip().startswith("#"))
    name, code = fields["versionName"], int(fields["versionCode"])
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", name) or not 1 <= code <= 2_100_000_000:
        raise ValueError("Use a stable x.y.z versionName and a positive Android versionCode.")
    tag = f"v{name}"
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text()) if os.environ.get("GITHUB_EVENT_PATH") else {}
    event_tag = event.get("release", {}).get("tag_name")
    if os.environ.get("GITHUB_REF_TYPE") == "tag":
        event_tag = os.environ["GITHUB_REF_NAME"]
    if event_tag and event_tag != tag:
        raise ValueError(f"Release tag {event_tag!r} does not match version.properties ({tag}).")
    return name, code, tag


def package():
    name, code, tag = version()
    destination = ROOT / "dist"
    destination.mkdir(exist_ok=True)
    assets = {}
    for abi, suffix in (("arm64-v8a", "arm64"), ("x86_64", "x86_64")):
        source = ROOT / f"app/build/outputs/apk/release/app-{abi}-release.apk"
        target = destination / f"Orca-{name}-{suffix}.apk"
        shutil.copyfile(source, target)
        with target.open("rb") as source:
            digest = hashlib.file_digest(source, "sha256").hexdigest()
        assets[abi] = {"name": target.name, "sha256": digest, "size": target.stat().st_size}
    metadata = {"schemaVersion": 1, "applicationId": "io.github.nihil.orca", "versionName": name,
                "versionCode": code, "tag": tag, "assets": assets}
    manifest = destination / "orca-update.json"
    manifest.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    files = [destination / asset["name"] for asset in assets.values()] + [manifest]
    with (destination / "SHA256SUMS.txt").open("w", encoding="utf-8") as output:
        for path in files:
            with path.open("rb") as source:
                output.write(f"{hashlib.file_digest(source, 'sha256').hexdigest()}  {path.name}\n")
    return metadata


def gh(*args, check=True):
    return subprocess.run(["gh", *args], check=check, text=True, capture_output=True)


def publish(repository):
    _, code, tag = version()
    metadata = json.loads((ROOT / "dist/orca-update.json").read_text())
    current = gh("release", "view", tag, "--repo", repository, "--json", "assets,isDraft,tagName", check=False)
    existing = json.loads(current.stdout) if current.returncode == 0 else None
    if existing and any(asset["name"] == "orca-update.json" for asset in existing["assets"]):
        asset = next(asset for asset in existing["assets"] if asset["name"] == "orca-update.json")
        previous = json.loads(gh("api", asset["apiUrl"], "-H", "Accept: application/octet-stream").stdout)
        if code != previous["versionCode"]:
            raise ValueError("This versionName is already published. Increase both versionName and versionCode.")
        print(f"{tag} is already complete; published assets are immutable. Bump version.properties for another release.")
        return
    for release in json.loads(gh("api", f"repos/{repository}/releases?per_page=100").stdout):
        if not release["draft"] and not release["prerelease"] and release["tag_name"] != tag:
            previous_manifest = next((asset for asset in release["assets"] if asset["name"] == "orca-update.json"), None)
            if previous_manifest is None:
                continue
            previous = json.loads(gh("api", previous_manifest["url"], "-H", "Accept: application/octet-stream").stdout)
            if code <= previous["versionCode"]:
                raise ValueError("versionCode must increase beyond the latest published release.")
            break
    if existing is None:
        # A failed lookup due to permissions/network must not masquerade as a missing release.
        if "404" not in current.stderr and "not found" not in current.stderr.lower():
            raise RuntimeError(current.stderr)
        gh("release", "create", tag, "--repo", repository, "--target", os.environ["GITHUB_SHA"],
           "--draft", "--title", f"Orca {metadata['versionName']}", "--generate-notes")
    files = [str(ROOT / "dist" / asset["name"]) for asset in metadata["assets"].values()]
    gh("release", "upload", tag, *files, str(ROOT / "dist/SHA256SUMS.txt"), "--repo", repository, "--clobber")
    # Publish the readiness marker last. The app never offers an incomplete release.
    gh("release", "upload", tag, str(ROOT / "dist/orca-update.json"), "--repo", repository)
    if existing is None or existing["isDraft"]:
        gh("release", "edit", tag, "--repo", repository, "--draft=false", "--latest")
    print(f"Published https://github.com/{repository}/releases/tag/{tag}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["version", "package", "publish"])
    parser.add_argument("--repository", default="projectGloria/Orca")
    args = parser.parse_args()
    if args.command == "version":
        name, code, tag = version()
        lines = f"name={name}\ncode={code}\ntag={tag}\n"
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
                output.write(lines)
        print(lines, end="")
    elif args.command == "package":
        print(json.dumps(package(), indent=2))
    else:
        publish(args.repository)
