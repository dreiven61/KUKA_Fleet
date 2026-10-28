# SKI IB-SEM 진행·완료·이상 보고 매핑

> 아래 초기 조사 기록은 0.1.0 시점의 이력을 포함한다. **현재 적용 안내는 `Reports03_적용방법.md`**를 기준으로 한다. 기존 0.2.2/Reports02의 실제 208·211·207과 S6F12는 확인됐으며, 이번 0.3.0/Reports03은 602·603·606·607의 로컬 구현/검증을 완료했다. 신규 네 보고의 서버 적용/실제 운전 검증은 남아 있다.

검토일: 2026-10-27. 대상: 기존 KMP1500i 가상 AMR + ECS E82 + SERVOSTAR-MES-TRANSFER 0.1.0.

## 현재 실제 확인한 범위

`TR_TEST_002`의 S2F49 수신 → ECS `/mes-transfer/submit` HTTP 200 → 1번 AMR 97→89 운송 → Job status 30/완료 시각 존재 → 화물 SECS_ST_89/비적재까지 확인했다. 이후 **API Success → ReportTransferInitiated(CEID208/RPTID2) 연결을 저장했고, 현재 Fleet 읽기 전용 화면에서 Response Body `B[1] 0x00`까지 재확인했다.** `SecsTransferTest (4).json`은 그 응답 설정 전의 export이며 responseBody가 null이다. 호스트의 실제 S6F11 수신 및 S6F12 응답은 아직 검증하지 않았다. 진행·완료·이상 보고의 실제 상태 연계도 미완료다.

현재 UI 증거: `S6F12_ACKC6_saved_20261027.png`. 로컬 메시지 본문 구현: `src/servostar/fleet/reporting/IbsemReports.java`. `build.ps1`의 본문 검사는 로컬 직렬화 확인이며 서버 적용이나 송수신 시험을 대신하지 않는다.

## 원문

- MessageSpec v1.0: p17-18 S2F50, p19 S5F1, p21-23 S6F11 및 RPTID 2/5/6, p28 S6F12, p29-30 S9.
- BasicSpec v1.0: p12-13 Transfer 상태, p19-20 TSC/Transfer CEID, p22 Vehicle CEID, p23 Unit Alarm, p25 Report ID, p35 ResultCode.
- ScenarioSpec v0.5: p17-18 정상 반송, p29 알람 발생/해제, p30 운송 중 알람, p31-32 출발/도착 인터록 실패, p33-35 로봇 다운, p36-38 재시작, p39 중복 명령.

문서의 내부 업무 절차를 현재 서버 변경에 대한 지시/허가로 취급하지 않는다. 사용자의 직접 작성 요청 범위에서 기존 규칙의 보고 설정을 편집했다. 실제 운송이나 서버 바이너리 교체는 이번 작업에서 실행하지 않았다.

## 필수 보고와 발생 조건

| 단계 | 메시지/CEID | RPTID | 발생 조건과 필요한 근거 |
|---|---|---:|---|
| 명령 수락/거부 | S2F50 | - | 유효성 검사와 실제 API 접수 결과가 확정된 뒤 HCACK 결정. 정상 비동기 접수는 MessageSpec 기준 4, 중복은 5, 실행 불가는 2, 파라미터 오류는 3+CPACK. |
| 반송 시작 | S6F11 / 208 TransferInitiated | 2 | 정상 접수된 명령의 실행 시작/WAITING 진입. 수신 직후나 API 거부에도 무조건 전송하지 않음. |
| 차량 할당 | S6F11 / 604 VehicleAssigned | 5 | 실제 배차/할당. 설정상 robotIds=[1]이라는 사실만으로 보고하지 않음. |
| 출발지 도착 | S6F11 / 601 VehicleArrived | 5 | 출발 노드의 실제 이동 완료. |
| 픽업/반송 진행 | S6F11 / 211 Transferring, 602 AcquireStarted | 2 / 5 | 첫 화물 픽업 시작. API 접수나 단순 목적지 이동을 반송 중으로 치환하지 않음. |
| 픽업 완료 | S6F11 / 301 CarrierInstalled, 603 AcquireCompleted | 3 / 5 | UP_CONTAINER 완료 및 화물 적재 확인. |
| 운송 이동 | S6F11 / 605 VehicleDeparted, 601 VehicleArrived | 5 | 해당 포트 출발/목적지 도착의 실제 전이. |
| 드롭 시작/완료 | S6F11 / 606, 302, 607 | 5 / 3 / 5 | DOWN_CONTAINER 시작/완료와 화물 분리 확인. |
| 차량 해제 | S6F11 / 610 VehicleUnassigned | 5 | 명령 배차 해제. |
| 정상 완료 | S6F11 / 207 TransferCompleted | 2 | 작업의 최종 완료 확인. ResultCode=0, CarrierLoc=실제 최종 포트. HTTP 200만으로 생성하지 않음. |
| 운송 일시중단/재개 | S6F11 / 209 TransferPaused, 210 TransferResumed | 2 | 실제 전이와 명령 상관관계가 확인될 때 각 1회. |
| 알람 발생/해제 | S5F1 + S6F11 / 102·702, 101·701 | - / 확인필요·6 | S5F2 및 S6F12 응답 추적. 동일 알람 반복 관측을 매번 신규 발생으로 보고하지 않음. 전체 알람 0→1, 1→0과 개별 알람 전이를 구별. |
| 출발/도착 인터록 실패로 종료 | S6F11 / 207 | 2 | 해당 실패로 작업이 실제 종료되었을 때만 ResultCode=5/6. 도착 인터록 실패 때 적재 중이면 CarrierLoc=차량 ID (Scenario p32). |
| 취소/중단 요청·결과 | S2F41/42 + S6F11 / 201-206 | 2 | CANCEL과 ABORT를 구분. 실제 취소/중단 제어와 결과 확인이 먼저 필요. |
| SECS 프로토콜 오류 | S9F1/3/5/7/9/11 | - | Device ID, Stream/Function, 형식, T3 timeout, 길이 오류. 관련 10바이트 MHEAD/SHEAD를 원본 거래에서 보존. 운송/경로 오류를 S9F7로 보내지 않음. |

