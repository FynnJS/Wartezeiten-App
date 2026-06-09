import json
import os
import pathlib
import re
import subprocess
from datetime import datetime, timezone


def sanitize_tag(tag):
    return tag if tag else "main"


def version_name_from_tag(tag):
    return tag[1:] if tag.startswith("v") else tag


def determine_version_code(tag):
    match = re.match(r"^v?(\d+)\.(\d+)\.(\d+)$", tag)
    if match:
        major, minor, patch = map(int, match.groups())
        return major * 10000 + minor * 100 + patch
    count = int(subprocess.check_output(["git", "rev-list", "--count", "HEAD"]).decode().strip())
    return count


def find_release_apk():
    apk_dir = pathlib.Path("app/build/outputs/apk/release")
    apk_candidates = sorted(apk_dir.glob("*.apk"))
    if not apk_candidates:
        raise SystemExit(f"Release APK not found in {apk_dir}")
    if len(apk_candidates) > 1:
        print(f"Multiple release APKs found, using {apk_candidates[0]}: {apk_candidates}")
    return apk_candidates[0]


github_event = os.environ.get("GITHUB_EVENT_NAME", "")
github_repo = os.environ.get("GITHUB_REPOSITORY", "")
github_ref = os.environ.get("GITHUB_REF", "")
event_path = os.environ.get("GITHUB_EVENT_PATH", "")

tag_name = None
release_body = ""

if github_event == "release" and event_path:
    with open(event_path, encoding="utf-8") as f:
        event = json.load(f)
    tag_name = event["release"]["tag_name"]
    release_body = event["release"].get("body", "").strip()
elif github_ref.startswith("refs/tags/"):
    tag_name = github_ref.split("/", 2)[-1]
else:
    tag_name = "main"

if tag_name.startswith("refs/tags/"):
    tag_name = tag_name.split("/", 2)[-1]

release_tag = sanitize_tag(tag_name)
version_name = version_name_from_tag(release_tag)
version_code = determine_version_code(release_tag)
apk_source = find_release_apk()

apk_file_name = f"wartezeiten-app-{version_name}.apk"
apk_release_asset = apk_source.with_name(apk_file_name)
apk_release_asset.write_bytes(apk_source.read_bytes())
apk_url = f"https://github.com/{github_repo}/releases/download/{release_tag}/{apk_file_name}"
size_bytes = apk_source.stat().st_size
size_label = f"{size_bytes / (1024 * 1024):.2f}"
release_date = datetime.now(timezone.utc).strftime("%Y-%m-%d")

release_notes = [line for line in release_body.splitlines() if line.strip()]
if not release_notes:
    release_notes = [f"Automatisch generierter Release fuer {version_name}."]

release_json = {
    "versionName": version_name,
    "versionCode": version_code,
    "releaseDate": release_date,
    "releasePageUrl": f"https://github.com/{github_repo}/releases/tag/{release_tag}",
    "apkUrl": apk_url,
    "apkSize": size_label,
    "releaseNotes": release_notes,
    "showBanner": True,
}

website_dir = pathlib.Path("website")
website_dir.mkdir(exist_ok=True)
with open(website_dir / "release.json", "w", encoding="utf-8") as f:
    json.dump(release_json, f, indent=2, ensure_ascii=False)

print(f'release_file={website_dir / "release.json"}')
with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
    output.write(f"version_name={version_name}\n")
    output.write(f"version_code={version_code}\n")
    output.write(f"apk_file_name={apk_file_name}\n")
    output.write(f"apk_release_asset={apk_release_asset}\n")
    output.write(f"apk_url={apk_url}\n")
    output.write(f"apk_size={size_label}\n")
