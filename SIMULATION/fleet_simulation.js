/* KUKA Fleet 2.15: same-origin simulator controller, no external libraries.
 * Run in the already authenticated Fleet tab. Never creates API credentials.
 */
(function (root, factory) {
  const lib = factory();
  if (typeof module === 'object' && module.exports) module.exports = lib;
  else lib.install(root);
})(typeof window === 'undefined' ? globalThis : window, function () {
  'use strict';
  const PLAN = Object.freeze({
    mapCode: 'lay01', floorNumber: 'dist1', modelCode: 'SIM_TRAY_001',
    pollMs: 2500, dwellMs: 2500, missionTimeoutMs: 300000,
    robots: [
      {id:'90101', tray:'SIM_CARGO_001', start:'68', ends:['89','97']},
      {id:'90102', tray:'SIM_CARGO_002', start:'69', ends:['84','86']},
      {id:'90103', tray:'SIM_CARGO_003', start:'67', ends:['94','96']}
    ]
  });
  const PATH = {
    maps:'/apibd/api/v1/data/map-building/select/all',
    types:'/apibd/api/v1/data/mobile-robot-type/select/all',
    models:'/apibd/api/v1/data/container-model/select/all',
    sims:'/apisim/api/v1/simu/getRobotStatus',
    createSim:'/apisim/api/v1/simu/insertNewRobot',
    position:'/apimm/api/robot/location',
    simPosition:'/apisim/api/v1/simu/ModifyRobotInfo',
    createContainer:'/apibd/api/v1/data/container/insert',
    dispatch:'/apimm/api/mission/dispatch'
  };
  const clone = x => JSON.parse(JSON.stringify(x));
  const eq = (a,b) => String(a) === String(b);
  const encode = encodeURIComponent;
  function rows(value) {
    const data = value && Object.prototype.hasOwnProperty.call(value,'data') ? value.data : value;
    if (Array.isArray(data)) return data;
    if (data && Array.isArray(data.content)) return data.content;
    if (data == null) return [];
    throw new Error('Fleet 목록 응답 형식이 예상과 다릅니다. 반복을 중지했습니다.');
  }
  function dataOf(value) {
    return value && Object.prototype.hasOwnProperty.call(value,'data') ? value.data : value;
  }
  function nodeNumber(record) {
    return String(record?.stayNodeNumber ?? record?.nodeNumber ?? record?.lastNodeNumber ?? '');
  }
  class FleetDemo {
    constructor({request,storage,onChange=()=>{},now=()=>Date.now(),delay=ms=>new Promise(r=>setTimeout(r,ms)),plan=PLAN}) {
      this.request=request; this.storage=storage; this.onChange=onChange;
      this.now=now; this.delay=delay; this.plan=clone(plan);
      this.key='kuka-fleet-simulation-v1-'+plan.mapCode+'-'+plan.floorNumber;
      this.saved=JSON.parse(storage.getItem(this.key)||'null')||{robots:{},containers:{},missions:{},counts:{}};
      for (const k of ['robots','containers','missions','counts']) this.saved[k] ||= {};
      this.running=false; this.busy=false; this.logs=[]; this.error=''; this.setupReady=false;
      this.status=Object.fromEntries(plan.robots.map(r=>[r.id,'준비 전']));
      this.roster=[]; this.nodes={}; this.model=null; this.type=null; this.map=null;
    }
    save() {this.storage.setItem(this.key,JSON.stringify(this.saved)); this.emit();}
    emit() {this.onChange(this.snapshot());}
    snapshot() {return {running:this.running,busy:this.busy,ready:this.setupReady,error:this.error,
      statuses:clone(this.status),counts:clone(this.saved.counts),missions:clone(this.saved.missions),logs:[...this.logs]};}
    log(text) {this.logs.unshift(new Date(this.now()).toLocaleTimeString()+' '+text); this.logs=this.logs.slice(0,70); this.emit();}
    async call(path,method='GET',body,allowMissing=false) {
      const response=await this.request(path,method,body);
      if (response && !Array.isArray(response)) {
        if (allowMissing && response.success===false && /data_not_exist|not_found/i.test(String(response.code))) return {data:null};
        if (response.success===false || response.succ===false) throw new Error(String(response.message||response.msg||response.code));
        if (response.code && ['NOT_LOGIN','TOKEN_EXPIRED','NO_ACCESS','LICENSE_HAS_EXPIRED'].includes(response.code)) throw new Error(response.code);
        if (response.code!=null && !['0','200','OK'].includes(String(response.code)) && response.success!==true && response.succ!==true) throw new Error(String(response.message||response.msg||response.code));
      }
      return response;
    }
    async simRecords(ids) {return rows(await this.call(PATH.sims,'POST',ids));}
    async assertSim(id) {
      const sims=await this.simRecords([id]);
      if (!sims.some(r=>eq(r.robotId,id))) throw new Error(id+': 시뮬레이터 등록이 확인되지 않아 명령을 차단했습니다.');
    }
    async robot(id) {
      return dataOf(await this.call('/apibd/api/v1/data/mobile-robot/getRobotByRobotId?robotId='+encode(id),'GET',undefined,true));
    }
    async container(code) {
      return dataOf(await this.call('/apibd/api/v1/data/container/select/code/'+encode(code),'GET',undefined,true));
    }
    async checkLayout() {
      const maps=rows(await this.call(PATH.maps));
      this.map=maps.find(m=>eq(m.mapCode,this.plan.mapCode));
      if (!this.map) throw new Error('lay01 레이아웃을 먼저 Enable하세요. 프로그램은 레이아웃을 자동 활성화하지 않습니다.');
      if (!this.map.buildingCode) throw new Error('레이아웃의 Building Code를 확인하지 못했습니다.');
      const rt=dataOf(await this.call('/apibd/api/v1/data/mobile-robot/getRealtimeInfo?floorNumber='+encode(this.plan.floorNumber)+'&isFirst=true&mapCode='+encode(this.plan.mapCode)));
      if (!rt || !Array.isArray(rt.robotRealtimeList)) throw new Error('현재 지도 로봇 목록을 확인하지 못했습니다.');
      this._lastRealtime=rt;
      const active=rt.robotRealtimeList;
      if (active.length) {
        const sim=await this.simRecords(active.map(r=>String(r.robotId)));
        const virtual=new Set(sim.map(r=>String(r.robotId)));
        const physical=active.filter(r=>!virtual.has(String(r.robotId)));
        if (physical.length) throw new Error('같은 지도에 실물 또는 가상 여부가 확인되지 않은 로봇이 있습니다: '+physical.map(r=>r.robotId).join(', '));
      }
      const labels=new Set(this.plan.robots.flatMap(r=>[r.start,...r.ends]));
      for (const label of labels) {
        const n=dataOf(await this.call('/apibd/api/v1/data/node/select/map/'+encode(this.plan.mapCode)+'/floor/'+encode(this.plan.floorNumber)+'/number/'+encode(label)));
        if (!n) throw new Error('노드 '+label+'가 없습니다. v2 지도를 가져오세요.');
        this.nodes[label]=n;
      }
      const types=rows(await this.call(PATH.types,'POST',{allType:true}));
      const models=rows(await this.call(PATH.models,'POST',{}));
      this.model=models.find(m=>eq(m.modelCode,this.plan.modelCode));
      if (!this.model || this.model.id==null) throw new Error('SIM_TRAY_001 모델을 먼저 생성하세요.');
      const typeCodes=String(this.model.robotTypeCode||this.model.robotType||'').split(',').map(s=>s.trim());
      this.type=types.find(t=>typeCodes.includes(String(t.robotTypeCode)) && /1500i/i.test(String(t.robotTypeCode)+' '+String(t.robotTypeName)));
      if (!this.type) this.type=types.find(t=>/^KMP1500I$/i.test(String(t.robotTypeCode)));
      if (!this.type || !(Number(this.type.robotLength)>0) || !(Number(this.type.robotWidth)>0)) throw new Error('KMP 1500i 로봇 타입과 크기를 확인하지 못했습니다.');
      // Preflight all model/endpoint compatibility before adding any data.
      if (typeCodes.filter(Boolean).length && !typeCodes.includes(String(this.type.robotTypeCode))) throw new Error('트레이 모델에 KMP 1500i 호환 타입을 설정하세요.');
      for (const r of this.plan.robots) for (const label of r.ends) {
        const n=this.nodes[label];
        const functions=n.fullFunctionNode||[];
        const raw=n.functionList||[];
        if (functions.length || raw.length) {
          const matches=functions.map(f=>f.mainFunction).concat(raw).filter(Boolean).filter(f=>Number(f.functionType)===1);
          if (!matches.length) throw new Error(label+': Workstation 설정이 없습니다.');
          if (!matches.some(f=>String(f.containerModel||'').split(',').includes(this.plan.modelCode))) throw new Error(label+': SIM_TRAY_001 모델 연결이 없습니다.');
        }
      }
    }
    async setup() {
      if (this.busy||this.running) throw new Error('현재 작업이 진행 중입니다.');
      this.busy=true; this.error=''; this.setupReady=false; this.emit();
      try {
        await this.checkLayout();
        const ids=this.plan.robots.map(r=>r.id);
        const sims=await this.simRecords(ids);
        // All ID conflicts are checked before the first mutation.
        for (const r of this.plan.robots) {
          const live=await this.robot(r.id);
          const known=this.saved.robots[r.id];
          const sim=sims.some(x=>eq(x.robotId,r.id));
          if ((live||sim) && !known) throw new Error(r.id+': 이미 사용 중인 ID입니다. 기존 로봇을 덮어쓰지 않습니다.');
          if (live && !sim) throw new Error(r.id+': 가상 로봇이 아니므로 중지했습니다.');
          const container=await this.container(r.tray);
          if (container && !this.saved.containers[r.tray]) throw new Error(r.tray+': 기존 컨테이너와 충돌합니다.');
          if (container && !eq(container.modelCode,this.plan.modelCode)) throw new Error(r.tray+': 모델이 다릅니다.');
          // Check that both route stations are free of someone else's containers.
          for (const label of r.ends) {
            if ((rtContainers(this._lastRealtime)||[]).some(c=>eq(c.stayNodeNumber,label)&&!eq(c.code,r.tray))) throw new Error(label+': 다른 컨테이너가 있습니다.');
          }
        }
        for (const r of this.plan.robots) {
          this.status[r.id]='가상 AMR 준비'; this.emit();
          await this.ensureRobot(r);
          await this.ensureContainer(r);
          this.status[r.id]='준비 완료'; this.emit();
        }
        this.setupReady=true;
        this.log('가상 AMR 3대와 가상 트레이 3개 준비 완료. Fleet Monitor에서 확인하세요.');
      } catch(e) {this.error=e.message; this.log('중지: '+e.message); throw e;}
      finally {this.busy=false; this.emit();}
    }
    async ensureRobot(r) {
      let sim=(await this.simRecords([r.id])).find(x=>eq(x.robotId,r.id));
      let live=await this.robot(r.id);
      const position=this.nodes[r.start];
      if (!sim) {
        if (live) throw new Error(r.id+': 기존 로봇이 있어 생성하지 않았습니다.');
        const previous=this.saved.robots[r.id];
        if (previous && previous.stage!=='create-pending') throw new Error(r.id+': 생성 기록은 있으나 서버에서 사라졌습니다. 상태를 확인하세요.');
        this.saved.robots[r.id]={stage:'create-pending'}; this.save();
        await this.call(PATH.createSim,'POST',{
          robotId:r.id,robotType:this.type.robotTypeCode,robotLen:Number(this.type.robotLength)*1000,
          robotWidth:Number(this.type.robotWidth)*1000,mapCode:this.plan.mapCode,floorNumber:this.plan.floorNumber,
          initInfo:{batteryLevel:100,posX:Number(position.xCoordinate),posY:Number(position.yCoordinate),theta:90}
        });
        await this.assertSim(r.id);
        this.saved.robots[r.id]={stage:'created'}; this.save();
      }
      await this.assertSim(r.id);
      // Registration can take a short time. Do not replay creation.
      for (let i=0;!live && i<15;i++) {await this.delay(1000);live=await this.robot(r.id);}
      if (!live) throw new Error(r.id+': 생성 후 Fleet 등록을 기다리는 중입니다. 준비 버튼으로 재확인하세요.');
      if (!eq(live.robotTypeCode,this.type.robotTypeCode)) throw new Error(r.id+': 로봇 타입 불일치');
      if ([1,'1','OFF_MAP','Off-map'].includes(live.status)) {
        if (this.saved.missions[r.id]) throw new Error(r.id+': 진행 기록이 있어 위치를 재설정하지 않습니다.');
        await this.call(PATH.position,'PUT',{buildingCode:this.map.buildingCode,floorNumber:this.plan.floorNumber,
          lastNodeNumber:Number(r.start),mapCode:this.plan.mapCode,robotId:r.id});
        await this.call(PATH.simPosition,'POST',{robotId:r.id,posX:Number(position.xCoordinate),posY:Number(position.yCoordinate)});
        await this.call('/apimm/api/robot/entrance?robotId='+encode(r.id)+'&withContainer=false','POST',null);
        for (let i=0;i<15;i++) {
          live=await this.robot(r.id);
          if (live && eq(live.mapCode,this.plan.mapCode)&&![1,'1'].includes(live.status)) break;
          await this.delay(1000);
        }
      }
      if (!eq(live.mapCode,this.plan.mapCode) || !eq(live.floorNumber,this.plan.floorNumber)||[1,'1',2,'2'].includes(live.status)) throw new Error(r.id+': 지도 진입을 확인하지 못했습니다.');
      this.saved.robots[r.id]={stage:'ready'};this.save();
    }
    async ensureContainer(r) {
      let c=await this.container(r.tray);
      if (c) {
        if (!eq(c.modelCode,this.plan.modelCode)||!eq(c.mapCode,this.plan.mapCode)||!eq(c.floorNumber,this.plan.floorNumber)||Number(c.inMapStatus)!==1) throw new Error(r.tray+': 기존 생성 상태를 확인하세요.');
      } else {
        this.saved.containers[r.tray]={stage:'create-pending'};this.save();
        await this.call(PATH.createContainer,'POST',{
          buildingCode:this.map.buildingCode,mapCode:this.plan.mapCode,floorNumber:this.plan.floorNumber,
          code:r.tray,modelId:Number(this.model.id),orientation:0,entranceNodeNumber:Number(r.ends[0]),
          enterNodeNumber:Number(r.ends[0]),defaultNodeNumber:-1,status:0,inMapStatus:1,
          checkCode:null,loadStatus:1,remark:'KUKA Fleet virtual demo'
        });
        c=await this.container(r.tray);
        if (!c || Number(c.inMapStatus)!==1) throw new Error(r.tray+': 생성 결과를 확인하지 못했습니다.');
      }
      this.saved.containers[r.tray]={stage:'ready'};this.save();
    }
    stop() {this.running=false;this.log('반복 중지 요청. 이미 전달된 운반 작업은 완료될 때까지 진행합니다.');}
    async start() {
      if (this.running||this.busy) throw new Error('현재 작업이 진행 중입니다.');
      if (!this.setupReady) throw new Error('먼저 가상 AMR·트레이 준비를 실행하세요.');
      this.busy=true;this.error='';this.emit();
      try {await this.checkLayout();for (const r of this.plan.robots) await this.assertSim(r.id);}
      finally {this.busy=false;this.emit();}
      this.running=true;this.log('3대 자동 왕복 운반 시작');
      this.loopPromise=Promise.all(this.plan.robots.map(r=>this.worker(r))).finally(()=>{this.running=false;this.emit();});
      return this.snapshot();
    }
    async worker(r) {
      try {
        while (this.running) {
          let pending=this.saved.missions[r.id];
          if (pending) {
            if (!await this.waitMission(r,pending)) break;
            this.saved.counts[r.id]=(this.saved.counts[r.id]||0)+1;
            delete this.saved.missions[r.id];this.save();
            this.log(r.id+' 운반 완료 ('+this.saved.counts[r.id]+'회)');
            if (!this.running) break;
            await this.delay(this.plan.dwellMs);
            if (!this.running) break;
          }
          await this.assertSim(r.id);
          const live=await this.robot(r.id),c=await this.container(r.tray);
          if (!live||!eq(live.mapCode,this.plan.mapCode)||!eq(live.floorNumber,this.plan.floorNumber)) throw new Error(r.id+': 지도 상태 불일치');
          if (Number(live.status)!==3 || Number(live.occupyStatus)!==0) throw new Error(r.id+': 유휴 상태가 아닙니다. 기존 작업을 확인하세요.');
          if (!c||Number(c.inMapStatus)!==1||Number(c.isCarry)!==0) throw new Error(r.tray+': 지도에 내려놓은 트레이가 아닙니다.');
          const from=nodeNumber(c);
          if (!r.ends.includes(from)) throw new Error(r.tray+': 예상 구간 밖에 있습니다 ('+from+').');
          const target=r.ends.find(n=>n!==from),code='SIM'+r.id+'_'+this.now().toString(36)+'_'+Math.random().toString(36).slice(2,7);
          pending={code,target,source:from,created:this.now(),ack:false};
          // Persist BEFORE submitting; a timeout will never trigger a blind resubmit.
          this.saved.missions[r.id]=pending;this.save();
          this.status[r.id]=from+' → '+target;this.emit();
          await this.assertSim(r.id);
          if (!this.running) {delete this.saved.missions[r.id];this.save();break;}
          await this.call(PATH.dispatch,'POST',{
            completedLock:0,priority:2,rackCodes:[r.tray],targetNodes:[{targetNodeLabel:target}],
            floorNumber:this.plan.floorNumber,mapCode:this.plan.mapCode,templateCode:'T1',robotId:r.id,
            originNodeLabel:'',upstreamNumber:code
          });
          pending.ack=true;this.save();
        }
        this.status[r.id]=this.saved.missions[r.id]?'진행 중인 작업 추적 대기':'반복 중지';
      } catch(e) {
        this.running=false;this.error=e.message;this.status[r.id]='확인 필요';
        this.log('중지: '+e.message+' (작업 번호는 보존됩니다)');
      }
      this.emit();
    }
    async waitMission(r,pending) {
      this.status[r.id]=pending.source+' → '+pending.target+' (진행 확인)';this.emit();
      const waitStarted=this.now();
      for (;;) {
        await this.assertSim(r.id);
        const response=await this.call('/apibd/api/v1/data/mission/select/code/'+encode(pending.code),'GET',undefined,true);
        const mission=dataOf(response);
        if (mission) {
          const status=String(mission.status||mission.missionStatus||'').toUpperCase();
          if (['CANCELED','CANCELLED','FAILED','ERROR','ABORTED','SUSPENDED'].includes(status)||mission.errorCode&&String(mission.errorCode)!=='0') throw new Error(pending.code+': '+status+' '+(mission.errorMessage||mission.errorCode||''));
          if (['FINISHED','COMPLETED','COMPLETE'].includes(status)) {
            const c=await this.container(r.tray),robot=await this.robot(r.id);
            if (c && nodeNumber(c)===pending.target && Number(c.isCarry)===0 && Number(robot?.status)===3 && Number(robot?.occupyStatus)===0) return true;
          }
        }
        if (this.now()-waitStarted>this.plan.missionTimeoutMs) throw new Error(pending.code+': 결과 확인 시간 초과. 중복 전송 없이 중지합니다.');
        if (!this.running) return false;
        await this.delay(this.plan.pollMs);
      }
    }
  }
  function rtContainers(rt) {return rt?.containerRealtimeList||[];}
  function install(win) {
    if (!/^https?:$/.test(win.location.protocol) || !win.location.pathname.startsWith('/v')) throw new Error('로그인한 KUKA Fleet 페이지에서 실행하세요.');
    if (win.KukaFleetSim) {win.KukaFleetSim.show();return;}
    const host=win.document.createElement('div');host.id='kuka-sim-controller';
    host.style.cssText='position:fixed;right:18px;bottom:18px;width:390px;z-index:2147483000';
    const shadow=host.attachShadow({mode:'open'});
    shadow.innerHTML=`<style>
      *{box-sizing:border-box}section{font:13px/1.55 system-ui,sans-serif;color:#263248;background:#fff;border:1px solid #d9dee7;border-radius:12px;box-shadow:0 8px 36px #0003;overflow:hidden}
      header{background:#202b3d;color:#fff;padding:13px 16px;display:flex;justify-content:space-between;align-items:center}h2{font-size:15px;margin:0}main{padding:14px}p{margin:0 0 10px}button{cursor:pointer;border:1px solid #cbd2df;background:#fff;border-radius:6px;padding:8px 10px;font:inherit}button:disabled{opacity:.45;cursor:default}button.primary{background:#ea6b00;color:#fff;border-color:#ea6b00}.actions{display:flex;gap:6px;margin:10px 0}table{width:100%;border-collapse:collapse;font-size:12px}td,th{text-align:left;padding:7px 2px;border-bottom:1px solid #edf0f5}#log{max-height:120px;overflow:auto;font-size:11px;color:#58657b;background:#f6f8fb;padding:8px;margin-top:10px;white-space:pre-wrap}#error{color:#bd2929;overflow-wrap:anywhere}small{color:#68768c}header button{padding:1px 7px;background:transparent;color:white;border:0}.hidden main{display:none}
      </style><section><header><h2>가상 AMR 3대 · 자동 운반</h2><button id="fold">접기</button></header><main>
      <p>lay01 / dist1 · KMP 1500i · 가상 트레이</p>
      <table><thead><tr><th>AMR</th><th>운반 구간</th><th>완료</th></tr></thead><tbody>
      ${PLAN.robots.map(r=>`<tr><td>${r.id}</td><td>${r.ends.join(' ↔ ')}</td><td id="count-${r.id}">0</td></tr><tr><td colspan="3" id="state-${r.id}">준비 전</td></tr>`).join('')}</tbody></table>
      <div class="actions"><button id="setup" class="primary">① 3대·트레이 준비</button><button id="start">② 왕복 시작</button><button id="stop">반복 정지</button></div>
      <small>반복 정지는 새 작업 전송을 멈춥니다. 진행 중인 작업은 Fleet에서 계속됩니다. 페이지를 닫아도 전달된 작업은 취소되지 않습니다.</small>
      <p id="error"></p><div id="log">기존 Fleet 로그인 세션을 사용합니다. 비밀번호·토큰을 저장하지 않습니다.</div>
      </main></section>`;
    win.document.body.appendChild(host);
    function view(s) {
      for (const r of PLAN.robots) {
        shadow.getElementById('count-'+r.id).textContent=s.counts[r.id]||0;
        shadow.getElementById('state-'+r.id).textContent=s.statuses[r.id];
      }
      shadow.getElementById('error').textContent=s.error;
      shadow.getElementById('log').textContent=s.logs.join('\n');
      shadow.getElementById('setup').disabled=s.running||s.busy;
      shadow.getElementById('start').disabled=s.running||s.busy||!s.ready;
      shadow.getElementById('stop').disabled=!s.running;
    }
    const request=async(path,method,body)=>{
      const token=win.localStorage.getItem('token');
      if (!token) throw new Error('Fleet에 로그인한 뒤 다시 실행하세요.');
      const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
      try {
        const response=await win.fetch(path,{method,credentials:'same-origin',signal:controller.signal,
          headers:{authorization:token,'Content-Type':'application/json',language:'en',fleetuser:win.localStorage.getItem('username')||''},
          body:body===undefined?undefined:JSON.stringify(body)});
        if (!response.ok) throw new Error('Fleet HTTP '+response.status+' : '+path);
        return await response.json();
      } finally {clearTimeout(timer);}
    };
    const demo=new FleetDemo({request,storage:win.localStorage,onChange:view});
    const invoke=fn=>async()=>{try{await fn();}catch(e){demo.error=e.message;demo.log(e.message);}};
    shadow.getElementById('setup').onclick=invoke(()=>demo.setup());
    shadow.getElementById('start').onclick=invoke(()=>demo.start());
    shadow.getElementById('stop').onclick=()=>demo.stop();
    shadow.getElementById('fold').onclick=()=>shadow.querySelector('section').classList.toggle('hidden');
    win.KukaFleetSim={demo,show:()=>{host.style.display='block';shadow.querySelector('section').classList.remove('hidden');},stop:()=>demo.stop()};
    view(demo.snapshot());
    demo.log('① 준비 → Fleet Monitor에서 3대 확인 → ② 왕복 시작');
  }
  return {PLAN,PATH,FleetDemo,rows,install};
});
