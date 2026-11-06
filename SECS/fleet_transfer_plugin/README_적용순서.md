# 로컬 빌드 · 플러그인 1.5.8 / Virtual MCS 2.4.6

현재 Import 파일은 `build/servostar-mes-transfer-1.5.8.jar`다. Fleet 작업 상태가 10 또는 20이고 차량이 미배정이면 SECS 우선순위 UPDATE를 허용한다. 신규 SECS 운송과 UPDATE는 같은 우선순위 변환을 적용한다. SECS의 99는 Fleet의 1로 전달된다.

1.5.7은 WCS의 픽업 전 임시 배차 변경을 반영한다. 같은 COMMANDID·화물·workflow의 정상 출발지 도착 사실이 확인된 경우에 한해 차량을 다시 연결하고, 기존 차량 해제와 실제 차량 할당을 보고한다. 적재 이후 차량 변경은 허용하지 않는다. 저장된 전체 WCS 기록으로 완료를 재구성할 수 있는 기존 작업도 같은 방식으로 추적한다.

## 1.5.6 배포 이력

1.5.6은 상태 API와 S1F14/S1F2의 버전 표기를 실제 패키지 버전과 일치시키고, UPDATE 거부 사유를 CEID 9003으로 보고한다. 1.5.5 패키지는 상태 응답 문자열이 1.5.4로 남아 있었으므로 그 응답만으로 적용 여부를 판정할 수 없다.

## 1.5.5 배포 이력

1.5.5는 적재 후 하역 포트로 주행 중인 SECS 이송의 `S2F41 UPDATE / DESTPORT`를 처리한다. Fleet이 같은 작업과 차량의 새 목적지 주행을 보고하면 `CEID 212`를 보낸다. 목적지 변경 요청에는 해당 작업의 현재 우선순위를 넣는다. [Virtual MCS 시험 순서](../../VirtualMCS/README.md)

## 1.5.4 배포 이력

1.5.4는 Fleet 차량의 지도 삽입과 제거를 감지해 `S6F11 / CEID 608·609 / RPTID 5`로 보고한다. Virtual MCS 2.4.1에서 수신과 ACK를 지원한다. [적용 순서와 확인 방법](../native_host/VEHICLE_INSTALLATION_1.5.4.md)

## 1.5.3 배포 이력

1.5.3은 OFFLINE/ONLINE 전환, S5F5/S5F6 알람 목록, 배차 대기 작업의 UPDATE 우선순위 변경, Modbus TCP 포트 BUZZER를 추가한다. 서버 반영과 현장 설비 검증은 별도다. [1.5.3 적용과 시험 범위](../native_host/HOST_COMMANDS_1.5.3.md)

## 1.5.2 배포 이력

이전 배포본 `build/servostar-mes-transfer-1.5.2.jar`는 1.5.0 기능·전체 런타임과 PortID/VehicleID 통일 수정을 포함한다. 최종 1.5.1은 설명문 변경 후 수동 재패키징에서 라이브러리가 빠져 125KB가 된 오류가 있어 `.jar.invalid`로 분리했다. [패키징 원인·수정·검증 방법](../native_host/PACKAGE_REPAIR_1.5.2_20261103.md). MCS는 2.4.0을 사용한다. Import는 사용자가 직접 진행하며 저널과 설정은 유지한다.

아래 내용은 이전 버전 기록이다.

1.5.0은 선택적인 XML 파일로 Fleet Robot ID와 MCS용 VehicleID를 양방향 연결한다. 실제 제어·이력에는 원래 Robot ID를 유지하고, SECS 조회·차량/화물/알람/적재 회수 보고를 만들 때 현장 이름을 사용한다. 앞자리 0도 보존한다. XML 양식과 경로, 이름 변경 조건, 서버 확인 범위는 [Robot 이름 XML 적용방법](../native_host/ROBOT_NAME_XML_1.5.0_적용방법.md)을 따른다. 현재 서버는 1.4.1이고 새 버전은 아직 배포하지 않았다.

