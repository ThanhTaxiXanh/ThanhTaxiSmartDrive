#!/usr/bin/env python3
"""Phase 1 architecture/documentation gate. Not road, legal or Android Auto certification."""
from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

BASE = Path("docs/phase1")
REQUIRED = (
    "README.md", "PRD.md", "FEASIBILITY.md", "ARCHITECTURE.md",
    "DATA-MODEL.md", "WIREFRAMES.md", "SDK-AND-LICENSES.md",
    "ADRS.md", "RISK-AND-TEST-GATES.md", "SKILL-REVIEW.md",
    "REQUIREMENTS.csv", "PHASE1-REVIEW.json",
)
PHASES = set("123456")
STATUSES = {"designed", "implemented", "partial", "not_started"}


def check_repository(root: Path) -> list[str]:
    problems: list[str] = []
    base = root / BASE
    for file in REQUIRED:
        path = base / file
        if not path.is_file():
            problems.append(f"Missing Phase 1 deliverable: {path}")
        elif path.stat().st_size < 100:
            problems.append(f"Suspiciously empty Phase 1 deliverable: {path}")
    if problems:
        return problems

    review = json.loads((base / "PHASE1-REVIEW.json").read_text(encoding="utf-8"))
    if review.get("phase") != 1:
        problems.append("Review phase must be 1")
    if review.get("gate") != "architecture_documentation_complete_pending_CI":
        problems.append("Unexpected Phase 1 gate statement")
    declared = set(review.get("docs", []))
    expected = {str(BASE / f) for f in REQUIRED if f != "PHASE1-REVIEW.json"}
    if not expected.issubset(declared):
        problems.append(f"Phase 1 review must enumerate all deliverables: {sorted(expected-declared)}")
    app_claims = review.get("current_app_claims", {})
    for not_implemented in ("android_auto_carappservice","legal_speed_certification","completed_national_sign_data"):
        if app_claims.get(not_implemented) is not False:
            problems.append(f"Cannot certify incomplete capability: {not_implemented}")

    with (base / "REQUIREMENTS.csv").open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream)
        wanted = {"id","source_section","requirement","phase","platform",
                  "current_status","evidence","next_gate"}
        if set(reader.fieldnames or []) != wanted:
            problems.append("Requirements CSV header mismatch")
            return problems
        items = list(reader)
    if len(items) < 40:
        problems.append(f"Master Prompt coverage too small: only {len(items)} requirements")
    seen: set[str] = set()
    for row in items:
        ident = row["id"]
        if not ident or ident in seen:
            problems.append(f"Empty or duplicate requirement ID: {ident}")
        seen.add(ident)
        if not all(v.strip() for v in row.values() if v is not None):
            problems.append(f"Incomplete requirement trace: {ident}")
        if row["phase"] not in PHASES:
            problems.append(f"Invalid phase for {ident}")
        if row["current_status"] not in STATUSES:
            problems.append(f"Invalid implementation status for {ident}")
        if row["platform"] == "ANDROID_AUTO" and row["current_status"] == "implemented":
            problems.append(f"Android Auto falsely marked complete: {ident}")
    ids = {row["id"] for row in items}
    key_requirements = {"MAP-01","SPD-01","SIG-01","DASH-01","CAL-01",
                        "QR-01","MEDIA-01","OFF-01","AI-01",
                        "AUTO-01","OPS-01","OPS-02"}
    if not key_requirements.issubset(ids):
        problems.append(f"Missing requirements: {sorted(key_requirements - ids)}")
    phases = {row["phase"] for row in items}
    if phases != PHASES:
        problems.append(f"Requirements must trace all six phases: {phases}")

    feasibility = (base / "FEASIBILITY.md").read_text(encoding="utf-8")
    if not all(s in feasibility for s in ("Fermata", "MapLibre", "CarAppService",
                                           "70/30", "GPL-3.0", "Android Auto")):
        problems.append("Feasibility study does not resolve core sources/platform split")
    wireframes = (base / "WIREFRAMES.md").read_text(encoding="utf-8")
    if not all(s in wireframes for s in ("70%", "25%", "55%", "20%", "Android Auto")):
        problems.append("Missing four-region and template wireframe distinction")
    roadmap = (root / "docs/ROADMAP.md").read_text(encoding="utf-8")
    if "Phase 1" not in roadmap or "docs/phase1/README.md" not in roadmap:
        problems.append("Roadmap must link the Phase 1 acceptance packet")
    ci = (root / ".github/workflows/android.yml").read_text(encoding="utf-8")
    if "python3 tools/phase1_check.py" not in ci:
        problems.append("CI must enforce Phase 1 architecture guard")

    return problems


def main() -> int:
    issues = check_repository(Path(__file__).resolve().parent.parent)
    for issue in issues:
        print("PHASE1 FAIL:", issue, file=sys.stderr)
    if issues:
        return 2
    print("PHASE1 PASS: documented feasibility, architecture, 6-phase traceability and guard.")
    print("This is NOT proof of Android Auto, legal speed limits or device/road validation.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
