#!/usr/bin/env python3
"""Fail-closed *structural* survey QA. This does not confer legal approval."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

from tools.road_pack_builder import haversine
from tools.survey_routing_qa import inspect_routing

MAX_SEGMENTS = 100000
MAX_SIGNS = 25000


def inspect_pack(pack: dict) -> dict:
    issues: list[dict[str, str]] = []
    def add(level: str, key: str, description: str) -> None:
        issues.append({"level": level, "key": key, "message": description})

    roads = pack.get("segments", [])
    signs = pack.get("trafficSigns", [])
    if not isinstance(roads, list) or not isinstance(signs, list):
        add("error", "schema", "segments/trafficSigns must be arrays")
        return {"passed": False, "issues": issues, "stats": {}}
    if len(roads) > MAX_SEGMENTS or len(signs) > MAX_SIGNS:
        add("error", "size", "Pack exceeds supported survey record counts")
        return {"passed": False, "issues": issues, "stats": {}}
    ids: set[str] = set()
    by_id: dict[str, dict] = {}
    for road in roads:
        if not isinstance(road, dict):
            add("error", "segment", "Road entry must be an object")
            continue
        ident = str(road.get("id", ""))
        if not ident or ident in ids:
            add("error", ident or "segment", "Missing or duplicate road ID")
        ids.add(ident)
        by_id[ident] = road
        if not str(road.get("sourceRef", "")).strip():
            add("error", ident, "Road is missing traceable sourceRef")
        if road.get("reviewed") is True:
            add("warning", ident, "Reviewed flag is self-reported; independently audit approval")
    seen: set[str] = set()
    indexed: dict[str, list[dict]] = {}
    for sign in signs:
        if not isinstance(sign, dict):
            add("error", "trafficSign", "Sign entry must be an object")
            continue
        ident = str(sign.get("id", ""))
        segment = str(sign.get("segmentId", ""))
        if not ident or ident in seen:
            add("error", ident or "trafficSign", "Missing or duplicate sign ID")
        seen.add(ident)
        if segment not in by_id:
            add("error", ident, "Sign references unknown road ID")
            continue
        if not str(sign.get("sourceRef", "")).strip():
            add("error", ident, "Sign is missing traceable sourceRef")
        if sign.get("reviewed") is True:
            add("warning", ident, "Reviewed sign still needs legal and field verification")
        if sign.get("kind") == "P_127" and (
            type(sign.get("kph")) is not int or not 5 <= sign["kph"] <= 130
        ):
            add("error", ident, "P.127 speed value invalid")
        if sign.get("kind") not in ("R_420", "R_421", "P_127", "END_SPEED"):
            add("error", ident, "Unknown traffic sign kind")
        indexed.setdefault(segment, []).append(sign)

    inspect_routing(pack.get("routing"),by_id,add)

    # Nearby contradictory signs on the same directional segment warrant review.
    comparison_budget = 200_000
    for segment, segment_signs in indexed.items():
        for i, a in enumerate(segment_signs):
            for b in segment_signs[i+1:]:
                comparison_budget -= 1
                if comparison_budget <= 0:
                    add("error","density","Excessive nearby-sign comparisons; split/clean the dataset")
                    break
                if a.get("direction") != b.get("direction"):
                    continue
                try:
                    dist = haversine(float(a["lat"]),float(a["lon"]),
                                     float(b["lat"]),float(b["lon"]))
                except (TypeError,ValueError,KeyError):
                    add("error", segment, "Invalid sign coordinates")
                    continue
                if dist > 8.0:
                    continue
                conflict = (
                    (a.get("kind") == b.get("kind") == "P_127" and a.get("kph") != b.get("kph")) or
                    {a.get("kind"), b.get("kind")} == {"R_420", "R_421"}
                )
                if conflict:
                    add("error", segment,
                        "Contradictory signs within 8m: " +
                        str(a.get("id")) + " vs " + str(b.get("id")))
                elif a.get("kind") == b.get("kind"):
                    add("warning", segment,
                        "Potential duplicate: " + str(a.get("id")) + " and " + str(b.get("id")))
                if len(issues) >= 1000:
                    add("error","issue-limit","QA issue limit exceeded")
                    break
            if comparison_budget <= 0 or len(issues) >= 1000:
                break
        if comparison_budget <= 0 or len(issues) >= 1000:
            break

    checks = {
        "segment_count": len(roads),
        "traffic_sign_count": len(signs),
        "routing_present": isinstance(pack.get("routing"), dict),
        "errors": sum(x["level"] == "error" for x in issues),
        "warnings": sum(x["level"] == "warning" for x in issues),
    }
    return {"passed": checks["errors"] == 0, "issues": issues, "stats": checks,
            "disclaimer": "Structural checks ONLY; not authoritative field or legal verification."}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    value = json.loads(args.pack.read_text(encoding="utf-8"))
    result = inspect_pack(value)
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",
                           encoding="utf-8")
    print("QA report: " + str(args.report) + " (passed=" + str(result["passed"]) + ")")
    if not result["passed"]:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