## 정상 반송의 기준 순서

Scenario p17-18: S2F50 → 208 → 604 → 601(Source) → 211 → 602 → 301 → 603 → 605 → 601(Dest) → 606 → 302 → 607 → 610 → 207. 각 S6F11에 S6F12가 대응한다. 모든 사건을 최종 완료 시점에 모아서 가짜로 재생하지 않는다.

## 보고 본문과 코드 규칙

S6F11: `L[3] { U4 DATAID, U2 CEID, L[1] { L[2] { U2 RPTID, L[n] values } } }`, W=1.

RPTID 2: `L[3] { A CommandID, L[1] { L[2] { L[3] { A CarrierID, A SourcePort, A DestPort }, A CarrierLoc } }, U2 ResultCode }`.

예시 메시지는 `reference/`에 생성한다. `REFERENCE_ONLY_001`, 예시 ALID 900001을 실제 운송/운영 코드로 사용하면 안 된다. 이 코드는 SKI/KUKA가 배정한 알람 ID가 아니다.

S5F1 ALCD는 발생 시 bit8=1, 해제 시 bit8=0, 하위 7비트는 알람 분류다. 예시 분류 6은 장비 상태 경고이며 모든 Fleet 오류에 일괄 적용하는 기본값이 아니다. ALID는 U4, ALTX는 문서상 ASCII 80자 이내/허용 문자 영문·숫자·underscore·space이다. Fleet의 문자열 warnCode→ALID 매핑표를 별도로 확정해야 한다.

ResultCode: 0 OK, 5 Source Interlock Error, 6 Dest Interlock Error, 7 CarrierMisMatch, 8 ValidationCheckNG. 일반 경로 오류 PP_NoPath, 통신 단절, 임의 타임아웃을 근거 없이 5/6/7/8로 바꾸지 않는다. 정의되지 않은 일반 실패 코드, Canceled/Aborted 수치는 호스트 프로파일과 합의해야 한다. 경고 상태만 보고 업무 종료로 단정하지 않는다.

## 현재 설치 코드에서 확인한 연결 지점과 제한

- Standard API 2.15.5에는 WCS 상태 구독 `MissionStatusDispatch`, RackMove 상태 처리기가 있다. MOVE_BEGIN/END, ACTION_BEGIN/END, TASK_COMPLETED, WARNING/WARN_CLEAR, CANCEL_COMPLETED 등이 정의되어 있다. 실제 콜백 본문과 발생 순서는 다음 운송의 읽기 전용 로그로 검증해야 한다.
- ECS에는 `Event Report Send`(S6F11), `Alarm Report Send`(S5F1) 노드가 있다. 현재 규칙에는 CEID208만 API Success 다음으로 연결했다. 진행/완료/알람 노드는 아직 연결하지 않았다.
- **현재 추출한 rule-engine-components-2.11.0의 EventReportSendNode.createSecs2에는 TRANSFER_INITIATED만 본문 생성 경로가 있다. VEHICLE_ASSIGNED/UNASSIGNED 및 나머지 enum은 null 반환 경로다.** 드롭다운에 완료 보고가 보인다는 이유로 정상 송신을 단정하지 않는다. 로컬 재현 검사 결과는 `probe_result.txt`에 기록한다. 현재 컨테이너 바이너리까지 같다고 이 검사만으로 주장하지 않는다.
- 같은 노드의 ASCII 필드는 동적 치환을 지원하지만 숫자 필드는 parseInt/parseLong으로 값을 직접 읽는다. `${RESULTCODE}` 같은 숫자 동적 식을 바로 입력하면 되는 것으로 안내하지 않는다. 분기별 상수 또는 검증된 송신기 확장이 필요하다.
- 추가 코드 확인: 로컬 `TbEventReportSendNode`는 `responseBody`를 읽지 않으며, `SecsClient.sendSecs2ToHost`는 `equip.send`의 Optional 응답을 버리고 예외 유무만 반환한다. 따라서 UI에 ACKC6=0을 저장한 것만으로 S6F12의 승인 코드 검사까지 구현됐다고 볼 수 없다. 실제 응답 거부/다른 S/F에 대한 처리 검증과 송신 계층 보완이 필요하다.
- AlarmReportSend 노드는 별도 본문 생성 경로를 가지나 실제 S5F2 승인 처리까지 검증한 것은 아니다.
- 기존 0.1.0 플러그인은 접수 전송 전용이며 비동기 상태 기록/보고 송신/호스트 승인 큐를 구현하지 않았다. 설치된 0.1.0 JAR을 이 자료 생성 작업에서 수정하거나 교체하지 않았다.

