"""Structural QA for user-supplied surveyed, directed routing topology.

Never treats a geometric road crossing or self-declared reviewed=true as a
certified legal turn. The checks are stricter than the raw OSM road geometry.
"""
import math
from tools.road_pack_builder import haversine

def inspect_routing(routing, roads, add):
    if routing is None:
        return
    if not isinstance(routing,dict):
        add("error","routing","Expected routing object")
        return
    nodes=routing.get("nodes",[])
    edges=routing.get("edges",[])
    turns=routing.get("allowedTurns",[])
    if not all(isinstance(x,list) for x in (nodes,edges,turns)):
        add("error","routing","nodes/edges/allowedTurns must all be arrays")
        return
    if len(nodes)>20000 or len(edges)>40000 or len(turns)>80000:
        add("error","routing","Graph too large")
        return
    ns={};es={}
    for n in nodes:
        if not isinstance(n,dict):
            add("error","node","Non-object routing node")
            continue
        ident=n.get("id")
        if not isinstance(ident,str) or not ident or ident in ns:
            add("error","node","Duplicate/missing node ID")
            continue
        name=n.get("displayName")
        if name is not None and (
            not isinstance(name,str) or len(name.strip())>100 or
            any(ord(c)<32 or ord(c)==127 for c in name)
        ):
            add("error",ident,"Survey point displayName must be a short clean string")
        ns[ident]=n
        try:
            lat=float(n["lat"]);lon=float(n["lon"])
            if not math.isfinite(lat) or not math.isfinite(lon) or abs(lat)>90 or abs(lon)>180:
                raise ValueError()
        except (TypeError,ValueError,KeyError):
            add("error",ident,"Invalid node coordinates")
    for e in edges:
        if not isinstance(e,dict):
            add("error","edge","Non-object routing edge")
            continue
        ident=e.get("id")
        if not isinstance(ident,str) or not ident or ident in es:
            add("error","edge","Duplicate/missing edge ID")
            continue
        es[ident]=e
        a,b=e.get("fromNode"),e.get("toNode")
        segmentId=e.get("segmentId")
        segment=roads.get(segmentId) if isinstance(segmentId,str) else None
        if not isinstance(a,str) or not isinstance(b,str) or a not in ns or b not in ns or a==b:
            add("error",ident,"Directed edge has nonexistent or identical endpoints")
        if segment is None:
            add("error",ident,"Directed edge references unknown surveyed road")
        if e.get("direction") not in ("forward","reverse"):
            add("error",ident,"Direction must be forward or reverse")
        if e.get("reviewed") is True:
            if not str(e.get("sourceRef","")).strip():
                add("error",ident,"Reviewed edge lacks a survey sourceRef")
            if segment is not None and segment.get("reviewed") is not True:
                add("error",ident,"Reviewed routing edge uses an unreviewed segment")
        if isinstance(a,str) and isinstance(b,str) and a in ns and b in ns and segment is not None and e.get("direction") in ("forward","reverse"):
            try:
                beginning=segment["from"] if e["direction"]=="forward" else segment["to"]
                ending=segment["to"] if e["direction"]=="forward" else segment["from"]
                aPos=ns[a];bPos=ns[b]
                d1=haversine(float(aPos["lat"]),float(aPos["lon"]),*beginning)
                d2=haversine(float(bPos["lat"]),float(bPos["lon"]),*ending)
                direct=haversine(float(aPos["lat"]),float(aPos["lon"]),
                                 float(bPos["lat"]),float(bPos["lon"]))
                dist=float(e["distanceMeters"])
                if d1>8 or d2>8 or direct<=2 or not math.isfinite(dist) or not direct*.9<=dist<=direct*1.5:
                    add("error",ident,"Edge coordinates/length do not fit segment geometry")
            except (KeyError,TypeError,ValueError,IndexError):
                add("error",ident,"Invalid directed-edge geometry")
    seen=set()
    for t in turns:
        if not isinstance(t,dict):
            add("error","turn","Non-object allowed turn")
            continue
        src,dst,via=t.get("fromEdge"),t.get("toEdge"),t.get("viaNode")
        ident=(str(src),str(via),str(dst))
        if ident in seen:
            add("error","turn","Duplicate directed turn")
        seen.add(ident)
        before=es.get(src) if isinstance(src,str) else None
        after=es.get(dst) if isinstance(dst,str) else None
        if before is None or after is None or not isinstance(via,str) or via not in ns or (
            before is not None and after is not None and
            (before.get("toNode")!=via or after.get("fromNode")!=via)):
            add("error","turn","Turn cannot traverse through specified shared node")
        if t.get("reviewed") is True:
            if not str(t.get("sourceRef","")).strip():
                add("error","turn","Reviewed turn lacks survey sourceRef")
            if before is not None and after is not None and (
                before.get("reviewed") is not True or after.get("reviewed") is not True):
                add("error","turn","Reviewed turn uses unreviewed edge")
