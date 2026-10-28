"""Read-only Fleet gRPC GetData probe. No action or configuration RPCs."""
from pathlib import Path
import json
import sys

BASE = Path(__file__).parent
sys.path.insert(0, str(BASE / 'grpc_readonly_deps'))
sys.stdout.reconfigure(encoding='utf-8')

import grpc
from grpc_tools import protoc
import grpc_tools
from google.protobuf import descriptor_pb2, descriptor_pool, message_factory, json_format

APIS = BASE / 'secs_service_package/server/config/apis'
descriptor_path = BASE / 'fleet_readonly_descriptors.pb'
sources = [
    'kuka/dataprovider/v1alpha1/data_provider_service.proto',
    'kuka/mobilerobotics/robotmanager/v1alpha1/robot_data.proto',
    'kuka/mobilerobotics/capacity/v1alpha1/transport_capacity.proto',
    'kuka/mobilerobotics/station/v1alpha1/station_data.proto',
    'kuka/mobilerobotics/payload/v1alpha1/payload_data.proto',
]
result = protoc.main([
    'protoc', f'-I{APIS}',
    f'-I{Path(grpc_tools.__file__).parent / "_proto"}',
    '--include_imports', f'--descriptor_set_out={descriptor_path}', *sources,
])
if result:
    raise SystemExit(result)

pool = descriptor_pool.DescriptorPool()
fds = descriptor_pb2.FileDescriptorSet.FromString(descriptor_path.read_bytes())
for fd in fds.file:
    pool.Add(fd)

def cls(name):
    return message_factory.GetMessageClass(pool.FindMessageTypeByName(name))

prefix = 'kuka.dataprovider.v1alpha1.'
channel = grpc.insecure_channel('192.168.0.45:50090')

def read_rpc(method, request):
    response_cls = cls(prefix + method + 'Response')
    call = channel.unary_unary(
        '/' + prefix + 'DataProviderService/' + method,
        request_serializer=lambda msg: msg.SerializeToString(),
        response_deserializer=response_cls.FromString,
    )
    return call(request, timeout=8)

types = read_rpc('GetDataTypes', cls(prefix + 'GetDataTypesRequest')())
output = {'endpoint': '192.168.0.45:50090', 'dataTypes': list(types.data_types)}
robot_type = next((t for t in types.data_types if t.endswith('robotmanager.v1alpha1.RobotData')), None)
if robot_type:
    response = read_rpc('GetData', cls(prefix + 'GetDataRequest')(data_type=robot_type))
    name = response.data.type_url.rsplit('/', 1)[-1]
    body = cls(name).FromString(response.data.value)
    output['robotData'] = json_format.MessageToDict(body, preserving_proto_field_name=True)
    output['robotCount'] = len(body.robots)
else:
    output['robotDataError'] = 'RobotData not exposed by provider'

for suffix, key in [
    ('station.v1alpha1.StationData', 'stationData'),
    ('payload.v1alpha1.PayloadData', 'payloadData'),
]:
    data_type = next((t for t in types.data_types if t.endswith(suffix)), None)
    if data_type:
        response = read_rpc('GetData', cls(prefix + 'GetDataRequest')(data_type=data_type))
        name = response.data.type_url.rsplit('/', 1)[-1]
        output[key] = json_format.MessageToDict(cls(name).FromString(response.data.value), preserving_proto_field_name=True)

(BASE / 'robotdata_readonly_result.json').write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(output, ensure_ascii=False, indent=2))
channel.close()
