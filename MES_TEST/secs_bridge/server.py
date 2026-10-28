"""Fleet native SECS -> Action Executor -> existing virtual robot workflow.

No HSMS implementation, no automatic replay of uncertain REST submissions,
and no invented S6F11 completion event. CHECK_COMPLETED is a custom read-only check.
"""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import getpass
import hashlib
import json
import os
from pathlib import Path
import sys
import threading
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import grpc
from google.protobuf.message import DecodeError
from query_fleet_api import query
from run_workflow_once import BASE_URL, monitor, preflight, rows, save, submit_once
from protocol import validate
from wire import RAW, cls, handlers, message


def now():
    return datetime.now(timezone.utc).isoformat()


def reply(code=0, ack=None):
    result = message("SubmitActionResponse")
    result.error.code = code
    if ack is not None:
        result.error.message = "ResponseAck:" + str(ack)
    return result


class Executor:
    def __init__(self, directory, token, worker=None):
        self.directory = directory
        directory.mkdir(parents=True, exist_ok=True)
        self.token = token
        self.lock = threading.RLock()
        self.changed = threading.Condition(self.lock)
        self.revision = 0
        self.states = {}
        self.worker = worker or self.execute
        self.threads = []
        for path in directory.glob("*.json"):
            state = json.loads(path.read_text(encoding="utf-8"))
            self.states[state["command_id"]] = state

    def path(self, command_id):
        return self.directory / (hashlib.sha256(command_id.encode()).hexdigest() + ".json")

    def publish(self, state):
        with self.changed:
            self.states[state["command_id"]] = json.loads(json.dumps(state))
            save(self.path(state["command_id"]), state)
            self.revision += 1
            self.changed.notify_all()

    def authorize(self, context):
        # CLI binds one laboratory NIC; also reject callers from other hosts.
        peer = context.peer()
        if not any(peer.startswith("ipv4:" + host + ":") for host in ("192.168.0.45", "127.0.0.1")):
            context.abort(grpc.StatusCode.PERMISSION_DENIED, "Fleet server only")

    def SubmitAction(self, request, context):
        self.authorize(context)
        description = request.action
        if not description.uuid or len(description.uuid) > 128:
            return reply(3, 3)
        if description.command.type_url.rsplit("/", 1)[-1] != RAW:
            return reply(3, 1)
        try:
            raw = cls(RAW).FromString(description.command.value)
            values = validate(raw.command, raw.data_bytes)
        except (ValueError, UnicodeError, DecodeError) as error:
            print("REJECTED_PARAMETERS:", str(error), flush=True)
            return reply(3, 3)
        command_id = values["COMMANDID"]
        with self.lock:
            if raw.command == "CHECK_COMPLETED":
                state = self.states.get(command_id)
                # This is a query, never an action acceptance or a REST submission.
                ack = 6 if state is None else (0 if state["stage"] == "completed_and_verified" else 2)
                print("CHECK_COMPLETED", command_id, "ACK", ack, flush=True)
                return reply(3, ack)
            encoded = base64.b64encode(description.SerializeToString()).decode()
            for known in self.states.values():
                if known["action_uuid"] == description.uuid:
                    return reply() if known["description"] == encoded else reply(3, 3)
            if command_id in self.states:
                print("DUPLICATE_COMMANDID:", command_id, "not submitted again", flush=True)
                return reply(3, 2)
            if any(state["stage"] != "completed_and_verified" for state in self.states.values()):
                return reply(3, 2)
            # No REST mutation can occur before these checks and the durable journal.
            try:
                checked = preflight(self.token)
            except Exception as error:
                print("PREFLIGHT_REJECTED:", str(error).replace(self.token, "[REDACTED]"), flush=True)
                return reply(3, 2)
            state = {
                "command_id": command_id, "action_uuid": description.uuid,
                "description": encoded, "stage": "prepared", "created_at": now(),
                "preflight_at_acceptance": checked,
                "request": {"orgId": "UNIVERSAL", "requestId": uuid.uuid4().hex,
                            "missionCode": "SECS_WF_" + uuid.uuid4().hex,
                            "missionType": "RACK_MOVE", "robotType": "LIFT", "robotIds": ["1"],
                            "robotModels": ["KMP1500I"], "containerModelCode": "SIM_TRAY_001",
                            "containerCode": "SIM_CARGO_001", "templateCode": "W000000002",
                            "priority": 1, "lockRobotAfterFinish": False}}
            self.publish(state)
            self.start_worker(state, True)
        print("ACCEPTED", command_id, state["request"]["missionCode"], flush=True)
        return reply()  # Native Fleet derives HCACK=4 while action is unfinished.

    def start_worker(self, state, new):
        thread = threading.Thread(target=self.worker, args=(state, new), daemon=True)
        self.threads.append(thread)
        thread.start()

    def execute(self, state, new):
        # Work on a private copy; subscribers never observe partially updated dictionaries.
        state = json.loads(json.dumps(state))
        path = self.path(state["command_id"])
        try:
            if new:
                submit_once(self.token, state, path)
                self.publish(state)
            if state["stage"] in ("accepted", "submission_attempted", "acceptance_unknown", "monitor_timeout"):
                monitor(self.token, state, path, 300)
        except Exception as error:
            state["last_error"] = str(error).replace(self.token, "[REDACTED]")
            state["stage"] = "needs_review"
        self.publish(state)
        print("WORKFLOW_RESULT", state["command_id"], state["stage"], flush=True)

    def recover(self):
        for state in list(self.states.values()):
            if state["stage"] == "prepared":
                state["stage"] = "recovery_hold"  # Never start an unsubmitted job on service restart.
                self.publish(state)
            elif state["stage"] in ("accepted", "submission_attempted", "acceptance_unknown", "monitor_timeout"):
                self.start_worker(state, False)  # Existing missionCode queries ONLY.

    def action(self, state):
        result = message("Action")
        result.description.ParseFromString(base64.b64decode(state["description"]))
        stage = state["stage"]
        result.state.action_state = (6 if stage == "completed_and_verified" else
                                     2 if stage == "prepared" else
                                     4 if stage in ("accepted", "submission_attempted", "acceptance_unknown") else 5)
        result.state.error_state.level = 1 if result.state.action_state != 5 else 2
        if result.state.action_state == 5:
            error = result.state.error_state.errors.add()
            error.uuid = state["action_uuid"]
            error.status.code = 9
            error.status.message = "Manual review required: " + stage
        return result

    def SubscribeActions(self, request, context):
        self.authorize(context)
        revision = -1
        sequence = 0
        while context.is_active():
            with self.changed:
                if self.revision == revision:
                    self.changed.wait(timeout=1)
                    continue
                revision = self.revision
                response = message("SubscribeActionsResponse")
                response.header.message_type_uri = response.DESCRIPTOR.full_name
                response.header.update_type = 1  # Complete snapshot, including terminal actions.
                response.header.seq = sequence
                response.header.timestamp.FromDatetime(datetime.now(timezone.utc))
                sequence += 1
                for state in self.states.values():
                    response.actions.add().CopyFrom(self.action(state))
            yield response

    def GetActionTypes(self, request, context):
        self.authorize(context)
        return message("GetActionTypesResponse", action_types=[RAW])

    def UpdateAction(self, request, context):
        self.authorize(context)
        response = message("UpdateActionResponse")
        response.error.code = 9
        response.error.message = "Updates are not supported"
        return response

    def CancelActions(self, request, context):
        self.authorize(context)
        response = message("CancelActionsResponse")
        for identifier in request.action_uuids:
            response.errors[identifier].code = 9
            response.errors[identifier].message = "No cancellation was issued; inspect Fleet job"
        return response

    def PauseExecutor(self, request, context):
        self.authorize(context)
        context.abort(grpc.StatusCode.UNIMPLEMENTED, "Pause is not supported")

    def ResumeExecutor(self, request, context):
        self.authorize(context)
        context.abort(grpc.StatusCode.UNIMPLEMENTED, "Resume is not supported")