## SKI 사양과 현재 ACK의 차이

현재 ECS는 S2F49를 받은 즉시 단일 `B[1] 0`을 반환하고 그 뒤 REST 호출을 실행했다. MessageSpec p17-18의 S2F50은 `L[2] { B[1] HCACK, L[0 또는 CPACK 목록] }`다. 비동기 수락값 4를 사용하고 거부·중복 판정을 그 응답에 반영하도록 ACK 시점/구조를 변경해야 완전한 SKI 프로파일이 된다. REST에서 중복을 막는 것과 호스트에 HCACK=5를 반환하는 것은 별개다.

## 문서 간 불일치 처리

- Basic p20의 207 DVVALs 표는 CommandInfo라고 쓰지만 Basic p25/Message p22의 RPTID 2 형식은 CommandID다. 본문은 MessageSpec의 실제 구조를 기준으로 작성했다.
- Scenario p40 OperatorInitiatedAction(501)은 Basic p21의 401과 충돌한다. 임의로 501을 구현하지 않는다.
- Basic p20의 208/211 등은 DVVALs에 CommandID만 기재하면서 RPTID=2를 지정한다. Message p22의 전체 RPTID 2 구조로 시험하되 기존 MCS 수신 구현과 대조해야 한다.
- Scenario의 정상 수락 표기는 0 또는 4를 함께 쓰지만 MessageSpec은 0을 사용하지 않고 4를 사용한다고 명시한다. 완전 호환 시험에서는 수신 구현과 함께 프로파일을 고정한다.

## 적용 순서

1. 먼저 정상 API 접수 뒤 **시작 보고 208 한 건**을 기존 SECS 연결로 보내고 SEComSimulator S6F12를 확인한다. 현재 ECS 본문 생성 경로가 확인된 이벤트부터 시작한다.
2. 실제 WCS 상태 통지(픽업 시작/완료, 드롭, 완료, Warning/Clear)를 COMMANDID로 연결한다. 폴링만 쓸 경우 짧은 전이를 놓칠 수 있으므로 세부 진행 사건을 지어내지 않는다.
3. 완료 207/ResultCode 0을 실제 최종 완료로 검증한다. 송신 노드의 null 경로는 별도 송신 구현/수정 없이 완료된 것으로 취급하지 않는다.
4. 알람 코드 매핑과 S5F1 set/clear, 102/702·101/701, 209/210을 연결한다. 일시 경고·최종 실패·정보 조회 실패를 서로 구별한다.
5. S2F50 L[2]/HCACK4·중복5·파라미터오류3, CANCEL/ABORT 결과, 재접속·재시작 시 미보고 큐/승인 추적을 시험한다.

## 로컬 메시지 본문 구현

`IbsemReports`는 RPTID2(207~211), RPTID5(601~607·610), RPTID3(301·302), S5F1, RPTID6(701·702), Binary ACK를 생성한다. 목적지/화물 위치/ResultCode는 호출자가 실제 상태를 전달해야 하며 자동으로 성공을 가정하지 않는다. 이 라이브러리는 송신기, ECS 설치 플러그인, 상태 구독기가 아니다. 기존 0.1.0 운송 플러그인과 서버 JAR은 변경하지 않았다.

211 예시는 픽업 시작 시점의 화물 위치를 출발 포트로 수정했다. Scenario p17에서 211은 CarrierInstalled보다 먼저 발생한다. 실제 적재 확인 전에 차량 ID를 화물 위치로 단정하지 않는다. 207 정상 완료에는 확인된 최종 도착 포트를 사용한다.

시험 완료 기준은 PC 수신 로그의 S6F11/S5F1, PC 송신 로그의 S6F12/S5F2, Fleet 상태가 동일 COMMANDID/거래로 맞는 것이다. API 접수/운송 완료만으로 이 기준을 통과했다고 표시하지 않는다.