1.4.1은 1.4.0의 최초 시작 순서 결함을 수정했다. 기존 운송 저널을 생성하는 Spring bean이 Fleet 카탈로그 스캔보다 먼저 실행돼, 과거 EVENT의 포트를 해석하다 FLEET_CATALOG_UNAVAILABLE로 시작에 실패했다. 이제 저널 파일은 변경하지 않고 읽어 두며, 카탈로그 준비 후 이력을 복원한다. 준비 전 새 운송은 차단한다. [1.4.0 Enable 실패 재현과 1.4.1 수정](../native_host/PLUGIN_1.4.0_시작실패_1.4.1_수정_20261102.md).

2026-11-02 사용자 적용 후 서버 1.4.1 실행과 catalog.ready=true를 확인했다. 실제 Fleet 스캔은 차량 3개/작업 포트 10개/지도·층 1개이며, 실제 상태 조회 연속 시험 300/300 정상(최대266ms), 운송 미제출 preview 및 기존 저널 준비 확인도 통과했다. 실제 운송과 운송 중 단절·재기동 검증은 별도다.

1.4.0을 Enable하다 실패한 경우에는 해당 1.4.0 행만 Delete해 실패한 런타임 등록을 해제한 뒤, 1.4.1 JAR를 한 번 Import하고 Enable한다. 다른 버전과 작업/보고 저널 파일은 유지한다. Import는 사용자가 직접 진행한다.

1.4.0은 시작 시 Fleet 차량·차량 모델·전체 지도/층/노드·작업장 포트·화물 모델을 조회해 공통 카탈로그를 구성한다. 5초 주기로 갱신하고 새 운송 전에도 조회한다. 실제 robotId를 그대로 사용하며 1/2/3, 특정 지도, 포트 번호, KMP1500I, SIM_TRAY_001의 운영 코드 목록을 제거했다. API 자료형이 String이라는 사실과 Fleet/시뮬레이터가 영문 ID 등록을 허용하는지는 별개다.

스캔 실패는 카탈로그 준비 실패로 노출하고 새 운송을 제출하지 않는다. 이미 수락한 작업은 마지막 확인된 포트 바인딩을 유지하며, 미확인 작업이 있는 동안 포트 바인딩 변경은 차단한다. 상태와 스캔 결과는 인증된 `mes-secs/status`의 `catalog`와 `mes-secs/refresh-catalog`에서 확인한다. 지도 메타데이터 gRPC 주소의 기본값은 `krcs-basic-data:7999`이고 서버 설정 `metadataGrpcTarget`으로 변경할 수 있다. 적재 ABORT 대기 위치는 이동 정책이므로 현장 설정을 명시해야 하며 임의의 노드를 자동 선택하지 않는다.

2.3.2는 SML에 `[EnhancedVehicles]`, `[VehicleID]`, `[EnhancedTransfers]`, `[CommandID]` 등의 사양 항목명을 표시한다. 이 이름은 로그 주석이며 전송 데이터에 문자열 필드를 추가하지 않는다. S2F49 COMMANDINFO는 사양의 COMMANDID/PRIORITY 두 항목을 사용하고 플러그인은 이전 REPLACE=0 형식도 수신한다.

1.4.0 수정 전 확인한 실행 서버는 1.3.1이었다. 이후 1.4.1 서버 확인 결과는 위 단락 및 시작 실패 수정 기록을 따른다. [사양 대조 및 검증 범위](../native_host/SECS_사양대조_동적Fleet_20261102.md)를 확인한다.

1.3.1은 S1F3 요청 항목에 필요한 API만 읽고, 화물 소유권 등 일시적인 상태 전환 불일치는 최대3회/전체1500ms 유효시간 내에서 재조회한다. 지속 불일치·API 실패는 S1F0로 유지하며 빈 작업 성공으로 바꾸지 않는다. 같은 버전 재Import 대신 기존 실행본 Disable 후 새 버전을 한 번만 Import→Enable한다. 작업·보고 저널은 보존한다.

S1F3/S1F4로 현재 운송·차량·포트·화물을 새로 조회한다. 완료 보고 ACK가 남은 운송은 현재 작업 목록에 유지하고 같은 화물의 새 운송도 막는다. [1.3.0 변경 및 단절 시험 결과](../native_host/RECONNECT_1.3.0_시험결과_20261030.md)를 따른다. 운송 중 재기동과 완료 후 미확인 보고 재기동은 검증 범위를 구분한다.

아래는1.2.3 알람 시험 기록이다.

