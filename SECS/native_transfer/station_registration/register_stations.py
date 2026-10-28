"""One-shot Station/compartment registration through Fleet's installed gRPC API.

Default: read-only preview. --apply inserts only SECS_ST_89/PORT_89 and
SECS_ST_97/PORT_97. This program never submits a transport order or edits a graph.
"""
import argparse
import base64
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys
import time
import uuid

import grpc
from google.protobuf import descriptor_pb2, descriptor_pool, message_factory

ROOT = Path(__file__).resolve().parent
ENDPOINT = "192.168.0.45:50110"
ACTION = "kuka.actionexecutor.v1alpha1."
DATA = "kuka.dataprovider.v1alpha1."
STATION = "kuka.mobilerobotics.station.v1alpha1."
pool = descriptor_pool.DescriptorPool()
for descriptor in descriptor_pb2.FileDescriptorSet.FromString((ROOT / "station_api.pb").read_bytes()).file:
    pool.Add(descriptor)


def cls(name):
    return message_factory.GetMessageClass(pool.FindMessageTypeByName(name))


def targets():
    result = []
    for node in (89, 97):
        item = cls(STATION + "Station")(uuid=f"SECS_ST_{node}", name=f"SECS test port at node {node}")
        item.capacity.compartments.add(uuid=f"PORT_{node}")
        result.append(item)
    return result


def now():
    return datetime.now(timezone.utc).isoformat()


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    with temporary.open("w", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, path)


def same(left, right):
    return left.SerializeToString(deterministic=True) == right.SerializeToString(deterministic=True)


def find(data, identifier):
    matches = [station for station in data.stations if station.uuid == identifier]
    if len(matches) > 1:
        raise RuntimeError("Duplicate Station UUID: " + identifier)
    return matches[0] if matches else None


def inspect(data):
    plan = []
    for expected in targets():
        port = expected.capacity.compartments[0].uuid
        present = find(data, expected.uuid)
        for station in data.stations:
            if station.uuid != expected.uuid and any(compartment.uuid == port for compartment in station.capacity.compartments):
                raise RuntimeError(f"Port {port} already belongs to another Station: {station.uuid}")
        if present is not None and not same(present, expected):
            raise RuntimeError("Existing Station differs; no overwrite will be performed: " + expected.uuid)
        plan.append({"station": expected.uuid, "port": port,
                     "state": "ALREADY_PRESENT" if present is not None else "INSERT_REQUIRED"})
    return plan


class Client:
    def __init__(self, endpoint=ENDPOINT):
        self.channel = grpc.insecure_channel(endpoint)

    def call(self, package, service, method, request, timeout=8):
        return self.channel.unary_unary(
            "/" + package + service + "/" + method,
            request_serializer=lambda message: message.SerializeToString(),
            response_deserializer=cls(package + method + "Response").FromString,
        )(request, timeout=timeout)

    def preflight(self):
        actions = self.call(ACTION, "ActionExecutorService", "GetActionTypes", cls(ACTION + "GetActionTypesRequest")())
        if STATION + "ManageStationDataCommand" not in actions.action_types:
            raise RuntimeError("The endpoint does not advertise ManageStationDataCommand")
        types = self.call(DATA, "DataProviderService", "GetDataTypes", cls(DATA + "GetDataTypesRequest")())
        if STATION + "StationData" not in types.data_types:
            raise RuntimeError("The endpoint does not advertise StationData")

    def get(self):
        response = self.call(DATA, "DataProviderService", "GetData",
                             cls(DATA + "GetDataRequest")(data_type=STATION + "StationData"))
        if response.data.type_url.rsplit("/", 1)[-1] != STATION + "StationData":
            raise RuntimeError("Unexpected data type returned by Station service")
        return cls(STATION + "StationData").FromString(response.data.value)

    def insert(self, expected, action_uuid):
        command = cls(STATION + "ManageStationDataCommand")(type=1)  # INSERT, never UPDATE.
        command.data.CopyFrom(expected)
        request = cls(ACTION + "SubmitActionRequest")()
        request.action.uuid = action_uuid
        request.action.timestamp.FromDatetime(datetime.now(timezone.utc))
        request.action.command.type_url = "type.googleapis.com/" + command.DESCRIPTOR.full_name
        request.action.command.value = command.SerializeToString()
        return self.call(ACTION, "ActionExecutorService", "SubmitAction", request)

    def close(self):
        self.channel.close()


