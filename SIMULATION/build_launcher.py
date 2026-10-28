"""Package the Fleet console script into a local copy helper; no server writes."""
from pathlib import Path
import html,json,zipfile
root=Path(__file__).parent
source=(root/'fleet_simulation.js').read_text(encoding='utf-8')
page='''<!doctype html>
<html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>KUKA Fleet · 가상 AMR 3대 운반</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#f1f4f8;color:#233149;font:16px/1.7 system-ui,"Malgun Gothic",sans-serif}main{max-width:960px;margin:50px auto;padding:32px;background:white;border-radius:16px;box-shadow:0 10px 45px #22304712}h1{font-size:28px;margin:0 0 8px}h2{font-size:19px;margin:26px 0 8px}.eyebrow{font-size:13px;font-weight:700;color:#e76a00;letter-spacing:1px}p{margin:6px 0 16px}.routes{display:flex;gap:14px;margin:24px 0}.route{flex:1;padding:18px;background:#f6f8fb;border:1px solid #e3e8ef;border-radius:10px}.route b{display:block;color:#d35d00}.route span{display:block;font-size:23px;font-weight:700;margin:8px 0}.route small{font-size:12px;color:#627188}button,.button{display:inline-block;text-decoration:none;border:0;border-radius:8px;padding:13px 22px;background:#ea6b00;color:white;font:700 15px system-ui;cursor:pointer;margin:6px 8px 6px 0}.button{background:#25334b}ol{padding-left:23px}li{margin:10px 0}code{background:#eef2f7;padding:3px 6px;border-radius:4px;font-size:14px}details{margin-top:22px}textarea{width:100%;height:260px;font:12px/1.5 monospace;white-space:pre;tab-size:2}.notice{padding:14px 18px;border-left:4px solid #ee8c36;background:#fff8ef;font-size:14px}#copy-status{color:#14724f;font-size:14px}footer{color:#69778c;font-size:13px;margin-top:24px}@media(max-width:650px){main{margin:10px;padding:20px}.routes{flex-direction:column}}
</style><main>
<div class="eyebrow">KUKA AMR FLEET / lay01 · dist1</div>
<h1>가상 AMR 3대 자동 운반</h1>
<p>현재 Fleet Monitor에 가상 로봇과 트레이를 추가하고, 각 구간에서 왕복 운반합니다.</p>
<div class="routes"><div class="route"><b>AMR 90101</b><span>89 ↔ 97</span><small>시작 68 · SIM_CARGO_001</small></div><div class="route"><b>AMR 90102</b><span>84 ↔ 86</span><small>시작 69 · SIM_CARGO_002</small></div><div class="route"><b>AMR 90103</b><span>94 ↔ 96</span><small>시작 67 · SIM_CARGO_003</small></div></div>
<h2>실행 순서</h2>
<ol><li>Fleet에서 <b>lay01</b>을 Enable하고 로그인 상태를 유지합니다. <b>SIM_TRAY_001</b> 모델이 있어야 합니다.</li>
<li>아래 <b>실행 코드 복사</b>를 누르고, Fleet 탭에서 <b>F12 → Console</b>에 붙여넣어 Enter를 누릅니다.</li>
<li>Fleet 화면 오른쪽 아래 제어창에서 <b>① 3대·트레이 준비 → ② 왕복 시작</b>을 누릅니다. 움직임은 Fleet <b>Monitor</b>에서 확인합니다.</li></ol>
<button id="copy">실행 코드 복사</button><a class="button" href="http://192.168.0.45:5000/" target="_blank" rel="noopener">Fleet 열기</a><span id="copy-status"></span>
<div class="notice">같은 지도에 실물 또는 가상 여부가 확인되지 않은 로봇이 있으면 실행을 차단합니다. <b>반복 정지</b>는 다음 작업 전송을 중단하며, 이미 보낸 작업은 계속됩니다. 탭을 닫으면 반복 제어도 중단됩니다.</div>
<h2>다시 실행할 때</h2><p>동일한 Fleet 탭·브라우저에서 실행 코드를 다시 붙여넣으면 제어창이 표시됩니다. 새로고침했다면 <b>① 준비</b>로 기존 가상 AMR을 확인한 후 시작하세요. 이전 작업이 미확정이면 기록된 작업 번호부터 확인합니다.</p>
<details><summary>실행 코드 보기 · 복사가 안 되면 직접 선택</summary><textarea id="source" readonly>__SOURCE__</textarea></details>
<footer>실제 서버 2.15 프런트엔드 API와 첨부 매뉴얼을 기준으로 작성. 실서버 인증 후 생성·운반은 아직 검증하지 않았습니다. 비밀번호·토큰은 외부 전송하거나 파일에 저장하지 않습니다. 가상 로봇·트레이는 Fleet에 유지됩니다.</footer>
</main><script>
document.getElementById('copy').onclick=async()=>{const field=document.getElementById('source');try{await navigator.clipboard.writeText(field.value);document.getElementById('copy-status').textContent='복사 완료. Fleet 탭의 F12 → Console에 붙여넣으세요.';}catch(e){field.parentElement.open=true;field.focus();field.select();const ok=document.execCommand('copy');document.getElementById('copy-status').textContent=ok?'복사 완료':'아래 선택된 코드를 Ctrl+C로 복사하세요.';}};
</script></html>'''
(root/'시뮬레이션_실행안내.html').write_text(page.replace('__SOURCE__',html.escape(source)),encoding='utf-8')
manifest={'mapCode':'lay01','floorNumber':'dist1','containerModel':'SIM_TRAY_001',
 'robots':[{'id':'90101','startNode':68,'route':[89,97],'container':'SIM_CARGO_001'},
           {'id':'90102','startNode':69,'route':[84,86],'container':'SIM_CARGO_002'},
           {'id':'90103','startNode':67,'route':[94,96],'container':'SIM_CARGO_003'}],
 'live_run_verified':False,'virtual_robot_creation_verified_live':False,
 'server':'http://192.168.0.45:5000','dispatch':'/apimm/api/mission/dispatch',
 'missionTemplate':'T1','simulatorIdentityCheck':'/apisim/api/v1/simu/getRobotStatus'}
(root/'simulation_plan.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
with zipfile.ZipFile(root/'KUKA_Fleet_3AMR_Simulation.zip','w',compression=zipfile.ZIP_DEFLATED) as z:
    for name in ['fleet_simulation.js','시뮬레이션_실행안내.html','simulation_plan.json','README.md','test_simulation.cjs']:
        z.write(root/name,arcname=name)
print(root/'시뮬레이션_실행안내.html')
