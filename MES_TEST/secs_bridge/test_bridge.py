"""Offline tests: all Fleet reads/submissions are mocked. No robot commands."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
import uuid

import grpc
import server
from protocol import parameters, validate
from wire import RAW, SERVICE, cls, handlers, message


def binary(value):
    if isinstance(value, list):
        return bytes([1, len(value)]) + b"".join(binary(child) for child in value)
    text = value.encode("ascii")
    return bytes([65, len(text)]) + text


def request(command_id="WF_TEST_001", command="RUN_WORKFLOW", workflow="W000000002", action_id=None):
    pairs = [["COMMANDID", command_id]]
    if command == "RUN_WORKFLOW":
        pairs.append(["WORKFLOW", workflow])
    raw = cls(RAW)(command=command, data_bytes=binary(pairs))
    result = message("SubmitActionRequest")
    result.action.uuid = action_id or str(uuid.uuid4())
    result.action.command.type_url = "type.googleapis.com/" + RAW
    result.action.command.value = raw.SerializeToString()
    return result


class Context:
    def peer(self):
        return "ipv4:127.0.0.1:12345"


class ProtocolTests(unittest.TestCase):
    def test_parameter_types_and_limits(self):
        self.assertEqual(parameters(binary([["COMMANDID", "A"]])), {"COMMANDID": "A"})
        bad = [b"", b"\x00", binary([["COMMANDID", "A"]]) + b"x",
               binary([["COMMANDID", "A"], ["COMMANDID", "B"]]),
               binary(["not-a-pair"]), b"\xa9\x02\x00\x01", b"x" * 4097]
        for value in bad:
            with self.subTest(value=value[:15]), self.assertRaises(ValueError):
                parameters(value)

    def test_allowlist(self):
        with self.assertRaises(ValueError):
            validate("RUN_WORKFLOW", binary([["COMMANDID", "A"], ["WORKFLOW", "W000000003"]]))
        with self.assertRaises(ValueError):
            validate("RUN_WORKFLOW", binary([["COMMANDID", "../bad"], ["WORKFLOW", "W000000002"]]))


class ExecutorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        self.calls = []
        self.executor = server.Executor(self.directory, "TEST_SECRET",
            worker=lambda state, new: self.calls.append((state, new)))
        self.context = Context()

    def tearDown(self):
        for worker in self.executor.threads:
            worker.join(timeout=2)
        self.temp.cleanup()

    @patch.object(server, "preflight", return_value={})
    def test_durable_identity_duplicates_and_busy(self, preflight):
        first = request()
        self.assertEqual(self.executor.SubmitAction(first, self.context).error.code, 0)
        for worker in self.executor.threads:
            worker.join()
        saved = next(self.directory.glob("*.json")).read_text()
        self.assertIn("SECS_WF_", saved)
        self.assertNotIn("TEST_SECRET", saved)
        self.assertEqual(self.executor.SubmitAction(first, self.context).error.code, 0)
        self.assertEqual(self.executor.SubmitAction(request(), self.context).error.message, "ResponseAck:2")
        self.assertEqual(self.executor.SubmitAction(request("SECOND"), self.context).error.message, "ResponseAck:2")
        self.assertEqual(len(self.calls), 1)

    @patch.object(server, "preflight", side_effect=RuntimeError("busy"))
    def test_preflight_rejection_never_schedules(self, preflight):
        self.assertEqual(self.executor.SubmitAction(request(), self.context).error.message, "ResponseAck:2")
        self.assertEqual(self.calls, [])
        self.assertEqual(list(self.directory.glob("*.json")), [])

    @patch.object(server, "preflight", return_value={})
    def test_check_completion_and_restart(self, preflight):
        self.executor.SubmitAction(request(), self.context)
        for worker in self.executor.threads:
            worker.join()
        check = request(command="CHECK_COMPLETED")
        self.assertEqual(self.executor.SubmitAction(check, self.context).error.message, "ResponseAck:2")
        recovered = server.Executor(self.directory, "TEST_SECRET", worker=lambda state, new: self.calls.append((state, new)))
        recovered.recover()
        self.assertEqual(recovered.states["WF_TEST_001"]["stage"], "recovery_hold")
        self.assertEqual(len(self.calls), 1)  # Recovery of prepared journal must not submit.
        state = recovered.states["WF_TEST_001"]
        state["stage"] = "completed_and_verified"
        recovered.publish(state)
        self.assertEqual(recovered.SubmitAction(check, self.context).error.message, "ResponseAck:0")
        self.assertEqual(recovered.SubmitAction(request("MISSING", "CHECK_COMPLETED"), self.context).error.message, "ResponseAck:6")
        self.assertEqual(recovered.action(state).state.action_state, 6)

    @patch.object(server, "preflight", return_value={})
    def test_uncertain_restart_queries_only(self, preflight):
        self.executor.SubmitAction(request(), self.context)
        for worker in self.executor.threads:
            worker.join()
        state = self.executor.states["WF_TEST_001"]
        state["stage"] = "acceptance_unknown"
        self.executor.publish(state)
        calls = []
        recovered = server.Executor(self.directory, "TEST_SECRET", worker=lambda state, new: calls.append(new))
        recovered.recover()
        for worker in recovered.threads:
            worker.join()
        self.assertEqual(calls, [False])

    @patch.object(server, "preflight", return_value={})
    @patch.object(server, "submit_once")
    @patch.object(server, "monitor")
    def test_worker_no_premature_completion(self, monitor, submit, preflight):
        self.executor.SubmitAction(request(), self.context)
        for worker in self.executor.threads:
            worker.join()
        state = self.executor.states["WF_TEST_001"]
        submit.side_effect = lambda token, current, path: current.update(stage="acceptance_unknown")
        monitor.side_effect = lambda token, current, path, seconds: current.update(stage="monitor_timeout")
        self.executor.execute(state, True)
        self.assertEqual(self.executor.states["WF_TEST_001"]["stage"], "monitor_timeout")
        self.assertNotEqual(self.executor.action(self.executor.states["WF_TEST_001"]).state.action_state, 6)
        submit.assert_called_once()

    def test_real_grpc_roundtrip_and_stream(self):
        listener = grpc.server(ThreadPoolExecutor(max_workers=4))
        listener.add_generic_rpc_handlers((handlers(self.executor),))
        port = listener.add_insecure_port("127.0.0.1:0")
        listener.start()
        channel = grpc.insecure_channel("127.0.0.1:" + str(port))
        try:
            call = channel.unary_unary("/" + SERVICE + "/GetActionTypes",
                request_serializer=lambda msg: msg.SerializeToString(),
                response_deserializer=cls("kuka.actionexecutor.v1alpha1.GetActionTypesResponse").FromString)
            self.assertEqual(list(call(message("GetActionTypesRequest"), timeout=3).action_types), [RAW])
            stream = channel.unary_stream("/" + SERVICE + "/SubscribeActions",
                request_serializer=lambda msg: msg.SerializeToString(),
                response_deserializer=cls("kuka.actionexecutor.v1alpha1.SubscribeActionsResponse").FromString)
            subscription = stream(message("SubscribeActionsRequest"), timeout=4)
            self.assertEqual(len(next(subscription).actions), 0)
            with patch.object(server, "preflight", return_value={}):
                submit = channel.unary_unary("/" + SERVICE + "/SubmitAction",
                    request_serializer=lambda msg: msg.SerializeToString(),
                    response_deserializer=cls("kuka.actionexecutor.v1alpha1.SubmitActionResponse").FromString)
                self.assertEqual(submit(request(), timeout=3).error.code, 0)
            self.assertEqual(next(subscription).actions[0].state.action_state, 2)
            subscription.cancel()
        finally:
            channel.close()
            listener.stop(0).wait()


if __name__ == "__main__":
    unittest.main(verbosity=2)
