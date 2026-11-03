"""HSMS test peer. No automatic transport/control replay; probe mode is read-only.

Use only after the normal MCS disconnects. All incoming reports and deliberate
ACK fault decisions are journalled without Fleet credentials.
"""
import argparse,datetime,json,pathlib,socket,struct,time

def item(kind,value): return [kind,value]
def L(*values): return item('L',list(values))
def A(value): return item('A',str(value))
def U2(value): return item('U2',[int(value)])
def U4(value): return item('U4',[int(value)])
def B(value): return item('B',[int(value)])
FORMATS={'L':0,'B':0x20,'A':0x40,'I1':0x64,'I2':0x68,'I4':0x70,'I8':0x60,'U1':0xa4,'U2':0xa8,'U4':0xb0,'U8':0xa0}
def encode(x):
    kind,value=x
    if kind=='L': payload=b''.join(encode(v) for v in value);length=len(value)
    elif kind=='A':payload=value.encode('ascii');length=len(payload)
    else:
        size={'B':1,'U1':1,'U2':2,'U4':4,'U8':8,'I1':1,'I2':2,'I4':4,'I8':8}[kind]
        payload=b''.join(int(v).to_bytes(size,'big',signed=kind.startswith('I')) for v in value);length=len(payload)
    n=max(1,(length.bit_length()+7)//8)
    return bytes([FORMATS[kind]|n])+length.to_bytes(n,'big')+payload
def decode(data,offset=0):
    tag=data[offset];n=tag&3;offset+=1
    if n==0:raise ValueError('Invalid SECS length')
    length=int.from_bytes(data[offset:offset+n],'big');offset+=n
    kind=next((k for k,v in FORMATS.items() if v==tag&0xfc),None)
    if kind is None:raise ValueError('Unsupported SECS format '+hex(tag))
    if kind=='L':
        values=[]
        for _ in range(length): v,offset=decode(data,offset);values.append(v)
        return L(*values),offset
    raw=data[offset:offset+length]
    if len(raw)!=length:raise ValueError('Truncated SECS item')
    if kind=='A':value=raw.decode('ascii')
    else:
        size={'B':1,'U1':1,'U2':2,'U4':4,'U8':8,'I1':1,'I2':2,'I4':4,'I8':8}[kind]
        if length%size:raise ValueError('Invalid scalar length')
        value=[int.from_bytes(raw[i:i+size],'big',signed=kind.startswith('I')) for i in range(0,length,size)]
    return item(kind,value),offset+length

class Peer:
    def __init__(self,output,host='192.168.0.45',port=5556):
        self.output=pathlib.Path(output);self.output.parent.mkdir(parents=True,exist_ok=True)
        self.host=host;self.port=port;self.socket=None;self.sequence=0x71000000;self.events=[];self.ack_policy=lambda message:0
    def record(self,kind,value):
        with self.output.open('a',encoding='utf-8') as f:f.write(json.dumps(dict(clientTime=datetime.datetime.now().astimezone().isoformat(),kind=kind,value=value),ensure_ascii=False)+'\n')
    def send(self,stream,function,body=None,wait=False,system=None,stype=0):
        if system is None:self.sequence+=1;system=self.sequence
        session=0xffff if stype else 3000
        header=struct.pack('>HBBBBI',session,stream|(128 if wait else 0),function,0,stype,system)
        payload=b'' if body is None else encode(body)
        self.socket.sendall(struct.pack('>I',len(header)+len(payload))+header+payload)
        self.record('TX',dict(stream=stream,function=function,wait=wait,system=system,stype=stype,body=body));return system
    def read_exact(self,n):
        result=b''
        while len(result)<n:
            more=self.socket.recv(n-len(result))
            if not more:raise ConnectionError('HSMS peer closed')
            result+=more
        return result
    def receive(self,timeout=8):
        self.socket.settimeout(timeout);length=struct.unpack('>I',self.read_exact(4))[0]
        if length<10 or length>2*1024*1024:raise ValueError('Invalid HSMS packet length')
        raw=self.read_exact(length);session,stream,function,ptype,stype,system=struct.unpack('>HBBBBI',raw[:10])
        body=None
        if raw[10:]:
            body,end=decode(raw,10)
            if end!=len(raw):raise ValueError('Trailing SECS data')
        message=dict(session=session,stream=stream&127,function=function,wait=bool(stream&128),ptype=ptype,stype=stype,system=system,body=body)
        self.record('RX',message)
        return message
    def connect(self):
        self.socket=socket.create_connection((self.host,self.port),timeout=8)
        sid=self.send(0,0,stype=1)
        selected=self.wait_response(sid)
        if selected['stype']!=2 or selected['function']!=0:raise RuntimeError('HSMS select rejected')
        reply=self.primary(1,13,L())
        if (reply['stream'],reply['function'])!=(1,14):raise RuntimeError('S1F13 rejected')
        return reply
    def wait_response(self,system,timeout=8):
        deadline=time.monotonic()+timeout
        while True:
            message=self.receive(max(.1,deadline-time.monotonic()))
            if message['system']==system and not message['wait']:return message
            self.handle(message)
            if time.monotonic()>deadline:raise TimeoutError('HSMS secondary timeout')
    def primary(self,stream,function,body,timeout=8):
        system=self.send(stream,function,body,wait=True);return self.wait_response(system,timeout)
    def handle(self,message):
        if message['stype']==5:self.send(0,0,system=message['system'],stype=6);return
        if message['wait'] and (message['stream'],message['function']) in [(6,11),(5,1)]:
            self.events.append(message);decision=self.ack_policy(message)
            if decision is None:self.record('ACK_DROPPED',message['system']);return
            self.send(message['stream'],message['function']+1,B(decision),system=message['system'])
    def query(self,svids=(23,25,58,21)):
        started=time.monotonic();reply=self.primary(1,3,L(*(U2(v) for v in svids)))
        reply['latencyMs']=round((time.monotonic()-started)*1000,3);self.record('QUERY_RESULT',reply);return reply
    def close(self):
        if self.socket:
            self.socket.close();self.socket=None;self.record('DISCONNECTED',{})

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',required=True);p.add_argument('--queries',type=int,default=1)
    a=p.parse_args();peer=Peer(a.output)
    try:
        peer.connect()
        for _ in range(a.queries):
            r=peer.query();print(json.dumps(dict(stream=r['stream'],function=r['function'],body=r['body'],latencyMs=r['latencyMs']),ensure_ascii=False),flush=True)
    finally:peer.close()
