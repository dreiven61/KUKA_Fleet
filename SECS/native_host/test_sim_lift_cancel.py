"""Authorized SIM01 lift interruption probe. Default is NO execution.

Uses simulator execution log as the timing trigger and Fleet REST FORCE for
the capability test. This does not claim to validate the SECS report pipeline.
No configuration, carrier, history or journal deletion; no mutation retry.
"""
import argparse
import datetime as dt
import json
import pathlib
import re
import subprocess
import threading
import time
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--execute-sim', action='store_true')
    parser.add_argument('--stage', choices=['pickup', 'drop'], required=True)
    parser.add_argument('--config', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--dest', required=True)
    parser.add_argument('--carried-drop', action='store_true', help='Test lowering the carrier already attached by the previous probe')
    options = parser.parse_args()
    if not options.execute_sim:
        parser.error('Live virtual Fleet test requires --execute-sim')
    for port in (options.source, options.dest):
        if not re.fullmatch(r'PORT_(84|86|89|90|93|94|95|96|97|98)', port):
            parser.error('Existing work ports only')
    if options.source == options.dest:
        parser.error('Different source/destination required')
    if options.carried_drop and options.stage != 'drop':
        parser.error('--carried-drop requires --stage drop')
    out = pathlib.Path(options.output)
    out.mkdir(parents=True, exist_ok=True)
    token = json.loads(pathlib.Path(options.config).read_text(encoding='utf-8-sig'))['authorization']
    if not re.fullmatch('[0-9a-fA-F]{64}', token):
        raise RuntimeError('Invalid local authorization')
    writer = (out / 'events.jsonl').open('w', encoding='utf-8', buffering=1)
    lock = threading.Lock()

    def record(kind, data):
        with lock:
            writer.write(json.dumps(dict(time=dt.datetime.now().astimezone().isoformat(), mono=time.monotonic(), kind=kind, data=data), ensure_ascii=False) + '\n')

    def api(path, body):
        req = urllib.request.Request('http://192.168.0.45:10870/interfaces/api/' + path,
            json.dumps(body).encode(), {'Authorization': token, 'Content-Type': 'application/json'}, method='POST')
        # No POST replay after a response failure. The caller retains identity.
        with urllib.request.urlopen(req, timeout=8) as response:
            return json.load(response)

    def ok(r):
        return r.get('success') is True and r.get('code') in (None, '0', 0)

    def at(c, port):
        return c.get('nodeCode') in ('lay01-dist1-' + port[5:], 'SECS_ST_' + port[5:])

    robots = api('amr/robotQuery', {})
    record('preflight-robots', robots)
    rows = robots.get('data', [])
    if not ok(robots) or len(rows) != 3 or any(r.get('buildingCode') != 'SIM01' or r.get('robotType') != 'KMP1500I' or str(r.get('robotId')) not in ('1', '2', '3') or r.get('missionCode') for r in rows):
        raise RuntimeError('Expected three existing SIM01 virtual AMRs with no active work')
    state = api('mes-secs/status', {})
    record('preflight-secs', state)
    if any(j['phase'] in ('ACTIVE', 'UNCERTAIN') for j in state['state']['jobs']):
        raise RuntimeError('Unresolved native job; no test submitted')
    cargo = api('amr/containerQueryAll', {})
    record('preflight-cargo', cargo)
    carrier = 'SIM_CARGO_001'
    selected = [c for c in cargo.get('data', []) if c['containerCode'] == carrier]
    expected_carry = '1' if options.carried_drop else '0'
    if not ok(cargo) or len(selected) != 1 or str(selected[0]['isCarry']) != expected_carry or str(selected[0]['inMapStatus']) != '1' or (not options.carried_drop and not at(selected[0], options.source)):
        raise RuntimeError('Test carrier not unloaded at requested source')
    if any(str(c['inMapStatus']) == '1' and at(c, options.dest) for c in cargo['data']):
        raise RuntimeError('Test destination occupied')

    command = 'LIFT_' + options.stage.upper() + '_' + dt.datetime.now().strftime('%H%M%S')
    # Exact command is written before the sole submission attempt.
    request = dict(orgId='UNIVERSAL', requestId=command, missionCode=command, missionType='RACK_MOVE',
        robotType='LIFT', robotIds=['1', '2', '3'], robotModels=['KMP1500I'], priority=1,
        containerCode=carrier, containerModelCode=selected[0]['containerModelCode'], lockRobotAfterFinish=False,
        missionData=[dict(sequence=n, position='lay01-dist1-' + port[5:], type='NODE_POINT', passStrategy='AUTO',
            actionType=action, waitingMillis=0, actionConfirm=False, actionInform=False)
            for n, port, action in [(1, options.source, 'UP_CONTAINER'), (2, options.dest, 'DOWN_CONTAINER')]])
    if options.carried_drop:
        owners = [r for r in rows if r.get('containerCode') == carrier and r.get('liftStatus') == 1 and r.get('status') == 3 and r.get('occupyStatus') == 0]
        if len(owners) != 1:
            raise RuntimeError('Expected one idle AMR actually carrying the test carrier')
        if owners[0].get('nodeCode') != 'lay01-dist1-' + options.source[5:]:
            raise RuntimeError('Carrying AMR is not at the requested start station')
        request['robotIds'] = [str(owners[0]['robotId'])]
        # Retain the known two-action carrier context: Fleet's single DOWN
        # request loses rackCode. The initial UP on an already raised virtual
        # lift keeps the existing carrier attached, then the drop is tested.
    record('test-request', dict(stage=options.stage, request=request))
    signal = threading.Event()
    context = dict(vehicle='', pulse=None)
    target = 'lay01-dist1-' + (options.source if options.stage == 'pickup' else options.dest)[5:]
    marker = 'msg type: 0x0312' if options.stage == 'pickup' else 'msg type: 0x0314'
    tail = subprocess.Popen(['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=8', 'servostar_linux',
        'tail', '-n', '0', '--sleep-interval=0.02', '-F'] + ['/opt/server/logs/simulator/{0}/{0}_info.log'.format(i) for i in (1, 2, 3)],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))

    def reader():
        for line in tail.stdout:
            record('simulator', line.rstrip())
            if marker in line and context['vehicle'] and ('[' + context['vehicle'] + '][[') in line and not signal.is_set():
                context['pulse'] = dict(line=line.rstrip(), receivedMono=time.monotonic())
                signal.set()

    thread = threading.Thread(target=reader, daemon=True)
    thread.start()
    try:
        time.sleep(.3)
        if tail.poll() is not None:
            raise RuntimeError('Simulator timing observer unavailable; no test submitted')
        accepted = api('amr/submitMission', request)
        record('submit-reply', accepted)
        if not ok(accepted):
            raise RuntimeError('Fleet rejected test; no replay')
        print('SUBMITTED', command, options.source, options.dest, flush=True)
        limit = time.monotonic() + 180
        last = None
        sent = False
        while time.monotonic() < limit:
            observed = api('amr/robotQuery', {})
            if not ok(observed):
                raise RuntimeError('Cannot correlate target AMR')
            candidates = [r for r in observed['data'] if r.get('missionCode') == command]
            if len(candidates) == 1:
                robot = candidates[0]
                context['vehicle'] = str(robot['robotId'])
                if robot != last:
                    record('robot', robot)
                    last = robot
                if robot.get('nodeCode') == target and str(robot.get('rcsMissionCode', '')).startswith('C-') and robot.get('rcsMissionStatus') == 9:
                    context['vehicle'] = str(robot['robotId'])
                    if signal.is_set():
                        lag = 1000 * (time.monotonic() - context['pulse']['receivedMono'])
                        if lag > 700:
                            raise RuntimeError('Missed 1-second lift window; no late cancel sent')
                        record('trigger', dict(command=command, stage=options.stage, pulse=context['pulse'], robot=robot, receiptToSendMs=lag))
                        print('TRIGGER', command, 'AMR', context['vehicle'], 'delay_ms', round(lag, 1), flush=True)
                        sent = True
                        reply = api('amr/missionCancel', dict(requestId=command + '_ABORT', missionCode=command, cancelMode='FORCE', reason='Authorized virtual lift interruption test'))
                        record('cancel-reply', reply)
                        print('FORCE_REPLY', json.dumps(reply, ensure_ascii=False), flush=True)
                        break
            time.sleep(.025)
        if not sent:
            raise RuntimeError('Lift start not captured; inspect existing command, do not resubmit')
        for i in range(80):
            record('after-robot', api('amr/robotQuery', {'robotId': context['vehicle']}))
            record('after-job', api('amr/jobQuery', {'jobCode': command, 'limit': 2}))
            if i % 10 == 0:
                record('after-cargo', api('amr/containerQueryAll', {}))
            time.sleep(.125)
        final = dict(command=command, vehicle=context['vehicle'], stage=options.stage,
            job=api('amr/jobQuery', {'jobCode': command, 'limit': 2}),
            robot=api('amr/robotQuery', {'robotId': context['vehicle']}), cargo=api('amr/containerQueryAll', {}),
            secs=api('mes-secs/status', {}), output=str(out))
        record('final', final)
        (out / 'result.json').write_text(json.dumps(final, ensure_ascii=False, indent=2), encoding='utf-8')
        print('FINAL', json.dumps(dict(command=command, stage=options.stage, robot=final['robot'].get('data'), job=final['job'].get('data'), output=str(out)), ensure_ascii=False), flush=True)
    finally:
        tail.terminate()
        tail.wait(timeout=5)
        thread.join(timeout=3)
        writer.close()


if __name__ == '__main__':
    main()
