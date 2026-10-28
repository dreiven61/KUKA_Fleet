"""One authorized virtual-robot workflow test, with a durable no-retry journal."""
import argparse
from datetime import datetime, timezone
import getpass
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.request
import uuid

from query_fleet_api import query

BASE_URL = "http://192.168.0.45:5000"
ACTIVE_STATUSES = (10, 20, 25, 28, 50, 60)


def save(path, state):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    with temporary.open("w", encoding="utf-8") as file:
        json.dump(state, file, ensure_ascii=False, indent=2)
        file.flush()
        os.fsync(file.fileno())
    os.replace(temporary, path)


def rows(result):
    response = result.get("response", {})
    if result.get("http_status") != 200 or not isinstance(response, dict) or response.get("success") is not True:
        raise RuntimeError("Query failed: " + json.dumps(result, ensure_ascii=False))
    data = response.get("data")
    if not isinstance(data, list):
        raise RuntimeError("Expected a list response")
    return data


def preflight(token):
    robots = rows(query(BASE_URL, token, "robotQuery", {"robotId": "1"}))
    if len(robots) != 1 or str(robots[0].get("robotId")) != "1":
        raise RuntimeError("Robot 1 was not uniquely identified")
    robot = robots[0]
    expected = {"status": 3, "occupyStatus": 0, "liftStatus": 0, "reliability": 1,
                "mapCode": "lay01", "floorNumber": "dist1", "containerCode": ""}
    if any(robot.get(key) != value for key, value in expected.items()):
        raise RuntimeError("Robot 1 is not ready: " + json.dumps(robot, ensure_ascii=False))
    cargo_rows = rows(query(BASE_URL, token, "containerQueryAll", {"containerCode": "SIM_CARGO_001"}))
    if len(cargo_rows) != 1:
        raise RuntimeError("Cargo was not uniquely identified")
    cargo = cargo_rows[0]
    if (cargo.get("nodeCode") != "lay01-dist1-89" or cargo.get("isCarry") != 0
            or cargo.get("inMapStatus") != 1 or cargo.get("containerModelCode") != "SIM_TRAY_001"):
        raise RuntimeError("Cargo is not at the expected source: " + json.dumps(cargo, ensure_ascii=False))
    for status in ACTIVE_STATUSES:
        jobs = rows(query(BASE_URL, token, "jobQuery", {"robotId": "1", "status": str(status), "limit": 100}))
        if jobs:
            raise RuntimeError("Robot 1 has unresolved jobs: " + json.dumps(jobs, ensure_ascii=False))
    return {"robot": robot, "cargo": cargo, "unresolvedJobs": []}


