"""Re-run existing offline checks without rebuilding the delivered JAR."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone, timedelta

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
PLUGIN = ROOT / 'SECS/fleet_transfer_plugin'
JAVA = Path(r'C:\Program Files\Java\jdk-21\bin')
SDK = ROOT.parent / 'SW/KUKA.AMR_Fleet_2.15.1/STANDARD-API-2.15.5-jar-with-dependencies.jar'
SECS = PLUGIN / 'lib/secs-2.11.0.jar'
JAR = PLUGIN / 'build/servostar-mes-transfer-1.5.2.jar'
CLASSES = OUT / 'classes'
TESTS = OUT / 'tests'
CLASSES.mkdir(exist_ok=True)
TESTS.mkdir(exist_ok=True)
results = []
before = hashlib.sha256(JAR.read_bytes()).hexdigest()

def run(name, args):
    log = OUT / (name + '.log')
    with log.open('w', encoding='utf-8') as output:
        proc = subprocess.run([str(x) for x in args], cwd=ROOT, stdout=output,
                              stderr=subprocess.STDOUT, encoding='utf-8')
    text = log.read_text(encoding='utf-8', errors='replace')
    passes = [line for line in text.splitlines() if line.startswith('PASS:')]
    row = {'check': name, 'exitCode': proc.returncode, 'log': str(log), 'passes': passes}
    results.append(row)
    print(json.dumps(row, ensure_ascii=True), flush=True)
    if proc.returncode:
        print(text[-4000:], flush=True)
        save()
        raise SystemExit(proc.returncode)

def save():
    data = {'checkedKst': datetime.now(timezone(timedelta(hours=9))).isoformat(timespec='seconds'),
            'scope': 'local fixtures/loopback; no live Fleet calls or motions',
            'deliveredJarHashBefore': before,
            'deliveredJarHashAfter': hashlib.sha256(JAR.read_bytes()).hexdigest(),
            'results': results}
    (OUT / 'local_recheck_results.json').write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')

cp_compile = ';'.join(map(str, (SDK, SECS)))
run('compile_production', [JAVA/'javac.exe', '--release', '8', '-encoding', 'UTF-8', '-proc:none',
    '-cp', cp_compile, '-d', CLASSES, *sorted((PLUGIN/'src').rglob('*.java'))])
run('compile_tests', [JAVA/'javac.exe', '-encoding', 'UTF-8', '-proc:none',
    '-cp', cp_compile+';'+str(CLASSES), '-d', TESTS, *sorted((PLUGIN/'test').glob('*.java'))])
cp = ';'.join(map(str, (SDK, SECS, CLASSES, TESTS)))
java = [JAVA/'java.exe', '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory', '-cp', cp]
run('ColdStartCatalogTest', java+['servostar.fleet.transfer.ColdStartCatalogTest', PLUGIN])
fixtures = re.findall(r'FixtureRunner servostar\.fleet\.transfer\.(\w+)', (PLUGIN/'build.ps1').read_text(encoding='utf-8-sig'))
for fixture in fixtures:
    run(fixture, java+['servostar.fleet.transfer.FixtureRunner', 'servostar.fleet.transfer.'+fixture, PLUGIN])
run('PluginLoadTest', [JAVA/'java.exe', '-Dlog4j2.disable.jmx=true',
    '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory',
    '-Dservostar.transfer.offlineTest=true', '-cp', str(SDK)+';'+str(TESTS),
    'servostar.fleet.transfer.PluginLoadTest', JAR, SDK])
run('dotnet_tests', ['dotnet', 'run', '--project', ROOT/'VirtualMCS/Tests/KukaMcs.Tests.csproj', '-c', 'Release'])
run('package_integrity', [sys.executable, PLUGIN/'verify_package.py', JAR, '--version', '1.5.2',
    '--baseline', PLUGIN/'build/servostar-mes-transfer-1.5.0.jar'])
save()
assert before == hashlib.sha256(JAR.read_bytes()).hexdigest(), 'Delivered JAR changed during audit'
print('AUDIT_LOCAL_RECHECK_COMPLETE', flush=True)
