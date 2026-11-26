"""Fail closed if local school data becomes eligible for Android OS backup."""
import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
DOMAINS = set("""root file database sharedpref external device_root device_file
device_database device_sharedpref""".split())


def require_exclusions(section: ET.Element) -> None:
    denied = set()
    for entry in section:
        if entry.tag != "exclude" or set(entry.attrib) != {"domain", "path"}:
            raise ValueError(f"{section.tag}: expected only unconditional exclusions")
        domain = entry.attrib["domain"]
        if entry.attrib["path"] != "." or domain in denied:
            raise ValueError(f"{section.tag}: incomplete or duplicate exclusion: {domain}")
        denied.add(domain)
    if denied != DOMAINS:
        raise ValueError(f"{section.tag}: missing {sorted(DOMAINS-denied)}, unknown {sorted(denied-DOMAINS)}")


def check(source: Path) -> None:
    app = ET.parse(source / "AndroidManifest.xml").getroot().find("application")
    expected = {"allowBackup": "false", "fullBackupContent": "@xml/backup_rules",
                "dataExtractionRules": "@xml/data_extraction_rules"}
    if app is None or any(app.get(ANDROID + key) != value for key, value in expected.items()):
        raise ValueError("manifest must disable backup and link both deny policies")
    if app.get(ANDROID + "backupAgent") is not None:
        raise ValueError("unexpected custom backup agent")
    legacy = ET.parse(source / "res/xml/backup_rules.xml").getroot()
    if legacy.tag != "full-backup-content":
        raise ValueError("invalid legacy policy")
    require_exclusions(legacy)
    current = ET.parse(source / "res/xml/data_extraction_rules.xml").getroot()
    if current.tag != "data-extraction-rules" or [node.tag for node in current] != [
        "cloud-backup", "device-transfer"
    ]:
        raise ValueError("expected explicit cloud and direct transfer policy, no cross-platform opt-in")
    for section in current:
        require_exclusions(section)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=Path("app/src/main"))
    args = parser.parse_args()
    try:
        check(args.source)
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"Android backup policy failed: {error}", file=sys.stderr)
        raise SystemExit(1)
    print("Android backup policy passed: legacy, cloud, and direct transfer deny all nine domains.")