WCS 콜백에 없는 작업 경고도 정확한 jobQuery 조회로 감시해 발생·유지·해제를 보고한다. 경고200103의 시험 ALID는1200103이며 공식 SKI 할당값이 아니다. [1.2.3 수정·적용·검증 범위](../native_host/WORKFLOW_ALARM_1.2.3_적용방법.md)를 따른다. 2026-10-30 서버1.2.3 적용 및 가상 Fleet 작업6901/6902의 발생·해제·ACK 검증을 완료했다. [실제 시험 결과](../native_host/ALARM_1.2.3_실제시험결과_20261030.md).

아래는1.2.0 이전 기록이다.

적재 ABORT 후 설정한 대기 노드까지 화물을 유지해 이동하고, FROM=AMR_번호인 새 TRANSFER로 회수반송한다. [1.2.0 적용방법](../native_host/LOADED_ABORT_1.2.0_적용방법.md)을 따른다. 로컬 빌드와 가상 Fleet 실운전 검증은 구분하며, 서버 자동 교체는 하지 않는다.

아래는 이전 버전 기록이다.

# 플러그인1.1.3 / Virtual MCS2.1.1

PAUSE/RESUME 완료를 같은 작업·화물·AMR의 실제 RCS 상태(PAUSED/EXECUTING)로 확인한다. 콜백 누락으로 PAUSING에 머무르던 문제와 콜백 없는 반복 제어에서 보고 키가 중복되던 문제를 수정했다. **[PAUSE/RESUME 1.1.3 적용방법](../native_host/PAUSE_RESUME_1.1.3_적용방법.md)**을 따른다. PC 실행 파일·토큰·포트 변경은 필요 없다. 서버에는 자동 적용하지 않았다.

## 이전 1.1.2 변경

CANCEL도 현재 이동을 중단하도록 Fleet FORCE 취소를 사용한다. 픽업 전 제한과 CANCEL 결과 보고는 유지한다. 기존 1.1.1에서 교체하는 절차는 [CANCEL 1.1.2 적용방법](../native_host/CANCEL_1.1.2_적용방법.md)을 따른다. 아래는 기존 설치·과거 버전 기록이다.

외부 연결은192.168.0.45:5556이다. **[5556 전환방법](../native_host/5556_전환방법.md)**에 따라 기존ECS의5556 게시를 새 플러그인으로 넘긴다. 코드 기본 포트와 로컬 설정을5556으로 맞췄으며 서버는 자동 변경하지 않았다.

아래는 이전5557 개발본/기존ECS 기록이다. 현재 적용에는 위 문서를 사용한다.

# 현재 로컬 개발본: 1.1.0 / Virtual MCS 2.1.0

화물 ID 고정 제한 해제, 입고/반출/조회, 스캔 시험, 실제 재고에 기반한 운송 사전 확인을 추가했다. 서버에는 적용하지 않았다. **[화물 입출고 적용방법](../native_host/화물입출고_적용방법.md)**을 먼저 따른다. 최초 Native SECS5557 구성은 [기존 적용방법](../native_host/적용방법.md)을 참고한다.

아래는 이전 버전 기록이다.

# 현재 로컬 개발본: 1.0.0 / Virtual MCS2.0.0

새 SECS 수신부5557, CANCEL/ABORT, PAUSE/RESUME, 시험 알람, 접수/중복 ACK, 보고 보존·재전송을 추가했다. 서버에는 적용하지 않았다. **적용 절차는 [native_host/적용방법.md](../native_host/적용방법.md)**를 따른다. 기존 ECS5556/Reports07 시험은 보존한다.

아래는 과거 버전 적용 기록이다. 현재 적용 절차와 섞어 사용하지 않는다.

# 이전본: 0.5.0 / Reports06 (2026-10-28)

기존 AMR1 고정에서 KMP1500I AMR1/2/3 후보를 Fleet이 배차하는 방식으로 변경했다. 실제 WCS 차량 번호를 모든 단계/최종 상태/해제 조회까지 대조한다. 604/601/605/610 보고를 추가했다. 현재 화물/포트 범위는 기존 SIM_CARGO_001 / PORT_89↔PORT_97이다.

JAR: `build/servostar-mes-transfer-0.5.0.jar`. 적용 규칙과 PC 실행본을 함께 바꿔야 하므로 `../reporting/Reports06_자동배차_적용방법.md`를 따른다. 서버에는 자동 적용하지 않았다. 아래는 이전 버전의 적용 기록이다.

