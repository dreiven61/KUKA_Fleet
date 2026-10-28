# Fleet 표준 API 시험

대상: `http://192.168.0.45:5000`, STANDARD-API 2.15.5.

- `query_fleet_api.py`: 로봇과 작업 이력 조회만 수행한다.
- `run_workflow_once.py`: 가상 로봇 1번으로 기존 `SIM_RT_01` (`W000000002`)을 한 번 실행하는 시험용 프로그램이다.
- `runs/workflow_01_api_once.json`: 이번 시험의 요청 식별자, 접수 결과, 작업 상태, 최종 로봇·화물 상태를 저장한다.

API 토큰을 소스나 결과 파일에 저장하지 않는다. 직접 실행하면 토큰을 숨김 입력으로 받는다. Python 표준 라이브러리만 사용한다.

## Windows에서 직접 1회 시험

Monitor를 열고 SIM_REPEAT_01이 Disable 상태인지 확인한 뒤 `run_workflow_test.cmd`를 더블클릭한다. Access Token을 입력하고 Enter를 누르면 사전 검사 후 왕복 작업을 한 번 요청한다. 입력값은 화면에 표시하지 않는다. 실행 결과와 오류를 볼 수 있도록 종료 후 창을 유지한다. 실행할 때마다 별도 journal을 생성하므로 다시 실행하면 새 작업 시험이다. 미확정·오류 결과가 있으면 원인을 확인하기 전 다시 실행하지 않는다.

## 조회 실행

```powershell
& 'C:\Users\dreiv\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' 'C:\work\KUKA\MES_TEST\query_fleet_api.py'
```

## 기존 시험 결과 조회

아래 명령에는 `--submit`이 없으므로 명령을 다시 보내지 않는다.

```powershell
& 'C:\Users\dreiv\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' 'C:\work\KUKA\MES_TEST\run_workflow_once.py' --journal 'C:\work\KUKA\MES_TEST\runs\workflow_01_api_once.json'
```

프로그램은 전송 전에 작업 식별자와 전송 시도 상태를 기록한다. 같은 journal에 이미 전송 시도가 있으면 `--submit` 재실행을 거절한다. 응답 유실·시간 초과 시 자동 재전송이나 자동 취소를 하지 않는다. journal을 삭제하거나 이름을 바꾸어 미확정 작업을 다시 발행하지 않는다.

사전 검사: 로봇 1번이 유휴·비점유·위치 신뢰·미적재 상태이며 lay01/dist1에 있는지, SIM_CARGO_001이 89번 노드에 내려놓인 상태인지, 로봇 1번의 미완료·경고 작업이 없는지 확인한다. 가상 로봇과 반복 이벤트 Disable 여부는 사용자가 별도로 확인한 상태에서만 실행한다.

완료 판정: Job status 30, 로봇 1번 Idle/비점유/미적재, 화물 SIM_CARGO_001이 89번 노드에 내려놓인 상태를 함께 확인한다. 경고·취소·수동 완료는 성공으로 처리하지 않는다.

REST API → 기존 Workflow 실행 시험은 완료했다. 다음 단계의 SECS → Action Executor → REST API 연결 프로그램과 SEComSimulator 시험 라이브러리는 `secs_bridge/README.md`에 정리했다. `start_secs_bridge.cmd`로 연결 프로그램을 시작한다. Fleet 서버의 라우팅 추가와 실제 SECS 왕복 검증은 아직 필요하다. 기존 Workflow는 89 → 97 → 89 왕복이므로 단방향 TRANSFER 명령과 동일시하지 않는다.
