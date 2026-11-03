# 이미 적재된 AMR의 회수 운송 완료 보고 수정 — 1.2.2

## 확인한 장애

2026-10-30에 사용자가 추출한 실제 회수 작업 기록이다. Fleet 로그의 서버 시각은 별도로 설정된 2026-10-24이며 PC 시각과 직접 빼서 지연을 계산하지 않는다.

- COMMANDID: `4BC594E5FF7F4BCBAADC8E4FBB2389A1`
- Fleet workflowId: 6894 / AMR: 2 / 화물: SIM_CARGO_001
- 출발: 적재 AMR_2, 실제 노드 lay01-dist1-105
- 도착: PORT_89, 실제 노드 lay01-dist1-89
- 조회 결과: Fleet status30, warnFlag0, 완료 시각 있음. 화물은 SECS_ST_89에 비적재 상태로 배치됐고 차량은 빈 차·Idle·비점유·미션 없음 상태로 대기 노드101번에 있었다.
- Native SECS는 ACTIVE / nextReportIndex3, 진행 게이트는 STATUS_UNCERTAIN이었다. MCS는 604까지만 받았다.

실제 출발지 이벤트는 MOVE_BEGIN → MOVE_END → **UP_CONTAINER ACTION_END**였다. 이미 리프트가 올라가 있는 차량이므로 새 UP_CONTAINER ACTION_BEGIN은 없었다. 1.2.1의 공통 픽업 판정이 이를 이벤트 누락으로 처리해 나머지 보고를 막았다. 이번 변경은 이 코드 오류를 수정한다.

## 변경 범위

출발 전 검증 후 저널에 기록한 sourceVehicle/sourceNode가 SOURCEPORT의 적재 AMR과 일치하는 경우만 별도로 취급한다. 같은 차량·화물·인스턴스의 출발지 도착과 정상 UP_CONTAINER ACTION_END로 기존 적재 컨텍스트를 확인한다. 새 픽업이나 CarrierInstalled가 발생했다고 만들지 않는다.

- 회수 운송의 진행211·출발605·도착601·하차606/302/607·해제610·완료207을 처리한다.
- 새 픽업602/603/301 및 AMR 출발지 도착601은 생성하지 않는다.
- 일반 PORT→PORT 운송은 기존 픽업 BEGIN/END 조건을 유지한다.
- 구독 GAP, 차량/화물/인스턴스 불일치, 수동 완료, 실제 도착·하차·종료 누락은 정상 완료로 바꾸지 않는다.
- 최종 완료에는 저장된 콜백 외에도 현재 Fleet의 정확한 작업·화물 배치·차량 해제를 검증한다.

## 사용자 적용 순서

1. 새 운송 전송을 멈춘다. 작업6894나 저널을 삭제하거나 같은 운송을 다시 보내지 않는다.
2. 기존1.2.1 JAR를 로컬 또는 플러그인 검색 폴더 밖에 백업한다. Plugin Management에서1.2.1을 Disable → Delete한다. STANDARD-API는 유지한다.
3. `SECS/fleet_transfer_plugin/build/servostar-mes-transfer-1.2.2.jar`를 Import → Enable한다. `/opt/server/plugins` 아래에는 동일 SERVOSTAR-MES-TRANSFER Plugin-Id의 구버전 JAR를 여러 개 남기지 않는다. 과거의 단순 STOPPED 파일도 재기동 시 충돌할 수 있다.
4. 기존 Virtual MCS2.2.0에서5556 / Session3000으로 재연결하고 S1F13 통신을 확립한다. PC 프로그램 교체나 설정 JSON 수정은 이번 변경에 필요하지 않다.
5. 상태 API의 version=1.2.2와 communicating=true를 확인한다. 저장 기록과 현재 Fleet 상태가 계속 일치하면 기존 작업6894의 빠진 보고를 이어서 생성·전송한다. 기존 nextReportIndex3에서 시작하므로 이미 보낸208·604는 다시 생성하지 않는다.
6. Virtual MCS에서 기존 COMMANDID의207/Result0과 S6F12 ACK0을 확인한다. 미확인 작업 수가 줄고 다음 전송이 가능해지는지 확인한다. 원래 ABORT 작업의9004/RETURNED도 확인한다.
7. 이후 다음 운송은 화물이 실제 놓인 PORT_89에서 빈 목적지로 새 COMMANDID를 사용한다.

이번 작업에는 **기존 recover_loaded_abort.ps1 -Apply를 다시 실행할 필요가 없다.** 그 스크립트는 이미 끝난 운송의 완료 보고를 복구하는 기능이 아니라, 아직 적재 중인 ABORT 차량의 대기 이동을 명시적으로 요청하는 기능이다.

## 검증과 한계

실제 추출 기록을 `test/fixtures/return_6894_journal.jsonl`로 보존했다. 캡처 재생·현재 Fleet 조회 형태의 fixture·기존 nextReportIndex3에서 보고 재개·종료 뒤 GAP·재시작·중복 보고 방지를 검사한다. 새 픽업을 합성하지 않고, 최종 화물 배치가 다르거나 차량이 다른 작업에 묶인 경우 완료를 막는 것도 검사한다. 로컬1,993건 및 격리된 JAR의 API 등록·해제 검사가 통과했다.

서버 설치와 실시간 보고 수신은 사용자 적용 후 확인해야 한다. 로컬 검사 통과를 실제 SECS207 수신 완료로 취급하지 않는다. 서버 저널·화물·작업을 편집하거나 운송을 재실행하지 않았다.
