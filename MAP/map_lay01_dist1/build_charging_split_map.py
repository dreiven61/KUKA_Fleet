"""Separate charger approach functions from the main traffic grid, offline.

Source is the user's latest native export. Existing workstation/dock identities
and positions are preserved. No Fleet API, robot command or live state is used.
"""
from collections import deque
from copy import deepcopy
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "map_lay01_dist1_89j.json"
OUTPUT = ROOT / "map_lay01_dist1_charging_split_v4.json"
REPORT = ROOT / "map_lay01_dist1_charging_split_v4_validation.json"
APPROACHES = (("62", "99", "88"), ("64", "100", "87"))


def route(adjacency, start, goal, blocked=frozenset()):
    queue = deque([(start, [start])])
    seen = {start}
    while queue:
        node, path = queue.popleft()
        if node == goal:
            return path
        for neighbor in sorted(adjacency[node], key=int):
            if neighbor not in seen and neighbor not in blocked:
                seen.add(neighbor)
                queue.append((neighbor, path + [neighbor]))
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
    assert len(nodes) == len(floor["nodeList"]) == 27
    assert not any(fn["functionType"] == 8 for n in nodes.values()
                   for fn in n.get("functionList", []))
    source_edges = {(str(e["beginNodeLabel"]), str(e["endNodeLabel"])): e
                    for e in source_floor["edgeList"]}
    assert len(source_edges) == 68
    removed_pairs = set()
    added_pairs = set()

    for main, entry, dock in APPROACHES:
        assert entry not in nodes
        functions = [fn for fn in nodes[main].get("functionList", [])
                     if fn["functionType"] == 4]
        assert len(functions) == 1
        entry_function = deepcopy(functions[0])
        assert str(entry_function["relativeNodeLabel"]) == dock
        assert nodes[main]["nodeUuid"] == f"lay01-dist1-{main}"
        nodes[main]["functionList"] = [fn for fn in nodes[main].get("functionList", [])
                                       if fn["functionType"] != 4]

        # Ordinary topological node properties are copied from a common point.
        new_node = deepcopy(nodes["63"])
        new_node.update(nodeLabel=entry, nodeNumber=int(entry),
                        nodeUuid=f"lay01-dist1-{entry}",
                        xCoordinate=(nodes[main]["xCoordinate"] + nodes[dock]["xCoordinate"]) / 2,
                        yCoordinate=(nodes[main]["yCoordinate"] + nodes[dock]["yCoordinate"]) / 2,
                        foreignCode="", idleParkingSupport=0,
                        functionList=[entry_function])
        floor["nodeList"].append(new_node)
        nodes[entry] = new_node

        dock_functions = [fn for fn in nodes[dock].get("functionList", [])
                          if fn["functionType"] == 10]
        assert len(dock_functions) == 1
        assert str(dock_functions[0]["relativeNodeLabel"]) == main
        dock_functions[0]["relativeNodeLabel"] = entry
        # Keep the original opaque device ID, model, heading and dock settings.
        assert entry_function["deviceId"] == dock_functions[0]["deviceId"]
        assert entry_function["angle"] == dock_functions[0]["angle"]

        for a, b in ((main, dock), (dock, main)):
            original_edge = source_edges[(a, b)]
            removed_pairs.add((a, b))
            for begin, end in ((a, entry), (entry, b)):
                edge = deepcopy(original_edge)
                edge.update(beginNodeLabel=begin, endNodeLabel=end, edgeLength=1)
                floor["edgeList"].append(edge)
                added_pairs.add((begin, end))

    floor["edgeList"] = [e for e in floor["edgeList"]
                         if (str(e["beginNodeLabel"]), str(e["endNodeLabel"])) not in removed_pairs]

    # Verify identities, geometry, graph integrity and unrelated field preservation.
    assert data["mapCode"] == original["mapCode"] == "lay01"
    for key in source_floor:
        if key not in ("nodeList", "edgeList"):
            assert floor[key] == source_floor[key]
    assert len(nodes) == len(floor["nodeList"]) == 29
    assert len({n["nodeUuid"] for n in nodes.values()}) == 29
    assert len({n["nodeNumber"] for n in nodes.values()}) == 29
    assert len({(n["xCoordinate"], n["yCoordinate"]) for n in nodes.values()}) == 29
    for label, source_node in source_nodes.items():
        expected = deepcopy(source_node)
        if label in {"62", "64"}:
            expected["functionList"] = [fn for fn in expected.get("functionList", [])
                                        if fn["functionType"] != 4]
        if label in {"88", "87"}:
            replacement = "99" if label == "88" else "100"
            for fn in expected.get("functionList", []):
                if fn["functionType"] == 10:
                    fn["relativeNodeLabel"] = replacement
        assert nodes[label] == expected, ("unrelated node change", label)

    adjacency = {label: set() for label in nodes}
    pairs = set()
    lengths = {}
    for edge in floor["edgeList"]:
        a, b = str(edge["beginNodeLabel"]), str(edge["endNodeLabel"])
        assert a in nodes and b in nodes and (a, b) not in pairs
        pairs.add((a, b))
        dx = abs(nodes[a]["xCoordinate"] - nodes[b]["xCoordinate"])
        dy = abs(nodes[a]["yCoordinate"] - nodes[b]["yCoordinate"])
        distance = math.hypot(dx, dy)
        assert (dx == 0) != (dy == 0)
        assert math.isclose(edge["edgeLength"], distance)
        assert edge["orientation"] == "0,180"
        assert distance == (1 if (a, b) in added_pairs else 2)
        if (a, b) not in added_pairs:
            assert edge == source_edges[(a, b)], ("unrelated edge change", a, b)
        adjacency[a].add(b)
        lengths[(a, b)] = distance
    assert len(pairs) == len(floor["edgeList"]) == 72
    assert pairs == (set(source_edges) - removed_pairs) | added_pairs
    assert all((b, a) in pairs for a, b in pairs)
    assert all(not nodes[label].get("functionList") for label in ("62", "64"))

    by_function = {}
    for label, node in nodes.items():
        for fn in node.get("functionList", []):
            by_function.setdefault(fn["functionType"], []).append(label)
            if fn.get("relativeNodeLabel"):
                assert str(fn["relativeNodeLabel"]) in nodes
    assert len(by_function[1]) == 10
    assert set(by_function[4]) == {"99", "100"}
    assert set(by_function[10]) == {"88", "87"}
    assert 8 not in by_function
    for main, entry, dock in APPROACHES:
        assert adjacency[entry] == {main, dock}
        assert adjacency[dock] == {entry}
        assert nodes[entry]["functionList"][0]["relativeNodeLabel"] == dock
        dock_function = next(fn for fn in nodes[dock]["functionList"] if fn["functionType"] == 10)
        assert dock_function["relativeNodeLabel"] == entry
        assert route(adjacency, main, dock) == [main, entry, dock]
        assert lengths[(main, entry)] + lengths[(entry, dock)] == 2

    all_pairs = 0
    for start in nodes:
        for end in nodes:
            if start != end:
                assert route(adjacency, start, end), (start, end)
                all_pairs += 1
    assert all_pairs == 812

    business_paths = {}
    for start, end, expected_length in (("89", "97", 12), ("97", "89", 12),
                                        ("84", "86", 12), ("86", "84", 12),
                                        ("94", "96", 8), ("96", "94", 8)):
        path = route(adjacency, start, end, {"99", "100", "87", "88"})
        assert path is not None
        distance = sum(lengths[pair] for pair in zip(path, path[1:]))
        assert distance == expected_length
        business_paths[f"{start}->{end}"] = {"nodes": path, "distance_m": distance}

    OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    assert json.loads(OUTPUT.read_text(encoding="utf-8")) == data
    assert SOURCE.read_bytes() == original_bytes
    report = {
        "source": SOURCE.name, "source_sha256": hashlib.sha256(original_bytes).hexdigest(),
        "output": OUTPUT.name, "output_sha256": hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
        "node_count": 29, "directed_edge_count": 72,
        "main_grid_spacing_m": 2, "charging_approach_segment_m": 1,
        "existing_node_identities_and_positions_preserved": True,
        "queue_function_count": 0,
        "normal_main_nodes": ["62", "64"],
        "charging_branches": [["62", "99", "88"], ["64", "100", "87"]],
        "charging_device_ids_and_headings_preserved": True,
        "directed_edge_length_counts": {"2m": 64, "1m": 8},
        "static_directional_path_checks": all_pairs,
        "business_paths_without_any_charging_nodes": business_paths,
        "native_import_verified": False, "live_routing_verified": False,
        "limits": [
            "Charging-entry points cannot be generic Move targets in the observed Fleet UI.",
            "That UI validation alone does not prove transit through an entry is prohibited.",
            "This patch removes charging entry functions from the main grid; actual detour reduction needs live verification.",
            "The WCS logs contain no internal graph-planner reason for avoiding node 62.",
            "Areas, dynamic planner blacklists, workflow bindings and running missions are outside this map file.",
            "Existing missions and current robot/container states are not changed.",
            "Virtual simulation only; physical docking and clearance are not qualified."]}
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(OUTPUT), "nodes": len(nodes), "directed_edges": len(pairs),
                      "paths_checked": all_pairs, "business_paths": business_paths},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    build()