def process_lock(directory):
    """Prevent two bridge processes from racing over the journal and virtual robot."""
    directory.mkdir(parents=True, exist_ok=True)
    handle = (directory / "bridge.lock").open("a+b")
    handle.seek(0)
    if os.name == "nt":
        import msvcrt
        handle.write(b"0")
        handle.flush()
        handle.seek(0)
        msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
    else:
        import fcntl
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    return handle


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="192.168.0.51:50240")
    parser.add_argument("--journal-dir", type=Path, default=Path(__file__).parent / "runs")
    parser.add_argument("--confirmed-virtual", action="store_true", required=True)
    parser.add_argument("--repeat-event-disabled", action="store_true", required=True)
    args = parser.parse_args()
    guard = process_lock(args.journal_dir)
    token = os.environ.get("FLEET_ACCESS_TOKEN") or getpass.getpass("Fleet access token: ")
    if not token.strip():
        raise SystemExit("Token is required")
    executor = Executor(args.journal_dir, token)
    grpc_server = grpc.server(ThreadPoolExecutor(max_workers=12), options=[
        ("grpc.max_receive_message_length", 16384), ("grpc.max_send_message_length", 4 * 1024 * 1024)])
    grpc_server.add_generic_rpc_handlers((handlers(executor),))
    if not grpc_server.add_insecure_port(args.bind):
        raise SystemExit("Cannot bind " + args.bind)
    grpc_server.start()
    executor.recover()
    print("READY", args.bind, "RUN_WORKFLOW / CHECK_COMPLETED; waiting for SECS command", flush=True)
    print("Only virtual robot 1, SIM_CARGO_001, W000000002. No S6F11 completion publisher.", flush=True)
    try:
        grpc_server.wait_for_termination()
    except KeyboardInterrupt:
        grpc_server.stop(3).wait()
        print("Stopped listener. Existing Fleet job is NOT cancelled; journal retained.")
    guard.close()


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