def submit_once(token, state, path):
    if state["stage"] != "prepared":
        raise RuntimeError("This journal has already attempted submission. Query the existing mission; do not resend.")
    state["preflight"] = preflight(token)
    state["stage"] = "submission_attempted"
    state["submitted_at"] = datetime.now(timezone.utc).isoformat()
    save(path, state)  # Save identity and attempted status BEFORE sending.
    request = urllib.request.Request(
        BASE_URL + "/interfaces/api/amr/submitMission",
        data=json.dumps(state["request"]).encode(),
        headers={"Authorization": token, "Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            state["submit_http_status"] = response.status
            text = response.read().decode("utf-8", errors="replace").replace(token, "[REDACTED]")
        state["submit_response"] = json.loads(text)
        state["stage"] = "accepted" if state["submit_response"].get("success") is True else "rejected"
    except urllib.error.HTTPError as error:
        state["submit_http_status"] = error.code
        state["submit_response"] = error.read().decode("utf-8", errors="replace").replace(token, "[REDACTED]")
        state["stage"] = "acceptance_unknown"
    except (urllib.error.URLError, OSError, ValueError) as error:
        state["submit_error"] = str(error).replace(token, "[REDACTED]")
        state["stage"] = "acceptance_unknown"
    save(path, state)
    print("Submission:", state["stage"], json.dumps(state.get("submit_response"), ensure_ascii=False), flush=True)


def monitor(token, state, path, seconds):
    mission_code = state["request"]["missionCode"]
    deadline = time.monotonic() + seconds
    previous = None
    while time.monotonic() < deadline:
        jobs = rows(query(BASE_URL, token, "jobQuery", {"jobCode": mission_code, "limit": 10}))
        jobs = [job for job in jobs if job.get("jobCode") == mission_code]
        if len(jobs) > 1:
            raise RuntimeError("Duplicate jobs have the same missionCode")
        if jobs:
            job = jobs[0]
            state["last_job"] = job
            signature = (job.get("status"), job.get("targetCellCode"), job.get("warnCode"))
            if signature != previous:
                print("Job:", json.dumps(job, ensure_ascii=False), flush=True)
                previous = signature
            save(path, state)
            status = job.get("status")
            if status == 30:
                robot_rows = rows(query(BASE_URL, token, "robotQuery", {"robotId": "1"}))
                cargo_rows = rows(query(BASE_URL, token, "containerQueryAll", {"containerCode": "SIM_CARGO_001"}))
                state["final_robot"] = robot_rows
                state["final_cargo"] = cargo_rows
                if (len(robot_rows) == 1 and len(cargo_rows) == 1
                        and robot_rows[0].get("status") == 3 and robot_rows[0].get("occupyStatus") == 0
                        and robot_rows[0].get("liftStatus") == 0 and robot_rows[0].get("containerCode") == ""
                        and cargo_rows[0].get("nodeCode") == "lay01-dist1-89"
                        and cargo_rows[0].get("isCarry") == 0 and cargo_rows[0].get("inMapStatus") == 1):
                    state["stage"] = "completed_and_verified"
                    save(path, state)
                    print("COMPLETED_AND_VERIFIED", mission_code, flush=True)
                    return
            elif status in (31, 35, 50, 60):
                state["stage"] = "needs_review"
                save(path, state)
                print("NEEDS_REVIEW", mission_code, "status", status, flush=True)
                return
        time.sleep(2)
    state["stage"] = "monitor_timeout"
    save(path, state)
    print("MONITOR_TIMEOUT; no resubmission or cancellation was performed.", mission_code, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--journal", type=Path, required=True)
    parser.add_argument("--submit", action="store_true")
    parser.add_argument("--confirmed-virtual", action="store_true")
    parser.add_argument("--repeat-event-disabled", action="store_true")
    parser.add_argument("--monitor-seconds", type=int, default=300)
    args = parser.parse_args()
    path = args.journal.resolve()
    if path.exists():
        state = json.loads(path.read_text(encoding="utf-8"))
    else:
        state = {
            "stage": "prepared", "created_at": datetime.now(timezone.utc).isoformat(),
            "request": {"orgId": "UNIVERSAL", "requestId": uuid.uuid4().hex,
                        "missionCode": "SECS_API_ONCE_" + datetime.now().strftime("%Y%m%d_%H%M%S") + "_" + uuid.uuid4().hex[:6],
                        "missionType": "RACK_MOVE", "robotType": "LIFT", "robotIds": ["1"],
                        "robotModels": ["KMP1500I"], "containerModelCode": "SIM_TRAY_001",
                        "containerCode": "SIM_CARGO_001", "templateCode": "W000000002",
                        "priority": 1, "lockRobotAfterFinish": False}}
        save(path, state)
    print("Journal:", path, flush=True)
    print("Request:", json.dumps(state["request"], ensure_ascii=False), flush=True)
    if args.submit and state["stage"] != "prepared":
        raise SystemExit("REFUSED: submission already attempted; use the same journal without --submit to query only.")
    if args.submit and not (args.confirmed_virtual and args.repeat_event_disabled):
        raise SystemExit("Virtual robot and disabled repeat event must be confirmed before submission.")
    if state["stage"] == "prepared" and not args.submit:
        return
    token = os.environ.get("FLEET_ACCESS_TOKEN") or getpass.getpass("Fleet access token: ")
    if not token.strip():
        raise SystemExit("An access token is required")
    if args.submit:
        submit_once(token, state, path)
    if state["stage"] in ("accepted", "submission_attempted", "acceptance_unknown", "monitor_timeout"):
        monitor(token, state, path, args.monitor_seconds)
    if state["stage"] != "completed_and_verified":
        raise SystemExit(2)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
