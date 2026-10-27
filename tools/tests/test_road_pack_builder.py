import unittest
from tools.road_pack_builder import build_pack

SEGMENTS = [{
    "id":"synthetic-A","from_lat":"21.0","from_lon":"106.0",
    "to_lat":"21.001","to_lon":"106.0",
    "road_kind":"TWO_WAY_OR_SINGLE_LANE_ONEWAY","population_zone":"UNKNOWN",
    "direction":"north","source_ref":"illustration-only",
}]
SIGNS = [{
    "id":"example-p127","segment_id":"synthetic-A","kind":"P_127",
    "lat":"21.0005","lon":"106.0","kph":"40",
    "direction":"north","source_ref":"illustration-only",
}]
KW = dict(series="demo",version=1,title="Synthetic data DO NOT DRIVE",source="TEST",license_name="demo")


class PackBuilderTests(unittest.TestCase):
    def test_unverified_by_default(self):
        pack = build_pack(SEGMENTS,SIGNS,**KW)
        self.assertFalse(pack["segments"][0]["reviewed"])
        self.assertFalse(pack["trafficSigns"][0]["reviewed"])
        self.assertEqual(0.0,pack["trafficSigns"][0]["confidence"])
        self.assertEqual(40,pack["trafficSigns"][0]["kph"])

    def test_wrong_segment_rejected(self):
        bad = dict(SIGNS[0],segment_id="other")
        with self.assertRaises(ValueError):
            build_pack(SEGMENTS,[bad],**KW)

    def test_off_road_sign_rejected(self):
        bad = dict(SIGNS[0],lon="106.1")
        with self.assertRaises(ValueError):
            build_pack(SEGMENTS,[bad],**KW)

    def test_duplicate_sign_rejected(self):
        with self.assertRaises(ValueError):
            build_pack(SEGMENTS,SIGNS*2,**KW)

    def test_missing_provenance_rejected(self):
        with self.assertRaises(ValueError):
            build_pack([dict(SEGMENTS[0],source_ref="")],SIGNS,**KW)

    def test_gibberish_speed_limit_rejected(self):
        with self.assertRaises(ValueError):
            build_pack(SEGMENTS,[dict(SIGNS[0],kph="141")],**KW)

    def test_directional_fields_kept_and_deterministic(self):
        self.assertEqual(build_pack(SEGMENTS,SIGNS,**KW),build_pack(SEGMENTS,SIGNS,**KW))


if __name__ == "__main__":
    unittest.main()