# Fleet 내부 From/To 시험 플러그인

작성일: 2026-10-27

## 구성과 범위

PC(SECS S2F49 TRANSFER) → Fleet ECS E82 수신 규칙 → 이 플러그인 → Standard API submitMission → 기존 가상 AMR.

별도 PC 프로그램이나 외부 브릿지 프로세스는 실행하지 않는다. 다만 **Fleet 내부에 사용자 정의 변환 플러그인을 추가**하는 방식이다. 기본 nav-secs/Order Engine이 기존 New Sims를 직접 지원하도록 바꾸는 패치는 아니며, KUKA 공식 제공 플러그인도 아니다.

현재 시험 범위는 로봇 1번, SIM_CARGO_001, SIM_TRAY_001, KMP1500I, PORT_89 ↔ PORT_97이다. 다른 로봇·화물·포트는 거부한다. 한 작업의 완료를 확인한 뒤 다음 작업을 보낸다. Priority 1~99, REPLACE=0만 지원한다.

## 지금 할 단계: Import

1. Fleet → Interface Platform → Plugin Management → Import.
2. 다음 JAR 파일을 선택한다.

   `C:\work\KUKA\SECS\fleet_transfer_plugin\build\servostar-mes-transfer-0.1.0.jar`

3. 목록에 `SERVOSTAR-MES-TRANSFER`, 버전 `0.1.0`이 추가되는지 확인한다. 기존 STANDARD-API는 유지한다.
4. Import만으로 STARTED가 되는지, 별도 Enable이 필요한지 현재 화면 상태를 확인한다. Import/Enable은 서버에 새 플러그인을 적용하는 단계다. 플러그인은 시작 시 운송을 실행하지 않는다.

이 JAR은 로컬 빌드·시험을 마쳤다. 실제 Fleet 서버에서의 Import/시작은 사용자가 수행한 결과로 확인해야 한다. 실패하면 반복 Import하거나 기존 플러그인을 삭제하지 말고 오류 내용을 확인한다.

## 다음 단계: 먼저 Preview로 연결 검증

플러그인 STARTED 확인 후 ECS의 기존 조회 노드는 보존하고, 별도 시험 호출 노드를 만든다. 시험 중에는 기존 반복 이벤트와 다른 운송 발행을 중지하고, 규칙 편집 전 MES_SECS_TEST 장치를 Disable한다.

- 노드: `Grpc api call` → Client `external system`.
- HTTP method: POST.
- URL: `http://192.168.0.45:10870/interfaces/api/mes-transfer/preview`
- Header `Content-Type`: `application/json`.
- Header `Authorization`: 기존 Standard API에서 성공한 **원래 토큰**. `Bearer`를 덧붙이지 않는다. 토큰을 이 문서나 소스에 넣지 않았다.
- 요청 본문: root OBJECT 아래 다음 6개 STRING 필드. Name과 External Name을 같은 대문자 이름으로 지정한다. 값은 Dynamic 파라미터로 연결한다.

| Name / External Name | 값 |
|---|---|
| COMMANDID | `${COMMANDID}` |
| PRIORITY | `${PRIORITY}` |
| REPLACE | `${REPLACE}` |
| CARRIERID | `${CARRIERID}` |
| SOURCEPORT | `${SOURCEPORT}` |
| DESTPORT | `${DESTPORT}` |

PRIORITY와 REPLACE도 여기서는 문자열로 전달할 수 있다. 플러그인이 정수 검증 후 숫자로 바꾼다. 배열은 ECS에서 입력하지 않는다.

Response Body는 빈 상태로 두지 않는다. 기존 조회 노드에서 사용한 `success → API_SUCCESS`(BOOLEAN), `code → API_CODE`(STRING)를 같은 방식으로 설정한다. 실제 응답 필드 이름과 매핑해야 하며 고정값으로 성공을 판정하지 않는다.

규칙 연결 예: `ValidateTransfer --True--> PreviewTransfer`. 저장 → 장치 Synchronize/Enable 후 SECS 시험 메시지 1회 전송. Preview는 내부 robotQuery로 인증을 확인한 뒤 생성할 운송 본문을 반환하며, **submitMission을 호출하지 않는다**.

