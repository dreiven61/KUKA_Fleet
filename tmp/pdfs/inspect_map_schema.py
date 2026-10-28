from zipfile import ZipFile
import re
z=ZipFile(r'C:\work\KUKA\SW\KUKA.AMR_Fleet_2.15.1\STANDARD-API-2.15.5-jar-with-dependencies.jar')
for suffix in ['NodeFunctionGto','FullFunctionNodeGto','NodeFunctionType','MapFloorGto','NodeGto']:
    n=f'com/kuka/rcs/bd/grpc/map/{suffix}.class'
    print(n)
    words=re.findall(rb'[A-Za-z][A-Za-z0-9_]{3,100}',z.read(n))
    print(' '.join(dict.fromkeys(s.decode() for s in words if s.startswith(b'get') or b'POINT' in s)))
