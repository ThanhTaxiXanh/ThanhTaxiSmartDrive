import unittest
from tools.survey_routing_qa import inspect_routing

def sample():
    roads={
        "s1":{"from":[21.0,106.0],"to":[21.001,106.0],"reviewed":True},
        "s2":{"from":[21.001,106.0],"to":[21.001,106.001],"reviewed":True}
    }
    routing={
        "nodes":[{"id":"A","lat":21.0,"lon":106.0},
                 {"id":"B","lat":21.001,"lon":106.0},
                 {"id":"C","lat":21.001,"lon":106.001}],
        "edges":[
            {"id":"ab","fromNode":"A","toNode":"B","segmentId":"s1",
             "direction":"forward","distanceMeters":111.2,"reviewed":True,"sourceRef":"field-a"},
            {"id":"bc","fromNode":"B","toNode":"C","segmentId":"s2",
             "direction":"forward","distanceMeters":104.5,"reviewed":True,"sourceRef":"field-b"}
        ],
        "allowedTurns":[{"fromEdge":"ab","viaNode":"B","toEdge":"bc",
                         "reviewed":True,"sourceRef":"turn-survey"}]
    }
    return routing,roads

def errors(routing,roads):
    found=[]
    inspect_routing(routing,roads,lambda level,key,message:found.append((level,key,message)))
    return found

class SurveyRoutingQaTests(unittest.TestCase):
    def test_valid_structural_route(self):
        g,r=sample()
        self.assertEqual([],errors(g,r))
    def test_turn_does_not_exist_through_overpass(self):
        g,r=sample()
        g["allowedTurns"][0]["viaNode"]="C"
        self.assertTrue(errors(g,r))
    def test_survey_restrictions_on_edges_and_turns(self):
        g,r=sample()
        g["edges"][0]["restrictions"]={"allowedVehicles":["TAXI"],
            "blockedWindows":[{"days":[1,2,3,4,5],"startMinute":420,"endMinute":540}]}
        g["allowedTurns"][0]["restrictions"]={"allowedVehicles":["TAXI"]}
        self.assertEqual([],errors(g,r))
        g["edges"][0]["restrictions"]["blockedWindows"][0]["endMinute"]=420
        self.assertTrue(errors(g,r))
        g["edges"][0]["restrictions"]["blockedWindows"][0]["endMinute"]=540
        g["edges"][0]["restrictions"]["allowedVehicles"]=["TAXI","UNSUPPORTED"]
        self.assertTrue(errors(g,r))

    def test_restrictions_are_never_silently_accepted_without_provenance(self):
        g,r=sample()
        g["allowedTurns"][0]["restrictions"]={"allowedVehicles":["TAXI"]}
        g["allowedTurns"][0]["sourceRef"]=""
        self.assertTrue(errors(g,r))
        g,r=sample()
        g["edges"][0]["restrictions"]={"brokenKey":"untrusted"}
        self.assertTrue(errors(g,r))
        g,r=sample()
        g["edges"][0]["restrictions"]={"blockedWindows":[
            {"days":[1,2],"startMinute":1320,"endMinute":120}]}
        self.assertEqual([],errors(g,r))
        g["edges"][0]["restrictions"]["blockedWindows"][0]["days"]=[0]
        self.assertTrue(errors(g,r))

    def test_display_name_needs_clean_user_supplied_metadata(self):
        g,r=sample()
        g["nodes"][0]["displayName"]="Bến xe"
        self.assertEqual([],errors(g,r))
        g["nodes"][0]["displayName"]="x"*120
        self.assertTrue(errors(g,r))
        g["nodes"][0]["displayName"]=1234
        self.assertTrue(errors(g,r))
        g["nodes"][0]["displayName"]="Trạm\nGiả lập"
        self.assertTrue(errors(g,r))

    def test_missing_source_ref(self):
        g,r=sample()
        g["allowedTurns"][0]["sourceRef"]=""
        self.assertTrue(any("sourceRef" in e[2] for e in errors(g,r)))
    def test_reverse_edge_cannot_use_forward_endpoints(self):
        g,r=sample()
        g["edges"][0]["direction"]="reverse"
        self.assertTrue(errors(g,r))
    def test_malformed_values_report_errors_instead_of_crashing(self):
        g,r=sample()
        g["edges"][0]["fromNode"]=["bad","value"]
        self.assertTrue(errors(g,r))
        g,r=sample()
        g["allowedTurns"][0]["fromEdge"]=["bad"]
        self.assertTrue(errors(g,r))
    def test_self_reported_turn_with_unreviewed_edge_rejected(self):
        g,r=sample()
        g["edges"][0]["reviewed"]=False
        self.assertTrue(any("unreviewed" in e[2] for e in errors(g,r)))

if __name__ == "__main__":
    unittest.main()
