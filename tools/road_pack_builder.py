#!/usr/bin/env python3
"""Build an UNVERIFIED, source-attributed Android road pack from surveyed CSV.

No scraped OSM data or live speed limit is ever silently marked reviewed.
The output is deterministic and suitable for independent QA and version control.
This is not a certified nationwide sign database.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from pathlib import Path

ROAD_KINDS = {
    "DIVIDED_OR_MULTI_LANE_ONEWAY", "TWO_WAY_OR_SINGLE_LANE_ONEWAY",
    "MOTORWAY", "UNKNOWN",
}
ZONES = {"INSIDE", "OUTSIDE", "UNKNOWN"}
SIGNS = {"R_420", "R_421", "P_127", "END_SPEED"}


def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    x = math.radians(lat2 - lat1)
    y = math.radians(lon2 - lon1)
    a = math.sin(x / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(y / 2) ** 2
    return 2 * 6371008.8 * math.asin(math.sqrt(max(0.0, min(1.0, a))))


def coordinate(value: str, latitude: bool) -> float:
    number = float(value)
    if not math.isfinite(number) or abs(number) > (90 if latitude else 180):
        raise ValueError("Tọa độ không hợp lệ")
    return number


def distance_to_segment(lat: float, lon: float, a: list[float], b: list[float]) -> tuple[float, float]:
    scale = math.cos(math.radians(lat)) * 111320
    ax, ay = (a[1] - lon) * scale, (a[0] - lat) * 111195
    bx, by = (b[1] - lon) * scale, (b[0] - lat) * 111195
    dx, dy = bx - ax, by - ay
    length2 = dx * dx + dy * dy
    t = -(ax * dx + ay * dy) / length2 if length2 > 0.001 else 0.0
    u = max(0.0, min(1.0, t))
    return math.hypot(ax + u * dx, ay + u * dy), t


def build_pack(
    segments_csv: list[dict[str, str]],
    signs_csv: list[dict[str, str]],
    *,
    series: str,
    version: int,
    title: str,
    source: str,
    license_name: str,
) -> dict:
    if not all((series.strip(), title.strip(), source.strip(), license_name.strip())):
        raise ValueError("Cần series, title, source, license rõ ràng")
    if not 1 <= version <= 1_000_000_000:
        raise ValueError("version phải là số nguyên dương hợp lệ")
    if len(segments_csv) > 100000 or len(signs_csv) > 25000:
        raise ValueError("Gói vượt giới hạn thiết bị")
    roads: list[dict] = []
    road_ids: set[str] = set()
    for row in segments_csv:
        ident = row["id"].strip()
        src = row["source_ref"].strip()
        if not ident or not src or ident in road_ids:
            raise ValueError("Trùng ID hoặc thiếu source_ref của đoạn đường")
        kind = row["road_kind"].strip()
        zone = row["population_zone"].strip()
        if kind not in ROAD_KINDS or zone not in ZONES:
            raise ValueError("Loại đường / khu dân cư không hợp lệ")
        a = [coordinate(row["from_lat"], True), coordinate(row["from_lon"], False)]
        b = [coordinate(row["to_lat"], True), coordinate(row["to_lon"], False)]
        if not 3.0 <= haversine(*a, *b) <= 2500.0:
            raise ValueError("Chiều dài đoạn đường không hợp lệ")
        road_ids.add(ident)
        roads.append({
            "id": ident, "from": a, "to": b, "roadKind": kind,
            "populationZone": zone, "direction": row["direction"].strip(),
            "reviewed": False, "sourceRef": src,
        })
    indexed = {road["id"]: road for road in roads}
    signs: list[dict] = []
    sign_ids: set[str] = set()
    for row in signs_csv:
        ident, segment_id = row["id"].strip(), row["segment_id"].strip()
        kind, src = row["kind"].strip(), row["source_ref"].strip()
        if not ident or ident in sign_ids or segment_id not in indexed or kind not in SIGNS or not src:
            raise ValueError("Biển trùng ID, sai mã, sai đoạn đường hoặc thiếu source_ref")
        lat, lon = coordinate(row["lat"],True), coordinate(row["lon"],False)
        road = indexed[segment_id]
        distance, progress = distance_to_segment(lat,lon,road["from"],road["to"])
        if distance > 12 or not 0.0 <= progress <= 1.0:
            raise ValueError("Biển báo không nằm đúng đoạn đường")
        limit = row.get("kph","").strip()
        if kind == "P_127":
            if not limit or not 5 <= int(limit) <= 130:
                raise ValueError("P.127 cần trị số tốc độ hợp lệ")
        elif limit:
            raise ValueError("Chỉ P.127 có kph")
        sign = {
            "id": ident, "segmentId": segment_id, "kind": kind,
            "lat": lat, "lon": lon, "direction": row.get("direction","").strip(),
            "sourceRef": src, "reviewed": False, "confidence": 0.0,
        }
        if kind == "P_127":
            sign["kph"] = int(limit)
        signs.append(sign)
        sign_ids.add(ident)
    return {
        "schema": 1, "series": series.strip(), "version": version,
        "title": title.strip(), "source": source.strip(), "license": license_name.strip(),
        "segments": sorted(roads,key=lambda x:x["id"]),
        "speedSigns": [], "trafficSigns": sorted(signs,key=lambda x:x["id"]),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--segments", type=Path, required=True, help="CSV: id,from_lat,from_lon,to_lat,to_lon,road_kind,population_zone,direction,source_ref")
    parser.add_argument("--signs", type=Path, required=True, help="CSV: id,segment_id,kind,lat,lon,kph,direction,source_ref")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--series", required=True)
    parser.add_argument("--version", type=int, required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--license", dest="license_name", required=True)
    args = parser.parse_args()
    with args.segments.open(encoding="utf-8-sig",newline="") as f:
        segments = list(csv.DictReader(f))
    with args.signs.open(encoding="utf-8-sig",newline="") as f:
        signs = list(csv.DictReader(f))
    pack = build_pack(segments,signs,series=args.series,version=args.version,
                      title=args.title,source=args.source,license_name=args.license_name)
    content = (json.dumps(pack,sort_keys=True,ensure_ascii=False,separators=(",",":")) + "\n").encode("utf-8")
    if len(content) > 10_000_000:
        raise ValueError("Gói quá 10 MB")
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_bytes(content)
    digest = hashlib.sha256(content).hexdigest()
    args.output.with_suffix(args.output.suffix + ".sha256").write_text(digest + "\n",encoding="ascii")
    print("Created UNVERIFIED pack: " + str(args.output))
    print("SHA256 (integrity only, not source authentication): " + digest)


if __name__ == "__main__":
    main()
