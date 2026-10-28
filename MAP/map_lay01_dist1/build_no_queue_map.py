"""Patch the user's latest Fleet export offline; no Fleet/robot connection."""
from collections import deque
from copy import deepcopy
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "map_lay01_dist1_o4h.json"
OUTPUT = ROOT / "map_lay01_dist1_no_queue_v3.json"
REPORT = ROOT / "map_lay01_dist1_no_queue_v3_validation.json"


def shortest_path(adjacency, start, goal, blocked=frozenset()):
    pending = deque([(start, [start])])
    seen = {start}
    while pending:
        node, path = pending.popleft()
        if node == goal:
            return path
        for next_node in sorted(adjacency[node], key=int):
            if next_node not in seen and next_node not in blocked:
                seen.add(next_node)
                pending.append((next_node, path + [next_node]))
    return None


def build():
    original_bytes = SOURCE.read_bytes()
    original = json.loads(original_bytes.decode("utf-8-sig"))
    data = deepcopy(original)
    assert len(data["floorList"]) == 1
    floor = data["floorList"][0]
    source_floor = original["floorList"][0]
    nodes = {str(n["nodeLabel"]): n for n in floor["nodeList"]}
    source_nodes = {str(n["nodeLabel"]): n for n in source_floor["nodeList"]}
    removed = []

    # QUEUING_POINT (8) is removed; coordinates and graph junctions remain.
    for label, node in nodes.items():
        for function in node.get("functionList", []):
            if function["functionType"] == 8:
                removed.append({"node": label,
                                "previous_workstation": function.get("relativeNodeLabel")})
        if any(fn["functionType"] == 8 for fn in node.get("functionList", [])):
            node["functionList"] = [fn for fn in node["functionList"]
                                    if fn["functionType"] != 8]

    # Match the syntax in the live export (94 -> 67 already used 0,180).
    # Each directed edge permits driving either forward or backward.
    changed_edges = []
    for edge in floor["edgeList"]:
        if edge["orientation"] != "0,180":
            changed_edges.append([str(edge["beginNodeLabel"]), str(edge["endNodeLabel"])])
            edge["orientation"] = "0,180"

    # Verify the complete diff against an independently constructed allowlist.
    expected = deepcopy(original)
    for node in expected["floorList"][0]["nodeList"]:
        if any(fn["functionType"] == 8 for fn in node.get("functionList", [])):
            node["functionList"] = [fn for fn in node["functionList"]
                                    if fn["functionType"] != 8]
    for edge in expected["floorList"][0]["edgeList"]:
        edge["orientation"] = "0,180"
    assert data == expected, "Unexpected unrelated change"

    assert len(nodes) == len(floor["nodeList"]) == 27
    assert len({n["nodeNumber"] for n in nodes.values()}) == 27
    assert len({n["nodeUuid"] for n in nodes.values()}) == 27
    assert len({(n["xCoordinate"], n["yCoordinate"]) for n in nodes.values()}) == 27
    assert data["mapCode"] == original["mapCode"] == "lay01"
    assert floor["floorNumber"] == source_floor["floorNumber"] == "dist1"
    assert len(removed) == 10
    assert all(fn["functionType"] != 8 for n in nodes.values()
               for fn in n.get("functionList", []))
    for label, node in nodes.items():
        for key in ("nodeNumber", "nodeUuid", "nodeType", "xCoordinate", "yCoordinate"):
            assert node[key] == source_nodes[label][key]
        for fn in node.get("functionList", []):
            if fn.get("relativeNodeLabel"):
                assert str(fn["relativeNodeLabel"]) in nodes

    def functions_of_type(group, kind):
        return {label: [fn for fn in n.get("functionList", []) if fn["functionType"] == kind]
                for label, n in group.items()
                if any(fn["functionType"] == kind for fn in n.get("functionList", []))}

    assert len(functions_of_type(nodes, 1)) == 10
    for kind in (1, 4, 10):
        assert functions_of_type(nodes, kind) == functions_of_type(source_nodes, kind)
    assert len(functions_of_type(nodes, 4)) == 2
    assert len(functions_of_type(nodes, 10)) == 2

    pairs = {(str(e["beginNodeLabel"]), str(e["endNodeLabel"]))
             for e in floor["edgeList"]}
    assert len(pairs) == len(floor["edgeList"]) == 68
    assert pairs == {(str(e["beginNodeLabel"]), str(e["endNodeLabel"]))
                     for e in source_floor["edgeList"]}
    adjacency = {label: set() for label in nodes}
    for edge in floor["edgeList"]:
        a, b = str(edge["beginNodeLabel"]), str(edge["endNodeLabel"])
        assert a in nodes and b in nodes and (b, a) in pairs
        dx = abs(nodes[a]["xCoordinate"] - nodes[b]["xCoordinate"])
        dy = abs(nodes[a]["yCoordinate"] - nodes[b]["yCoordinate"])
        assert (dx, dy) in ((2, 0), (0, 2))
        assert math.isclose(math.hypot(dx, dy), edge["edgeLength"])
        assert edge["orientation"] == "0,180"
        adjacency[a].add(b)
    for a, b in (("62", "88"), ("64", "87")):
        assert (a, b) in pairs and (b, a) in pairs

    all_pairs_checked = 0
    for a in nodes:
        for b in nodes:
            if a != b:
                assert shortest_path(adjacency, a, b), (a, b)
                all_pairs_checked += 1
    assert all_pairs_checked == 702
    examples = {}
    for a, b in (("89", "97"), ("97", "89"), ("84", "86"), ("86", "84"),
                 ("94", "96"), ("96", "94"), ("60", "61")):
        examples[f"{a}->{b}"] = shortest_path(adjacency, a, b)
    for a, b in (("89", "97"), ("84", "86"), ("94", "96")):
        assert shortest_path(adjacency, a, b, {"60", "63", "66"})

    OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    assert json.loads(OUTPUT.read_text(encoding="utf-8")) == data
    assert SOURCE.read_bytes() == original_bytes
    report = {
        "source": SOURCE.name,
        "source_sha256": hashlib.sha256(original_bytes).hexdigest(),
        "output": OUTPUT.name,
        "output_sha256": hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
        "map_code": data["mapCode"], "district": floor["floorNumber"],
        "nodes": 27, "directed_edges": 68, "spacing_m": 2,
        "removed_queue_functions": removed,
        "retained_workstations": sorted(functions_of_type(nodes, 1), key=int),
        "charging_entries": sorted(functions_of_type(nodes, 4), key=int),
        "charging_docks": sorted(functions_of_type(nodes, 10), key=int),
        "updated_direction_edges": len(changed_edges),
        "all_edges_orientation": "0,180",
        "static_directed_path_checks": all_pairs_checked,
        "example_static_paths": examples,
        "checks": ["only queue functions and edge orientations changed",
                   "original bytes unchanged", "identities and coordinates unchanged",
                   "all directions have a reverse edge", "all edges axis aligned and exactly 2m",
                   "all function references exist", "charging links and settings preserved",
                   "no QUEUING_POINT remains", "station routes bypass occupied center nodes",
                   "JSON round trip"],
        "fleet_import_verified": False,
        "fleet_motion_verified": False,
        "limitations": [
            "Static graph checks do not execute the Fleet planner or collision avoidance.",
            "Existing suspended missions and robot/container runtime state are not changed.",
            "WCS workflow/event configs, areas, and server queue policies are not in this map export.",
            "Physical floor geometry and payload clearance are not qualified; virtual simulation only.",
            "Predecessor waiting/queue behavior is intentionally removed per the latest request."]}
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(OUTPUT), "queue_functions_removed": len(removed),
                      "edge_directions_updated": len(changed_edges),
                      "directed_paths_checked": all_pairs_checked,
                      "example_paths": examples}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    build()
