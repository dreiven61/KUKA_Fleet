import copy
import json
import unittest
from recover_stale_pause import COMMAND, plan


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.job = {"request": {"COMMANDID": COMMAND, "CARRIERID": "SIM_CARGO_001",
                               "SOURCEPORT": "PORT_97", "DESTPORT": "PORT_89"},
                    "phase": "ACTIVE", "control": "PAUSE", "controlState": "ACCEPTED",
                    "instance": "6874", "vehicle": "3", "next": 2, "paused": False}
        self.data = {"tsc": "PAUSING", "jobs": {COMMAND: self.job,
                     "OLD_DONE": {"phase": "COMPLETED", "request": {"COMMANDID": "OLD_DONE"}}},
                     "reports": [{"id": 57, "state": "ACKED"}], "reportKeys": ["existing:1"],
                     "cargoOperations": {"INVENTORY_ID": {"phase": "CONFIRMED"}}, "sequence": 58}
        self.jobs = [{"jobCode": COMMAND, "workflowId": 6874, "robotId": "3", "status": 31,
                      "containerCode": "SIM_CARGO_001", "completeTime": "previously-observed"}]
        self.robots = [{"robotId": "3", "status": 3, "occupyStatus": 0, "containerCode": ""}]
        self.cargos = [{"containerCode": "SIM_CARGO_001", "inMapStatus": 1,
                        "isCarry": 0, "nodeCode": "SECS_ST_97"}]

    def run_plan(self, jobs=None, allow=False, active=None):
        return plan(self.data, self.jobs if jobs is None else jobs, self.robots, self.cargos,
                    [] if active is None else active, allow)

    def test_preserves_journal_and_input(self):
        original = copy.deepcopy(self.data)
        result = self.run_plan()
        self.assertEqual(self.data, original)
        self.assertEqual(result["tsc"], "AUTO")
        target = result["jobs"][COMMAND]
        self.assertEqual(target["phase"], "CANCELED")
        self.assertEqual((target["control"], target["controlState"], target["paused"]), ("", "", False))
        self.assertEqual(target["next"], 2)
        for key in ("reports", "reportKeys", "cargoOperations", "sequence"):
            self.assertEqual(result[key], original[key])
        self.assertEqual(result["jobs"]["OLD_DONE"], original["jobs"]["OLD_DONE"])
        self.assertEqual(json.loads(json.dumps(result)), result)

    def test_missing_job_requires_explicit_operator_confirmation(self):
        with self.assertRaises(RuntimeError):
            self.run_plan(jobs=[])
        self.assertIn("OPERATOR_CONFIRMED", self.run_plan(jobs=[], allow=True)["jobs"][COMMAND]["request"]["_operatorRecovery"]["reason"])

    def test_other_unresolved_work_blocks(self):
        for phase in ("ACTIVE", "SUBMITTING", "UNCERTAIN"):
            self.data["jobs"]["OTHER"] = {"phase": phase}
            with self.assertRaises(RuntimeError):
                self.run_plan()

    def test_fleet_or_robot_active_blocks(self):
        with self.assertRaises(RuntimeError):
            self.run_plan(active=[{"status": 20}])
        self.robots[0]["missionCode"] = "NEW_JOB"
        with self.assertRaises(RuntimeError):
            self.run_plan()

    def test_wrong_job_identity_or_status_blocks(self):
        for field, value in (("status", 20), ("workflowId", 9999), ("robotId", "1"), ("containerCode", "OTHER")):
            jobs = copy.deepcopy(self.jobs)
            jobs[0][field] = value
            with self.assertRaises(RuntimeError):
                self.run_plan(jobs=jobs, allow=True)

    def test_wrong_cargo_location_blocks(self):
        self.cargos[0]["nodeCode"] = "SECS_ST_89"
        with self.assertRaises(RuntimeError):
            self.run_plan()

    def test_unacknowledged_report_blocks(self):
        self.data["reports"][0]["state"] = "BLOCKED"
        with self.assertRaises(RuntimeError):
            self.run_plan()

    def test_unresolved_inventory_or_changed_native_state_blocks(self):
        self.data["cargoOperations"]["INVENTORY_ID"]["phase"] = "UNCERTAIN"
        with self.assertRaises(RuntimeError):
            self.run_plan()
        self.data["cargoOperations"] = {}
        self.job["instance"] = "DIFFERENT"
        with self.assertRaises(RuntimeError):
            self.run_plan()


if __name__ == "__main__":
    unittest.main()
