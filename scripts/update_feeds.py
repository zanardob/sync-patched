#!/usr/bin/env python3
"""Write bundles/stable.json from the newest stable release that carries the bundle."""

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET = "patches.rvp"
FEED = ROOT / "bundles" / "stable.json"


def load_releases(path):
    """Accept a flat release list or the array of pages that gh --paginate --slurp writes."""
    pages = json.loads(path.read_text(encoding="utf-8"))
    if pages and isinstance(pages[0], list):
        return [release for page in pages for release in page]
    return pages


def patch_asset(release):
    return next(
        (
            asset
            for asset in release.get("assets", [])
            if asset.get("name") == ASSET and asset.get("state") == "uploaded"
        ),
        None,
    )


def stable_release(releases):
    candidates = [
        release
        for release in releases
        if not release.get("draft")
        and not release.get("prerelease")
        and release.get("published_at")
        and patch_asset(release)
    ]
    if not candidates:
        raise SystemExit(f"No published release carries {ASSET}.")
    return max(candidates, key=lambda release: release["published_at"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--releases", type=Path, required=True, help="GitHub releases API response")
    args = parser.parse_args()

    release = stable_release(load_releases(args.releases))
    feed = {
        # Manager parses this as a LocalDateTime, so the trailing Z must go.
        "created_at": release["published_at"].removesuffix("Z"),
        "description": release.get("body") or "",
        "download_url": patch_asset(release)["browser_download_url"],
        "signature_download_url": "N/A",
        "version": release["tag_name"],
    }
    FEED.parent.mkdir(exist_ok=True)
    FEED.write_text(json.dumps(feed, indent=2) + "\n", encoding="utf-8")
    print(f"{release['tag_name']} -> {feed['download_url']}")


if __name__ == "__main__":
    main()
