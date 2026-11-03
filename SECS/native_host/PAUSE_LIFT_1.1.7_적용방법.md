# PAUSE 반복·리프트 제어 수정: 플러그인1.1.7 / Virtual MCS2.1.4

> **2026-10-30 시험 결과 반영:** 현재 설치본에서는 REST FORCE 취소가 Mission Manager의 NORMAL/MILD로 처리되어 상승·하강을 마친 뒤 작업이 취소됐다. 아래1.1.7 보고 설계는 리프트 즉시 정지를 보장하지 않으며, 정상 완료한 리프트를 이적재 실패로 보고하는 조건은 재검토가 필요하다. 새 버전의 설치·SECS 보고 순서 시험은 수행하지 않았다. 적용 전 [실서버 시뮬레이션 시험 결과](리프트_FORCE_시뮬레이션_시험결과_20261030.md)를 먼저 확인한다.

## 수정 내용

- 적재 후 실제 AMR이 PAUSED인데 `containerQuery` 오류100001(`No such node in the graph`)로 플러그인이 PAUSING에 남던 경로를 수정했다. 정확한 COMMANDID·workflowId·AMR·화물·상승 완료 상태를 확인한 현재 robotQuery 결과를 사용한다. 다른 화물/작업이거나 조회 결과가 불명확하면 확인 완료로 처리하지 않는다.
- 같은 기록의 첫 RESUME에서 약9초 늦게 보고된 현상 전체가 해결됐다고 단정하지 않는다. 적재 중 containerQuery 의존은 제거했지만, Fleet 상태 반영 지연과 실제 재개·보고 간격은 교체 후 다시 측정해야 한다.
- 실제 UP_CONTAINER/DOWN_CONTAINER 동작 중 CANCEL·PAUSE를 HCACK2 + 시험용 CEID9003 `LIFT_OPERATION_ACTIVE:<동작>`으로 거부한다. WCS ACTION_BEGIN~성공 ACTION_END 구간과 해당 AMR의 실행 중 C-step을 검사한다. 이적재 상태 조회 실패는 `HANDLING_STATE_UNCONFIRMED`로 구분한다.
- 리프트 완료 후 화물을 싣고 이동하는 PAUSE/RESUME는 허용한다. 기존 SKI CANCEL의 픽업 시작 전 제한은 유지한다. 픽업 시작 이후 운송 중단은 ABORT를 사용한다.
- 이적재 중 ABORT는 FORCE 취소 요청을 보내고, 해당 작업의 성공 CANCEL_COMPLETED를 기다린다. TASK_COMPLETED 실패나 API 접수만으로 중단 성공을 만들지 않는다.
- 이적재 중단이 확인되면 아래 보고 순서로 처리한다. AMR의 Idle·비점유·작업 연결 해제를 별도로 확인한다. 화물이 AMR에 남아 있어도 실제 적재 상태를 보존한다.

## 보고 순서와 시험용 알람

1. 기존 ABORT 접수 S2F42 HCACK4, AbortInitiated CEID203.
2. 실제 FORCE 중단 성공 + 확인 가능한 화물 위치를 확인한 후 **S5F1 ALID900002** 설정, S5F2 ACK0 대기. 이어 UnitAlarmSet CEID702.
3. AbortCompleted CEID201. ABORT 자체가 실패한 경우는 AbortFailed CEID202이며 성공201을 보내지 않는다.
4. 현재 AMR의 실제 해제 상태를 확인한 뒤 VehicleUnassigned CEID610. 중단 지점에 따라 출발·도착 포트로 보고하며 PC2.1.4가 출발지에서의610도 해당 취소 작업과 연결한다.
5. 실패 알람·201·610의 호스트 ACK를 확인한 뒤 서버의 활성 작업을 ABORTED로 전환한다. COMMANDID 이력과 화물은 삭제하지 않는다. ACK 실패/단절 시 WAIT_REPORT_ACK와 기존 DATAID를 보존하며 운송·ABORT를 자동 재실행하지 않는다.

ALID900002는 **이 프로젝트의 시험용 정의**이며 공식 SKI ALID가 아니다. 기존 설정의 alarmMappings에서는900002를 다른 용도로 사용할 수 없다.

| 항목 | 값 |
|---|---|
| ALID | U4 900002 |
| ALCD 설정/해제 | Binary 0x86 / 0x06 (범주6) |
| ALTX | PICKUP_ABORTED_명령ID 또는 DROPOFF_ABORTED_명령ID |
| 이적재 방향을 확정할 수 없는 C-step | HANDLING_ABORTED_명령ID |
| 복구 해제 | 동일 AMR에서 이후 **다른 작업**의 동일 이적재 동작이 성공 ACTION_END로 확인될 때 S5F1 해제 및 CEID701 |