정상 Preview 결과: `success:true`, `code:"0"`, `submitted:false`, data.missionData가 2개 객체인 배열. 출발은 UP_CONTAINER, 도착은 DOWN_CONTAINER. `SOURCEPORT`를 바꾸면 위치도 바뀌어야 한다.

## Preview 확인 후 실제 운송 연결

1. Preview 결과와 로봇·화물 위치를 확인한다.
2. 동일 호출의 URL 끝부분만 `/preview` → `/submit`으로 바꾼다.
3. 호출 노드의 자동 재시도는 0으로 둔다. 현재 Grpc api call 노드는 0을 저장할 수 있었다. 다른 노드의 최소값 1과 혼동하지 않는다.
4. 새 COMMANDID로 S2F49 TRANSFER를 1회 보낸다. 2026-10-27의 마지막 확인에서는 SIM_CARGO_001이 97번에 있었으므로, 위치가 그대로면 **97 → 89**로 시험한다. 현재 위치가 달라졌으면 실제 출발지에 맞춘다.
5. Jobboard 또는 jobQuery에서 COMMANDID와 같은 missionCode를 찾아 완료 및 최종 위치를 확인한다.

`success:true/code:0`은 API 접수 결과다. `completed:false`를 함께 반환하며 운송 완료로 표시하지 않는다. S2F50 ACK도 운송 완료 신호가 아니다. 완료 S6F11 보고, 취소·중단·재개 등 MES 전체 기능은 이 시험 플러그인에 포함하지 않았다.

## 오류·중복 처리

- 400: 필수값/포트/우선순위 오류 또는 같은 COMMANDID의 재요청. 입력과 기존 작업을 조회한다.
- 401: 토큰 누락/거부 또는 robotQuery 사전 확인 거부. 토큰과 Standard API 상태를 확인한다.
- 502 / VERIFY_JOB_BEFORE_RETRY: 제출 응답을 못 받았을 수 있다. COMMANDID로 작업 존재 여부부터 조회한다. 다른 ID로 같은 작업을 재발행하지 않는다.
- 한 번 submit을 시도한 ID는 플러그인 메모리에 보관하여 재전송을 차단한다. 플러그인 재시작 후에는 이 메모리가 초기화되므로 Standard API의 중복 ID 처리와 Jobboard 조회가 필요하다. 재시작을 중복 차단 해제 수단으로 사용하지 않는다.
- API 인증 토큰은 요청에서 받아 같은 컨테이너의 `127.0.0.1:10870` Standard API로만 전달한다. 파일/DB/로그에 저장하지 않는다. 임의 URL은 입력받지 않으며 리다이렉트와 POST 자동 재전송을 사용하지 않는다.
- 원복: ECS 호출을 Preview/기존 조회로 되돌리고 새 플러그인만 Disable한다. 이미 접수된 작업이 취소되는 것은 아니므로 해당 작업은 별도로 확인한다.

## 로컬 검증 결과와 재빌드

- Java 8 호환 바이트코드로 빌드, 설치본 STANDARD-API 2.15.5 JAR의 SBP/PF4J/Spring API에 맞춤.
- From/To 매핑, 객체 배열 직렬화·실제 HTTP 본문, 필수값/허용값 검증, 인증 실패 차단, Preview 비실행, 중복 방지, 응답 유실 시 재발행 방지: 36개 확인 통과.
- 설치본에 포함된 SBP/PF4J 로더로 **패키징된 JAR**을 로딩하고 자식 컨텍스트 시작·HTTP 경로 등록·중지 시 경로 제거 시험 통과.
- HTTP 시험은 로컬 loopback 모의 서버만 사용했다. 실제 Fleet의 호스트 필터·실행 중 라이브러리 조합과의 통합, SECS부터 운송 완료까지는 서버 적용 후 단계별 확인 대상이다.
- 로컬 로더 시험에서 사용한 벤더 fat JAR의 Log4j 패턴 경고가 출력됐지만 로딩/종료 검증은 통과했다. 서버에서 같은 경고가 발생한다고 확인한 것은 아니다.

재빌드: PowerShell에서 `C:\work\KUKA\SECS\fleet_transfer_plugin\build.ps1` 실행. vendor JAR과 Java 21 JDK 경로는 스크립트 인자로 바꿀 수 있다. 빌드에 사용한 vendor 라이브러리를 배포 JAR에 복사하지 않고 제공된 API를 참조한다.
