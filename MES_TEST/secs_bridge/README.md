# Native SECS → Workflow 시험 연결 프로그램

현재 목표는 Fleet 기본 TRANSFER 직접 운송이다. 이 디렉터리의 RUN_WORKFLOW/REST 브릿지는 이전의 별도 시험이며 현재 목표에 필수 구성 요소가 아니다. 기본 TRANSFER 시험 현황은 `C:\work\KUKA\SECS\native_transfer\README.md`를 따른다.

현재 단계는 SEComSimulator의 사용자 정의 S2F41 명령으로 가상 AMR 1번의 기존 왕복 Workflow를 실행하는 시험이다. HSMS/GEM은 Fleet의 기존 SECS 서비스가 처리하고, 이 프로그램은 KUKA Action Executor gRPC와 검증된 표준 REST API 사이를 연결한다. Secs4Net으로 작성할 새 MES는 다음 단계다.

```text
SEComSimulator (Host, Active)
    └─ HSMS :5555 → Fleet SECS (Passive, Device ID 3000)
                       └─ gRPC → Windows 192.168.0.51:50240
                                    └─ REST → Fleet :5000 → SIM_RT_01 → 가상 AMR 1
```

## 시험 범위와 완료 확인

- RUN_WORKFLOW: COMMANDID와 WORKFLOW를 ASCII 파라미터로 받는다.
- 허용 Workflow는 W000000002 / SIM_RT_01, 로봇은 1, 화물은 SIM_CARGO_001이다. 왕복 경로는 89 → 97 → 89다.
- 접수 시 S2F42 HCACK=4가 예상된다. 이것은 실제 작업 완료가 아니다. Fleet의 현재 구현은 실행 중인 Action의 접수를 4로 변환한다.
- 프로그램은 Job status 30, 로봇 유휴/비점유/미적재, 화물의 89번 노드 하역을 함께 확인해야 `completed_and_verified`로 판정한다.
- **이번 버전은 S6F11 Workflow 완료 보고를 발행하지 않는다.** 공개 SecsEventCommand는 운송·시스템·차량 이벤트를 받으며, 임의의 Workflow 완료 이벤트가 없다. 표준 TransferCompleted를 왕복 작업에 임의로 붙이지 않았다.
- 대신 사용자 정의 CHECK_COMPLETED로 COMMANDID의 완료를 조회한다. S2F42 HCACK=0: 완료 확인, 2: 아직 완료 확인 안 됨(실행/오류/미확정 포함), 6: 모르는 COMMANDID. 세부 실패 원인은 콘솔과 journal에서 확인한다. 이는 이 시험에서 정한 사용자 정의 명령이며 표준 E82 상태 조회 명령이 아니다.
- `CancelActions`, Pause/Resume, TRANSFER는 이 연결 프로그램에서 수행하지 않는다. 현재 Fleet의 표준 명령 라우팅은 유지한다.

## 1. Windows 연결 프로그램 실행

1. Fleet에서 SIM_RT_01은 Enabled, SIM_REPEAT_01은 Disable로 둔다. 가상 로봇 1번의 기존 작업이 끝나고 화물이 89번에 있어야 한다.
2. `C:\work\KUKA\MES_TEST\start_secs_bridge.cmd`를 더블클릭한다.
3. 숨김 입력에 Fleet API Access Token을 입력한다.
4. `READY 192.168.0.51:50240 ... waiting for SECS command`가 나오면 창을 열어 둔다. **이 실행만으로 새 작업을 보내지는 않는다.** 이전 접수 journal이 있으면 기존 작업 조회만 재개할 수 있다.

현재 PC의 192.168.0.51 주소를 사용한다. 주소가 바뀌면 server.py의 --bind와 아래 Fleet 라우팅 주소를 함께 바꿔야 한다. 실행기는 현재 PC에 이미 설치한 Python과 `C:\work\KUKA\tmp\grpc_readonly_deps`를 사용한다. 다른 PC에 배포할 때는 Python과 requirements.txt의 의존성 설치가 필요하다. fleet_actions.pb는 현재 Fleet 이미지에 포함된 API proto로 생성했다.

## 2. Fleet 서버의 명령 라우팅 추가

Windows 프로그램을 READY 상태로 만든 다음, **Linux Fleet 서버 터미널에서** 먼저 TCP 도달 여부를 확인한다.

```bash
python3 -c 'import socket; s=socket.create_connection(("192.168.0.51",50240),5); print("TCP OK"); s.close()'
```

연결이 실패하고 Windows 방화벽에 차단된 경우에만 관리자 PowerShell에서 다음 규칙을 추가한다. Fleet 서버 한 주소에서 오는 해당 포트만 허용한다.

```powershell
New-NetFirewallRule -DisplayName 'KUKA SECS Workflow Lab' -Direction Inbound -Action Allow -Protocol TCP -LocalAddress 192.168.0.51 -LocalPort 50240 -RemoteAddress 192.168.0.45
```

Linux에서 설정 파일을 백업하고 연다.

```bash
sudo cp -a /opt/server/config/secs/secs-config.yml /opt/server/config/secs/secs-config.yml.before-workflow-bridge
sudo nano /opt/server/config/secs/secs-config.yml
```

기존 `commandActionExecutors:` **안에 두 줄만 추가**한다. 이 블록을 새로 중복 생성하지 않는다. 기존 PAUSE/RESUME/CANCEL/ABORT/TRANSFER/default 행은 그대로 둔다.

