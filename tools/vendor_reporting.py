#!/usr/bin/python3
"""Copy the portable native reporting package into a new standalone plugin repo.

The canonical source remains tracked in Last-Rites. Copies have explicit hashes,
and production builds never reach outside their own repositories.
"""

import argparse
import hashlib
import json
from pathlib import Path
import shutil


def main():
    """Validate the target first, then copy only the documented package and tests."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", type=Path)
    args = parser.parse_args()
    source = Path(__file__).resolve().parents[1]
    target = args.target.resolve()
    if not (target / ".git").is_dir() or not (target / "build.gradle").is_file():
        parser.error("target must be a standalone plugin repository with build.gradle")
    destinations = [Path("src") / kind / "java/com/runeliteplugins/reporting"
                    for kind in ("main", "test")]
    if any((target / path).exists() for path in destinations):
        parser.error("reporting already exists; migrate existing plugins in a reviewed change")
    manifest_path = target / "reporting-provenance.json"
    if manifest_path.exists():
        parser.error("reporting-provenance.json already exists")
    files = {}
    for relative in destinations:
        # Paths and contents come from explicit package directories, not source matching.
        for path in sorted((source / relative).glob("*.java")):
            destination = target / path.relative_to(source)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)
            files[str(path.relative_to(source))] = hashlib.sha256(path.read_bytes()).hexdigest()
    manifest_path.write_text(json.dumps({"source": "Last-Rites", "revision": 1,
                                         "sha256": files}, indent=2) + "\n")
    print("Copied native reporting. Add ReportingSettings and wire the controller on the EDT.")


if __name__ == "__main__":
    main()
