"""Build IB-SEM review fixtures only. No sockets, HTTP calls, or live configuration changes."""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parent / 'reference'

def item(kind, value):
    return {'type': kind, 'value': value}

def L(*children):
    return item('L', list(children))

def A(value):
    return item('A', value)

def U2(value):
    return item('U2', value)

def U4(value):
    return item('U4', value)

def event(ceid, rptid, payload):
    return L(U4(1), U2(ceid), L(L(U2(rptid), payload)))

def transfer(ceid, command, carrier, source, dest, location, result):
    # MessageSpec p22: RPTID 2 has THREE values, including TransferCompleteInfo.
    return event(ceid, 2, L(A(command), L(L(L(A(carrier), A(source), A(dest)), A(location))), U2(result)))

def validate(node):
    kind, value = node['type'], node['value']
    if kind == 'L':
        assert isinstance(value, list)
        for child in value:
            validate(child)
    elif kind == 'A':
        assert isinstance(value, str) and value.isascii()
    else:
        assert isinstance(value, int) and not isinstance(value, bool)
        assert 0 <= value < {'B': 256, 'U2': 65536, 'U4': 4294967296}[kind]

def sml(node, level=0):
    kind, value = node['type'], node['value']
    indent = '  ' * level
    if kind == 'L':
        if not value:
            return indent + '<L [0]>'
        return indent + f'<L [{len(value)}]\n' + '\n'.join(sml(c, level + 1) for c in value) + '\n' + indent + '>'
    if kind == 'A':
        return indent + f'<A [{len(value.encode("ascii"))}] "{value}">'
    return indent + f'<{kind} [1] {value}>'

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    command, carrier, source, dest = 'REFERENCE_ONLY_001', 'SIM_CARGO_001', 'PORT_89', 'PORT_97'
    samples = {
        'S6F11_208_TransferInitiated': (6, 11, True, transfer(208, command, carrier, source, dest, source, 0)),
        # Scenario p17: 211 precedes AcquireStarted/CarrierInstalled. At this
        # example pickup-start snapshot the carrier remains at the source port.
        'S6F11_211_Transferring': (6, 11, True, transfer(211, command, carrier, source, dest, source, 0)),
        'S6F11_207_TransferCompleted_OK': (6, 11, True, transfer(207, command, carrier, source, dest, dest, 0)),
        'S6F11_207_SourceInterlock_Error5': (6, 11, True, transfer(207, command, carrier, source, dest, source, 5)),
        'S6F11_207_DestInterlock_Error6': (6, 11, True, transfer(207, command, carrier, source, dest, '1', 6)),
        'S6F11_604_VehicleAssigned': (6, 11, True, event(604, 5, L(A('1'), A(command), L(A(source)), L(A(carrier))))),
        # Alarm ID 900001 is an EXAMPLE LOCAL TEST ID, not assigned by SKI or KUKA.
        'S5F1_AlarmSet_EXAMPLE_ID': (5, 1, True, L(item('B', 0x86), U4(900001), A('TEST_ONLY_VEHICLE_WARNING'))),
        'S5F1_AlarmClear_EXAMPLE_ID': (5, 1, True, L(item('B', 0x06), U4(900001), A('TEST_ONLY_VEHICLE_WARNING'))),
        'S6F11_702_UnitAlarmSet_EXAMPLE_ID': (6, 11, True, event(702, 6, L(A('1'), U4(900001), A('TEST_ONLY_VEHICLE_WARNING')))),
        'S6F11_701_UnitAlarmClear_EXAMPLE_ID': (6, 11, True, event(701, 6, L(A('1'), U4(900001), A('TEST_ONLY_VEHICLE_WARNING')))),
        'S2F50_Accepted_Profile4': (2, 50, False, L(item('B', 4), L())),
        'S2F50_Duplicate_Profile5': (2, 50, False, L(item('B', 5), L())),
        'S6F12_EventAck': (6, 12, False, item('B', 0)),
        'S5F2_AlarmAck': (5, 2, False, item('B', 0)),
    }
    for name, (stream, function, wait, body) in samples.items():
        validate(body)
        doc = {'referenceOnly': True, 'stream': stream, 'function': function, 'wait': wait, 'body': body}
        (OUT / (name + '.json')).write_text(json.dumps(doc, indent=2), encoding='utf-8')
        (OUT / (name + '.sml')).write_text(f'{name}: S{stream}F{function}' + (' W' if wait else '') + '\n' + sml(body) + '.\n', encoding='utf-8')
    completed = samples['S6F11_207_TransferCompleted_OK'][3]
    values = completed['value'][2]['value'][0]['value'][1]['value']
    assert len(values) == 3
    assert values[0]['value'] == command
    assert values[1]['value'][0]['value'][0]['value'][2]['value'] == dest
    assert values[1]['value'][0]['value'][1]['value'] == dest
    assert values[2] == U2(0)
    assert samples['S6F11_207_DestInterlock_Error6'][3]['value'][2]['value'][0]['value'][1]['value'][1]['value'][0]['value'][1]['value'] == '1'
    assert samples['S5F1_AlarmSet_EXAMPLE_ID'][3]['value'][0]['value'] & 0x80
    assert not samples['S5F1_AlarmClear_EXAMPLE_ID'][3]['value'][0]['value'] & 0x80
    print(f'PASS: {len(samples)} reference message trees validated. No messages sent.')

if __name__ == '__main__':
    main()
