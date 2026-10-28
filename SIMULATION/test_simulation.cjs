'use strict';
const assert=require('node:assert/strict');
const {FleetDemo,PLAN,PATH}=require('./fleet_simulation.js');
const tests=[];
function test(name,fn){tests.push([name,fn]);}
function fixture() {
  let time=100000, demo;
  const values=new Map(), storage={getItem:k=>values.get(k)||null,setItem:(k,v)=>values.set(k,v)};
  const robots={},sims={},containers={},missions={},mutations=[];
  let actualRobot=false,unknownDispatch=false,missionFinished=true;
  const ok=data=>({success:true,code:'200',data});
  const backend=async(path,method,body)=>{
    if(path===PATH.maps)return ok([{mapCode:'lay01',buildingCode:'B01'}]);
    if(path===PATH.types)return ok([{robotTypeCode:'KMP1500I',robotLength:1.18,robotWidth:.82}]);
    if(path===PATH.models)return ok([{id:11,modelCode:'SIM_TRAY_001',robotTypeCode:'KMP1500I'}]);
    if(path.includes('/getRealtimeInfo?'))return ok({robotRealtimeList:[...Object.values(robots),...(actualRobot?[{robotId:'REAL01',status:3}]:[])],containerRealtimeList:[]});
    if(path.includes('/node/select/map/')) {
      const label=path.split('/').at(-1);
      return ok({nodeNumber:Number(label),xCoordinate:12,yCoordinate:21,fullFunctionNode:[{mainFunction:{functionType:1,containerModel:'SIM_TRAY_001'}}]});
    }
    if(path===PATH.sims)return body.map(id=>sims[id]).filter(Boolean);
    if(path.includes('/getRobotByRobotId?'))return ok(robots[new URL('http://mock'+path).searchParams.get('robotId')]||null);
    if(path.includes('/container/select/code/'))return ok(containers[path.split('/').at(-1)]||null);
    if(path.includes('/mission/select/code/'))return ok(missions[path.split('/').at(-1)]||null);
    mutations.push({path,body:JSON.parse(JSON.stringify(body??null))});
    if(path===PATH.createSim){sims[body.robotId]={robotId:body.robotId};robots[body.robotId]={robotId:body.robotId,robotTypeCode:'KMP1500I',status:1,occupyStatus:0};return ok(null);}
    if(path===PATH.position){Object.assign(robots[body.robotId],body);return {message:'OK'};}
    if(path===PATH.simPosition)return ok(null);
    if(path.startsWith('/apimm/api/robot/entrance?')){robots[new URL('http://mock'+path).searchParams.get('robotId')].status=3;return {message:'OK'};}
    if(path===PATH.createContainer){containers[body.code]={...body,modelCode:'SIM_TRAY_001',stayNodeNumber:body.enterNodeNumber,isCarry:0};return ok(null);}
    if(path===PATH.dispatch){
      missions[body.upstreamNumber]={status:missionFinished?'FINISHED':'EXECUTING'};
      const c=containers[body.rackCodes[0]];c.stayNodeNumber=Number(body.targetNodes[0].targetNodeLabel);
      if(unknownDispatch)throw new Error('simulated response loss AFTER server accepted command');
      return {message:'OK'};
    }
    throw new Error('Unexpected API: '+path);
  };
  const make=()=>new FleetDemo({request:backend,storage,now:()=>time,delay:async ms=>{time+=ms;await new Promise(r=>setImmediate(r));}});
  demo=make();
  return {demo,make,robots,sims,containers,missions,mutations,storage,
    real:()=>{actualRobot=true;},loss:()=>{unknownDispatch=true;},normal:()=>{unknownDispatch=false;},unfinished:()=>{missionFinished=false;}};
}
test('creates exactly 3 simulators and 3 trays; setup is idempotent',async()=>{
  const f=fixture();await f.demo.setup();assert.equal(f.demo.setupReady,true);
  assert.equal(Object.keys(f.sims).length,3);assert.equal(Object.keys(f.containers).length,3);
  assert.equal(f.mutations.filter(x=>x.path===PATH.createSim).length,3);
  await f.demo.setup();assert.equal(f.mutations.filter(x=>x.path===PATH.createSim).length,3);
  for(const c of Object.values(f.containers)){assert.equal(c.checkCode,null);assert.equal(c.modelId,11);}
});
test('refuses physical robot in active layout before any mutation',async()=>{
  const f=fixture();f.real();await assert.rejects(()=>f.demo.setup(),/실물/);assert.equal(f.mutations.length,0);
});
test('refuses an existing unowned simulator ID',async()=>{
  const f=fixture();f.sims['90101']={robotId:'90101'};f.robots['90101']={robotId:'90101',status:3};
  await assert.rejects(()=>f.demo.setup(),/이미 사용/);assert.equal(f.mutations.length,0);
});
test('three robots repeatedly carry on independent two-way routes',async()=>{
  const f=fixture();await f.demo.setup();
  f.demo.onChange=s=>{if(PLAN.robots.every(r=>(s.counts[r.id]||0)>=3))f.demo.running=false;};
  await f.demo.start();await f.demo.loopPromise;
  for(const r of PLAN.robots){
    assert.equal(f.demo.saved.counts[r.id],3);
    const moves=f.mutations.filter(m=>m.path===PATH.dispatch&&m.body.robotId===r.id);
    assert.deepEqual(moves.map(m=>m.body.targetNodes[0].targetNodeLabel),[r.ends[1],r.ends[0],r.ends[1]]);
    assert(moves.every(m=>m.body.templateCode==='T1'&&m.body.rackCodes[0]===r.tray));
  }
  const codes=f.mutations.filter(m=>m.path===PATH.dispatch).map(m=>m.body.upstreamNumber);
  assert.equal(codes.length,new Set(codes).size);
});
test('simulator identity is rechecked before dispatch',async()=>{
  const f=fixture();await f.demo.setup();delete f.sims['90101'];
  await assert.rejects(()=>f.demo.start(),/가상 여부가 확인되지 않은|시뮬레이터/);
  assert.equal(f.mutations.filter(m=>m.path===PATH.dispatch).length,0);
});
test('lost dispatch response keeps mission identity and never blindly retries',async()=>{
  const f=fixture();await f.demo.setup();f.loss();await f.demo.start();await f.demo.loopPromise;
  const pending=Object.values(f.demo.saved.missions);assert(pending.length>0);
  const count=f.mutations.filter(m=>m.path===PATH.dispatch).length;
  const restored=f.make();assert.deepEqual(restored.saved.missions,f.demo.saved.missions);
  assert(Object.values(restored.saved.missions).every(p=>p.ack===false));
  assert.equal(f.mutations.filter(m=>m.path===PATH.dispatch).length,count);
});
test('arrival alone does not count as finished if mission is executing',async()=>{
  const f=fixture();await f.demo.setup();f.unfinished();
  f.demo.plan.missionTimeoutMs=10;await f.demo.start();await f.demo.loopPromise;
  assert.equal(Object.values(f.demo.saved.counts).reduce((a,b)=>a+b,0),0);
  assert(Object.keys(f.demo.saved.missions).length>0);assert(f.demo.error.includes('시간 초과'));
});
test('stop during a mission preserves pending work without cancelling Fleet jobs',async()=>{
  const f=fixture();await f.demo.setup();f.unfinished();
  f.demo.onChange=s=>{if(Object.keys(s.missions).length)f.demo.running=false;};
  await f.demo.start();await f.demo.loopPromise;
  assert.equal(f.mutations.filter(m=>m.path.includes('/cancel')).length,0);
  assert.equal(f.demo.running,false);
});
(async()=>{let failed=0;for(const[name,fn]of tests){try{await fn();console.log('PASS',name);}catch(e){failed++;console.error('FAIL',name,e);}}console.log(`${tests.length-failed}/${tests.length} passed`);process.exitCode=failed?1:0;})();
