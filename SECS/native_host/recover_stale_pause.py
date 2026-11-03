#!/usr/bin/env python3
"""One-time repair for the cancelled 6874 job. No Fleet mutation or SECS report."""
import argparse
import copy
import datetime
import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import urllib.error
import urllib.request

COMMAND = "30E6733474C44F419675E499C61F6B8E"
BASE = "http://127.0.0.1:10870/interfaces/api/"
TERMINAL = {"COMPLETED", "CANCELED", "ABORTED", "FAILED", "REJECTED"}


def require(ok, why):
    if not ok:
        raise RuntimeError(why)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def post(api, body, token):
    req = urllib.request.Request(BASE + api, data=json.dumps(body).encode(),
                                 headers={"Authorization": token, "Content-Type": "application/json"})
    try:
        with urllib.request.build_opener(NoRedirect).open(req, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as ex:
        if api == "mes-secs/status" and ex.code == 404:
            return None
        raise RuntimeError("Read-only query failed: %s HTTP %s" % (api, ex.code)) from None


def rows(api, body, token):
    result = post(api, body, token)
    require(isinstance(result, dict) and result.get("success") is True
            and str(result.get("code")) in ("0", "None")
            and isinstance(result.get("data"), list), "Invalid query response: " + api)
    return result["data"]


def plan(data, jobs, robots, cargos, active_jobs, allow_missing):
    """Validate the exact known case before constructing a replacement journal."""
    require(data.get("tsc") == "PAUSING", "TSC must still be PAUSING; no change made")
    job = data.get("jobs", {}).get(COMMAND, {})
    req = job.get("request", {})
    require(req.get("COMMANDID") == COMMAND and req.get("CARRIERID") == "SIM_CARGO_001"
            and req.get("SOURCEPORT") == "PORT_97" and req.get("DESTPORT") == "PORT_89",
            "Native request identity differs")
    require(job.get("phase") == "ACTIVE" and job.get("control") == "PAUSE"
            and job.get("controlState") == "ACCEPTED"
            and str(job.get("instance")) == "6874" and str(job.get("vehicle")) == "3",
            "Native job/control identity differs")
    require(all(k == COMMAND or j.get("phase") in TERMINAL for k, j in data["jobs"].items()),
            "Another unresolved native job exists")
    require(all(d.get("state") == "ACKED" for d in data.get("reports", [])),
            "Unacknowledged reports exist; preserve them for review")
    require(all(o.get("phase") in {"CONFIRMED", "REJECTED"}
                for o in data.get("cargoOperations", {}).values()), "Unresolved inventory operation")
    require(not active_jobs, "Fleet still has nonterminal jobs")
    if jobs:
        require(len(jobs) == 1, "Ambiguous Fleet job")
        j = jobs[0]
        require(j.get("jobCode") == COMMAND and str(j.get("workflowId")) == "6874"
                and str(j.get("status")) == "31" and j.get("completeTime")
                and j.get("containerCode") == "SIM_CARGO_001" and str(j.get("robotId")) == "3",
                "Fleet has not confirmed this exact job cancelled")
        evidence = "CURRENT_FLEET_CANCELLED_31"
    else:
        require(allow_missing, "Fleet job absent; prior CANCELLED31 confirmation needs explicit --allow-missing-cancelled-job")
        evidence = "PREVIOUSLY_VERIFIED_CANCELLED_31_NOW_ABSENT_OPERATOR_CONFIRMED"
    require(len(robots) == 1 and str(robots[0].get("robotId")) == "3"
            and str(robots[0].get("status")) == "3" and str(robots[0].get("occupyStatus")) == "0"
            and not robots[0].get("containerCode") and not robots[0].get("missionCode")
            and not robots[0].get("rcsMissionCode"), "AMR3 is not idle/free of missions and cargo")
    require(len(cargos) == 1 and cargos[0].get("containerCode") == "SIM_CARGO_001"
            and str(cargos[0].get("inMapStatus")) == "1" and str(cargos[0].get("isCarry")) == "0"
            and cargos[0].get("nodeCode") in {"SECS_ST_97", "lay01-dist1-97"},
            "Cargo001 is not independently confirmed at PORT97")
    result = copy.deepcopy(data)
    target = result["jobs"][COMMAND]
    target.update(phase="CANCELED", control="", controlState="", paused=False)
    target["request"]["_operatorRecovery"] = {
        "reason": evidence, "serverUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "note": "Offline journal reconciliation only. No transport/control POST and no synthetic SECS completion."
    }
    result["tsc"] = "AUTO"
    return result


def journal_path(config):
    mounts = json.loads(subprocess.check_output(
        ["docker", "inspect", "kuka-interface", "--format", "{{json .Mounts}}"], text=True))
    inside = Path(config["journal"])
    matches = []
    for mount in mounts:
        dest = Path(mount["Destination"])
        if mount.get("Type") == "bind" and dest in inside.parents:
            matches.append((len(dest.parts), Path(mount["Source"]) / inside.relative_to(dest)))
    require(matches, "Journal bind mount could not be resolved")
    path = max(matches, key=lambda item: item[0])[1].resolve(strict=True)
    require(Path("/opt/server/logs/interface").resolve() in path.parents and path.is_file(),
            "Journal resolved outside the expected interface log directory")
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--allow-missing-cancelled-job", action="store_true",
                        help="Operator confirms the previously verified status31 job was removed from Fleet")
    args = parser.parse_args()
    require(os.geteuid() == 0, "Run with sudo on the Fleet server")
    config = json.loads(Path("/opt/server/config/servostar-secs.json").read_text(encoding="utf-8-sig"))
    token = config["authorization"]  # never print credentials
    require(post("mes-secs/status", {}, token) is None, "Disable the MES-TRANSFER plugin first")
    path = journal_path(config)
    original = path.read_bytes()
    data = json.loads(original)
    jobs = rows("amr/jobQuery", {"jobCode": COMMAND, "limit": 2}, token)
    robots = rows("amr/robotQuery", {"robotId": "3"}, token)
    cargos = rows("amr/containerQuery", {"containerCode": "SIM_CARGO_001"}, token)
    active = []
    for status in (10, 20, 25, 28, 50, 60):
        active.extend(rows("amr/jobQuery", {"status": status, "limit": 2}, token))
    updated = plan(data, jobs, robots, cargos, active, args.allow_missing_cancelled_job)
    print("CHECK PASSED: exact cancelled job, AMR3 idle, cargo001 PORT97, no active Fleet jobs")
    print("Journal:", path)
    if not args.apply:
        print("DRY RUN: no file changed. Add --apply for the backed-up repair.")
        return
    require(post("mes-secs/status", {}, token) is None and path.read_bytes() == original,
            "Plugin restarted or journal changed; no write allowed")
    info = path.stat()
    backup = path.with_name(path.name + ".before-pausing-recovery." +
                            datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    with backup.open("xb") as stream:
        stream.write(original)
        stream.flush()
        os.fsync(stream.fileno())
    os.chown(backup, info.st_uid, info.st_gid)
    os.chmod(backup, stat.S_IMODE(info.st_mode))
    fd, name = tempfile.mkstemp(prefix=path.name + ".recovery-", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(json.dumps(updated, ensure_ascii=True, separators=(",", ":")).encode())
            stream.flush()
            os.fsync(stream.fileno())
        os.chown(name, info.st_uid, info.st_gid)
        os.chmod(name, stat.S_IMODE(info.st_mode))
        require(path.read_bytes() == original and post("mes-secs/status", {}, token) is None,
                "Journal/plugin changed while preparing repair")
        os.replace(name, path)
        directory = os.open(str(path.parent), os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(name):
            os.unlink(name)
    print("BACKUP:", backup)
    print("RECOVERED:", COMMAND, "ACTIVE/PAUSE -> CANCELED; TSC PAUSING -> AUTO")
    print("Other jobs, IDs, reports and inventory preserved. Enable plugin1.1.4 next.")


if __name__ == "__main__":
    try:
        main()
    except Exception as ex:
        print("STOP: " + str(ex))
        raise SystemExit(1)
