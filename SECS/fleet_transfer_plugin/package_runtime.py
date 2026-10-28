"""Bundle only the supplied SDK's gRPC client runtime, not its Spring plugin.

Interface Platform isolates other plugins' classes. A bare custom JAR cannot
assume STANDARD-API's private gRPC implementation is on its parent classpath.
"""
import sys
import zipfile

sdk, output, *secs = sys.argv[1:]
prefixes = ('io/grpc/', 'io/perfmark/', 'com/google/', 'com/kuka/wcs/grpc/',
            'com/kuka/common/grpc/', 'javax/annotation/', 'org/checkerframework/',
            'org/codehaus/mojo/animal_sniffer/')
services = ('META-INF/services/io.grpc.',)
with zipfile.ZipFile(sdk) as source, zipfile.ZipFile(output, 'a', zipfile.ZIP_DEFLATED) as target:
    existing = set(target.namelist())
    count = 0
    for entry in source.infolist():
        if entry.is_dir() or entry.filename in existing:
            continue
        if entry.filename.startswith(prefixes + services):
            content=source.read(entry)
            if entry.filename.startswith(services):
                content=('\n'.join(line for line in content.decode('utf-8').splitlines()
                                  if line.strip().startswith('io.grpc.'))+'\n').encode('utf-8')
                if entry.filename=='META-INF/services/io.grpc.NameResolverProvider':
                    content=b'io.grpc.internal.DnsNameResolverProvider\n'
            target.writestr(entry, content)
            count += 1
    assert 'io/grpc/Channel.class' in target.namelist()
    assert 'com/kuka/wcs/grpc/mission/WcsMissionServiceGrpc.class' in target.namelist()
    print(f'Bundled {count} gRPC runtime files from the supplied SDK.')
if secs:
    with zipfile.ZipFile(secs[0]) as source, zipfile.ZipFile(output, 'a', zipfile.ZIP_DEFLATED) as target:
        count=0
        for entry in source.infolist():
            if not entry.is_dir() and entry.filename.startswith('com/kuka/ecs/common/transport/secs/'):
                target.writestr(entry,source.read(entry));count+=1
        assert count>100
        print(f'Bundled {count} SECS transport files from the installed Fleet library. No vendor server patches.')
