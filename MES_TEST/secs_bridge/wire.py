"""Dynamic bindings from the descriptor set compiled from this Fleet installation."""
from pathlib import Path
import grpc
from google.protobuf import descriptor_pb2, descriptor_pool, message_factory

PREFIX = "kuka.actionexecutor.v1alpha1."
SERVICE = PREFIX + "ActionExecutorService"
RAW = "kuka.mobilerobotics.secs.v1alpha1.SecsRawCommand"
pool = descriptor_pool.DescriptorPool()
for descriptor in descriptor_pb2.FileDescriptorSet.FromString(
        Path(__file__).with_name("fleet_actions.pb").read_bytes()).file:
    pool.Add(descriptor)


def cls(name):
    return message_factory.GetMessageClass(pool.FindMessageTypeByName(name))


def message(name, **fields):
    return cls(PREFIX + name)(**fields)


def handlers(executor):
    methods = {}
    for method in ("SubmitAction", "SubscribeActions", "GetActionTypes", "UpdateAction",
                   "CancelActions", "PauseExecutor", "ResumeExecutor"):
        handler = (grpc.unary_stream_rpc_method_handler if method == "SubscribeActions"
                   else grpc.unary_unary_rpc_method_handler)
        methods[method] = handler(
            getattr(executor, method),
            request_deserializer=cls(PREFIX + method + "Request").FromString,
            response_serializer=lambda response: response.SerializeToString())
    return grpc.method_handlers_generic_handler(SERVICE, methods)