중단된 작업을 지웠다는 이유만으로 실제 리프트 복구를 가정하거나 알람을 해제하지 않는다. 화물 위치가 불확실하면 완료 확인을 보류한다. 실제 하차 없는 CarrierRemoved/DepositCompleted나 정상 운송 완료207을 만들어 보내지 않는다. 성공 운송207과 ABORT201은 서로 다른 결과다.

상태 조회의 `controlState`: ACCEPTED → WAIT_RELEASE → WAIT_REPORT_ACK → 빈 값. `controlEvidence`는 최종 `CANCEL_COMPLETED_RELEASED_AND_REPORTED`다. WAIT_RELEASE는 Fleet 작업 연결 해제 확인 대기이며 강제로 저널을 초기화하면 안 된다.

## 적용: 사용자가 수행

1. 새 전송·자동 반복을 멈춘다. **현재 멈춰 있는 기존 작업이 있다면 먼저 Fleet에서 상태를 확인해 재개 또는 취소하고 실제 작업 종료를 확인한다.** 활성 작업이 있는 상태에서 플러그인을 교체하면 WCS 구독 공백으로 미완료 작업의 보고 검증이 보류될 수 있다. 저널을 삭제하거나 과거 취소 작업용 recover_stale_pause.py를 임의로 다시 실행하지 않는다.
2. Virtual MCS를 종료한다. Plugin Management에서 현재 SERVOSTAR-MES-TRANSFER 버전을 Disable한다. STANDARD-API는 유지한다.
3. `C:\work\KUKA\KUKA_Fleet\SECS\fleet_transfer_plugin\build\servostar-mes-transfer-1.1.7.jar` Import → Enable → STARTED 확인. 같은 플러그인 구버전을 함께 Enable하지 않는다.
4. `C:\work\KUKA\KUKA_Fleet\VirtualMCS\실행.cmd`로 PC2.1.4 실행. Visual Studio 사용 시 같은 저장소 소스를 빌드한다. 화면의2.1.4를 확인한다.
5. 기존 `192.168.0.45:5556 / PC Active / Session3000`으로 연결한다. 기존 토큰·포트·상태 파일 경로를 유지한다. config·Compose 변경이나 새 브릿지는 필요 없다.

아래는 작성 당시의 기존 작업6886/AMR2/COMMANDID `C78AD16FD0294918A00AEF26C99FD3DF` 복구 절차다. **이 작업은2026-10-30 시뮬레이션 시험에서 재개 후 정상 완료됐으므로 아래 요청을 다시 보내지 않는다.** 다른 작업에 그대로 재사용해서도 안 된다.

```http
POST http://192.168.0.45:10870/interfaces/api/amr/recoverMission
Content-Type: application/json
Authorization: 기존 Fleet 토큰

{"missionCode":"C78AD16FD0294918A00AEF26C99FD3DF"}
```

API 접수만으로 종료를 판단하지 말고 해당 작업의 이동·하차·완료와 남은 보고를 확인한 뒤2번 교체 단계로 진행한다.

## 시험: 한 단계씩

1. 일반 운송1건에서 리프트가 끝난 후 주행 중 PAUSE → 실제 정지 및209/105 확인 → RESUME → 실제 이동 및210/103 확인. 같은 운송에서2~3회 반복하고 최종207/Result0을 확인한다.
2. 리프트 상승 또는 하강 중 PAUSE와 CANCEL을 각각 시험한다. HCACK2 및9003의 리프트 사유가 나오고, 실제 이적재 동작은 계속되어야 한다. 적재 상태 자체를 이유로 주행 PAUSE가 거부되면 안 된다.
3. 가상 AMR의 이적재 동작 중 ABORT1회만 보낸다. FORCE 중단 → S5F1/900002 →201 →610 → ACK 후 ABORTED를 확인한다. 중단 화물의 현재 위치와 차량 적재 여부를 따로 확인한 뒤 복구한다.
4. 알람 ACK 거부/통신 단절 시 작업 자동 재전송 없이 보고가 보존되는지 확인한다. 자동 재시도 한도에 걸린 보고는 기존 report retry 절차로 같은 DATAID만 재시도한다.

로컬 회귀·패키지 로딩 검증은 실제 Fleet 운전 검증과 구분한다. 이 문서를 작성하는 작업에서 서버 설치·로봇 운전은 수행하지 않았다. 읽기 검사와 Fleet 제어 API는 별도 요청이므로, 이적재 시작과 PAUSE 수신이 동시에 일어나는 경합까지 보장하는 하드웨어 안전 인터록은 아니다. 이 경합과 실제 리프트 중단·복구는 가상 환경에서 먼저 검증해야 한다.

검증 결과: 서버 로컬 회귀1818항목 및 격리된 플러그인 로딩/라우트 등록·해제 통과. PC 화물·진단·HSMS 회귀367항목 통과 및 Windows 배포 빌드 완료. 실제 Fleet에 새 버전을 설치하거나 로봇을 조작한 결과는 포함하지 않는다.
