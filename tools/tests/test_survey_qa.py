import unittest
from tools.survey_qa import inspect_pack

def sample():
    return {"schema":1,"segments":[{
        "id":"road","from":[21.0,106.0],"to":[21.001,106.0],
        "sourceRef":"surveyor-archive:123","reviewed":False}],
        "trafficSigns":[{
            "id":"signA","segmentId":"road","kind":"P_127","kph":40,
            "lat":21.0005,"lon":106.0,"direction":"north",
            "sourceRef":"survey-photo:A","reviewed":False}]}


class SurveyQaTests(unittest.TestCase):
    def test_allows_complete_structural_data_but_no_legal_claim(self):
        result=inspect_pack(sample())
        self.assertTrue(result["passed"])
        self.assertIn("not authoritative",result["disclaimer"])

    def test_rejects_unknown_road(self):
        pack=sample()
        pack["trafficSigns"][0]["segmentId"]="missing"
        self.assertFalse(inspect_pack(pack)["passed"])

    def test_rejects_conflicting_nearby_p127(self):
        pack=sample()
        second=dict(pack["trafficSigns"][0],id="signB",kph=50,lat=21.000503)
        pack["trafficSigns"].append(second)
        result=inspect_pack(pack)
        self.assertFalse(result["passed"])
        self.assertTrue(any("Contradictory" in x["message"] for x in result["issues"]))

    def test_warns_about_duplicate_signs(self):
        pack=sample()
        pack["trafficSigns"].append(dict(pack["trafficSigns"][0],id="signB"))
        result=inspect_pack(pack)
        self.assertTrue(result["passed"])
        self.assertGreater(result["stats"]["warnings"],0)

    def test_requires_source_ref_per_record(self):
        pack=sample();pack["trafficSigns"][0]["sourceRef"]=""
        self.assertFalse(inspect_pack(pack)["passed"])

    def test_user_reviewed_flag_not_government_approval(self):
        pack=sample();pack["trafficSigns"][0]["reviewed"]=True
        result=inspect_pack(pack)
        self.assertTrue(result["passed"])
        self.assertTrue(any("legal" in x["message"] for x in result["issues"]))


if __name__=="__main__":
    unittest.main()