```yaml
commandActionExecutors:
  RUN_WORKFLOW: 192.168.0.51:50240
  CHECK_COMPLETED: 192.168.0.51:50240
  # 기존 매핑은 여기에 계속 유지
```

설정을 적용한다. SECS 서비스만 재시작하므로 SEComSimulator의 연결은 끊겼다가 다시 연결해야 한다.

```bash
sudo docker restart secs
sudo docker logs --tail 50 secs
```

이 설정은 아직 서버에 적용하지 않았다. 연결 프로그램도 현재 자동 시작 등록하지 않았다.

## 3. SEComSimulator에서 시험

1. 기존 USERLIB.SMD를 변경하지 않고, File → Open에서 `C:\work\KUKA\MES_TEST\secs_bridge\FLEET_WORKFLOW_TEST.SMD`를 연다. 기존 라이브러리의 다른 메시지와 자동 S6F12 응답을 보존한 시험용 사본이다.
2. Host/Active/192.168.0.45:5555/Device ID 3000으로 Open Connection 한다. **매번 재연결한 뒤 S1F13 ConnectEquip(L0, H→E)을 먼저 보내고, S1F14 COMMACK=0을 확인한다.** HSMS SELECTED만으로 SECS 통신 수립이 끝나지 않는다. Fleet은 통신 수립 전 S2F41을 받으면 S2F0로 중단한다. Control State가 OFFLINE이면 이어서 S1F17을 보내 S1F18 ONLACK=0(성공) 또는 2(이미 ONLINE)를 확인한다.
3. Monitor와 Windows 연결 프로그램 창을 열어 둔다.
4. RUN_WORKFLOW 항목의 `RunWorkflow` **S2F41 H→E 요청**을 보낸다. 응답 템플릿을 수동으로 보내지 않는다. 기본 값은 다음과 같다.

```text
S2F41 W
<L [2]
  <A "RUN_WORKFLOW">
  <L [2]
    <L [2] <A "COMMANDID"> <A "WF_TEST_001"> >
    <L [2] <A "WORKFLOW">  <A "W000000002"> >
  >
>
```

5. S2F42 HCACK=4와 콘솔의 `ACCEPTED`, `Submission: accepted`를 각각 확인한다. 전자는 연결 프로그램 접수, 후자는 Fleet REST 접수다.
6. 왕복이 끝나면 콘솔에 `COMPLETED_AND_VERIFIED` 및 `WORKFLOW_RESULT ... completed_and_verified`가 표시된다.
7. CHECK_COMPLETED의 `CheckCompleted` S2F41 요청을 보낸다. COMMANDID는 동일한 WF_TEST_001로 둔다. 완료 확인 후 HCACK=0이 예상된다.

동일 COMMANDID로 새 RUN_WORKFLOW를 보내면 HCACK=2로 거부하고 다시 실행하지 않는다. **앞선 작업의 완료를 확인한 뒤** 다음 시험은 WF_TEST_002처럼 새 ID로 바꾼다. COMMANDID는 영문/숫자/_/- 1~40자다. S2F49는 이 단계의 시험 대상이 아니다.

## 재시작·오류 처리

토큰은 파일에 저장하지 않는다. 명령 ID, Action UUID, REST requestId/missionCode와 결과는 secs_bridge/runs의 JSON journal에 원자적으로 기록한다. 프로세스 잠금으로 두 연결 프로그램의 동시 실행을 막는다.

- 전송 시도를 기록한 다음 REST를 한 번만 보낸다. 응답이 유실돼도 자동 재전송하지 않는다.
- 재시작하면 접수/미확정 작업은 같은 missionCode로 조회만 한다. 미전송 prepared journal은 recovery_hold로 남기고 자동 시작하지 않는다.
- 경고·취소·수동 완료·조회 오류·시간 초과는 완료로 판정하지 않는다. 이런 journal이 남아 있으면 새 명령을 막는다. 원인과 Fleet의 실제 작업 상태를 확인한 뒤 처리해야 한다. journal을 지워 중복 방지를 우회하지 않는다.
- 창을 닫아도 이미 Fleet에 내려간 작업은 취소되지 않는다.
- native SECS의 접수 ACK 및 CHECK_COMPLETED 응답은 아직 실제 HSMS 왕복 시험이 필요하다. 오프라인 mock/gRPC 테스트 통과만으로 Fleet 서버 연동 완료라고 보지 않는다.

## 검증 근거

KUKA_AMR_Fleet_2.15_en_V2.pdf 621~623쪽의 사용자 정의 remote command / SecsRawCommand / ResponseAck 라우팅과 설치 이미지의 proto를 기준으로 작성했다. 현재 설치 이미지의 RawCommandForwardingHandler, MultiActionExecutorClient, LibSecsWrapper, SecsEventHandler 처리도 확인했다. Java 패키지 `com.kuka.secs.v1alpha1`과 protobuf 패키지 `kuka.mobilerobotics.secs.v1alpha1`은 이름이 다르므로 protobuf의 실제 full name을 사용한다.

오프라인 시험 실행(PowerShell):

```powershell
$env:PYTHONPATH='C:\work\KUKA\tmp\grpc_readonly_deps'
& 'C:\Users\dreiv\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' 'C:\work\KUKA\MES_TEST\secs_bridge\test_bridge.py'
```