def apply(client, journal_path, verify_seconds=20, sleep=time.sleep):
    client.preflight()
    before = client.get()
    inspect(before)  # Check BOTH targets for conflicts before inserting either.
    journal = (json.loads(journal_path.read_text(encoding="utf-8")) if journal_path.exists()
               else {"endpoint": ENDPOINT, "created_at": now(), "entries": {}})
    if journal.get("endpoint") != ENDPOINT:
        raise RuntimeError("Journal endpoint does not match this Fleet server")
    if "station_data_before_base64" not in journal:
        journal["station_data_before_base64"] = base64.b64encode(before.SerializeToString()).decode()
        save(journal_path, journal)
    for expected in targets():
        current = client.get()
        inspect(current)
        present = find(current, expected.uuid)
        if present is not None:
            print("ALREADY_PRESENT", expected.uuid, expected.capacity.compartments[0].uuid, flush=True)
            record = journal["entries"].get(expected.uuid)
            if record:
                record["state"] = "REGISTERED_AND_VERIFIED"
                save(journal_path, journal)
            continue
        if expected.uuid in journal["entries"]:
            raise RuntimeError("Earlier submission exists but object is absent; inspect its result before retrying: " + expected.uuid)
        record = {"action_uuid": str(uuid.uuid4()), "state": "SUBMISSION_ATTEMPTED", "attempted_at": now()}
        journal["entries"][expected.uuid] = record
        save(journal_path, journal)  # Persist BEFORE any registration RPC.
        try:
            response = client.insert(expected, record["action_uuid"])
        except Exception as error:
            record.update(state="ACCEPTANCE_UNKNOWN", error=str(error))
            save(journal_path, journal)
            raise RuntimeError("Registration response was not confirmed; no retry was sent: " + expected.uuid) from error
        if response.error.code:
            record.update(state="REJECTED", code=response.error.code, message=response.error.message)
            save(journal_path, journal)
            raise RuntimeError(f"Registration rejected: {expected.uuid}: {response.error.code} {response.error.message}")
        record["state"] = "ACCEPTED_NOT_VERIFIED"
        save(journal_path, journal)
        deadline = time.monotonic() + verify_seconds
        while True:
            try:
                current = client.get()
                inspect(current)
                present = find(current, expected.uuid)
            except Exception as error:
                record["verification_error"] = str(error)
                save(journal_path, journal)
                raise
            if present is not None:
                record.update(state="REGISTERED_AND_VERIFIED", verified_at=now())
                save(journal_path, journal)
                print("REGISTERED_AND_VERIFIED", expected.uuid, expected.capacity.compartments[0].uuid, flush=True)
                break
            if time.monotonic() >= deadline:
                record["state"] = "VERIFICATION_TIMEOUT"
                save(journal_path, journal)
                raise RuntimeError("Registration accepted but not visible in StationData; no retry was sent")
            sleep(0.5)
    final = client.get()
    if any(row["state"] != "ALREADY_PRESENT" for row in inspect(final)):
        raise RuntimeError("Final StationData verification failed")
    journal["state"] = "BOTH_STATIONS_VERIFIED"
    save(journal_path, journal)
    print("BOTH_STATIONS_VERIFIED", flush=True)


def lock_process(directory):
    directory.mkdir(parents=True, exist_ok=True)
    handle = (directory / "registration.lock").open("a+b")
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
    parser.add_argument("--apply", action="store_true", help="Insert the two named Stations and compartments")
    args = parser.parse_args()
    client = Client()
    guard = None
    try:
        if args.apply:
            guard = lock_process(ROOT / "runs")
            apply(client, ROOT / "runs" / "registration.json")
        else:
            client.preflight()
            result = inspect(client.get())
            print("READ_ONLY_PREVIEW", ENDPOINT)
            for row in result:
                print(row["station"], row["port"], row["state"])
        print("Station registration only. Map-node linkage and KMP simulator integration remain separate.")
    except (RuntimeError, OSError, grpc.RpcError, ValueError) as error:
        print("ERROR:", str(error), file=sys.stderr, flush=True)
        return 1
    finally:
        client.close()
        if guard:
            guard.close()
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
