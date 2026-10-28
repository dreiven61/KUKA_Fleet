"""Offline registration/error/replay tests. No connection to Fleet."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import register_stations as reg


class FakeClient:
    def __init__(self, mode="success"):
        self.data = reg.cls(reg.STATION + "StationData")()
        self.calls = []
        self.mode = mode

    def preflight(self):
        pass

    def get(self):
        return reg.cls(reg.STATION + "StationData").FromString(self.data.SerializeToString())

    def insert(self, station, action_uuid):
        self.calls.append((station.uuid, action_uuid))
        response = reg.cls(reg.ACTION + "SubmitActionResponse")()
        if self.mode == "success":
            self.data.stations.add().CopyFrom(station)
        elif self.mode == "reject":
            response.error.code = 3
            response.error.message = "Rejected in offline test"
        elif self.mode == "lost":
            raise OSError("Response lost")
        return response


class RegistrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "state.json"

    def tearDown(self):
        self.temp.cleanup()

    def test_two_registrations_verified_and_replay_skips(self):
        client = FakeClient()
        reg.apply(client, self.path, verify_seconds=0)
        self.assertEqual([call[0] for call in client.calls], ["SECS_ST_89", "SECS_ST_97"])
        reg.apply(client, self.path, verify_seconds=0)
        self.assertEqual(len(client.calls), 2)

    def test_second_station_conflict_blocks_every_insert(self):
        client = FakeClient()
        wrong = client.data.stations.add(uuid="SECS_ST_97", name="Existing site config")
        wrong.capacity.compartments.add(uuid="SOMETHING_ELSE")
        with self.assertRaisesRegex(RuntimeError, "no overwrite"):
            reg.apply(client, self.path)
        self.assertEqual(client.calls, [])

    def test_port_owned_by_another_station_blocks_insert(self):
        client = FakeClient()
        client.data.stations.add(uuid="OTHER").capacity.compartments.add(uuid="PORT_89")
        with self.assertRaisesRegex(RuntimeError, "another Station"):
            reg.apply(client, self.path)
        self.assertEqual(client.calls, [])

    def test_no_readback_means_not_verified_and_no_replay(self):
        client = FakeClient("timeout")
        with self.assertRaisesRegex(RuntimeError, "not visible"):
            reg.apply(client, self.path, verify_seconds=0)
        with self.assertRaisesRegex(RuntimeError, "Earlier submission"):
            reg.apply(client, self.path, verify_seconds=0)
        self.assertEqual(len(client.calls), 1)

    def test_response_lost_never_retried(self):
        client = FakeClient("lost")
        with self.assertRaisesRegex(RuntimeError, "no retry"):
            reg.apply(client, self.path)
        with self.assertRaisesRegex(RuntimeError, "Earlier submission"):
            reg.apply(client, self.path)
        self.assertEqual(len(client.calls), 1)

    def test_rejected_registration_stops_before_second_station(self):
        client = FakeClient("reject")
        with self.assertRaisesRegex(RuntimeError, "rejected"):
            reg.apply(client, self.path)
        self.assertEqual(len(client.calls), 1)

    def test_wire_command_is_insert_station_only(self):
        client = reg.Client("127.0.0.1:1")
        calls = []
        try:
            with patch.object(client, "call", side_effect=lambda *args: calls.append(args)):
                client.insert(reg.targets()[0], "TEST_ACTION")
            package, service, method, request = calls[0]
            self.assertEqual((package, service, method), (reg.ACTION, "ActionExecutorService", "SubmitAction"))
            self.assertEqual(request.action.command.type_url,
                             "type.googleapis.com/" + reg.STATION + "ManageStationDataCommand")
            decoded = reg.cls(reg.STATION + "ManageStationDataCommand").FromString(request.action.command.value)
            self.assertEqual(decoded.type, 1)
            self.assertEqual(decoded.data.uuid, "SECS_ST_89")
            self.assertEqual(decoded.data.capacity.compartments[0].uuid, "PORT_89")
        finally:
            client.close()


if __name__ == "__main__":
    unittest.main(verbosity=2)
