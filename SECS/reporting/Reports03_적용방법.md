# 픽업·드롭 상세 보고 적용

2026-10-27. 로컬 구현/검증 완료, Fleet에는 적용하지 않은 파일이다.

## 이번 변경

| CEID | 보고 | 실제 근거 |
|---|---|---|
| 602 | 픽업 시작 | 기존 `/progress`: 출발 노드 UP_CONTAINER ACTION_BEGIN |
| 603 | 픽업 완료 | 새 `/pickup-completed`: 동일 명령/AMR/화물/작업 인스턴스의 출발 노드 ACTION_END |
| 606 | 드롭 시작 | 새 `/drop-started`: 픽업 시작·완료를 관측한 뒤 도착 노드 DOWN_CONTAINER ACTION_BEGIN |
| 607 | 드롭 완료 | 기존 `/completed`: 드롭 ACTION_END·TASK_COMPLETED 관측 + 실제 Job 완료/최종 노드/비적재 화물 위치 확인 |

성공 콜백만 단계 근거로 인정한다. 다른 로봇·화물·노드·순서, 구독 단절, 미완료 상태에서의 재시작은 정상 보고로 치환하지 않는다. 5초 조회 주기 때문에 여러 단계가 늦게 이어서 보고될 수 있지만 관측하지 않은 사건을 만들어 보내지 않는다. UI 수신 시각은 동작 발생 시각이 아니다.

전송 순서: `208 → 211 → 602 → 603 → 606 → 607 → 207`. 각 S6F11에 PC가 S6F12를 응답한다. 602/603/606/607은 RPTID5이며 AMR ID=`1`, COMMANDID, 해당 출발/도착 포트, CARRIERID를 포함한다. 607은 전체 작업 완료 판정에 사용하지 않는다.

## 적용 순서

1. 새 전송을 멈추고 기존 작업의 207 정상 완료와 Idle을 확인한다. 미확인 작업 중 플러그인을 교체하면 구독 공백으로 해당 명령이 차단된다.
2. Interface Platform → Plugin Management에서 `servostar-mes-transfer-0.2.2`를 Disable한다. STANDARD-API는 유지한다.
3. `../fleet_transfer_plugin/build/servostar-mes-transfer-0.3.0.jar`를 Import한 뒤 Enable하여 STARTED를 확인한다. 같은 Plugin ID의 이전 버전은 다시 활성화하지 않는다. 기존 플러그인과 규칙은 삭제하지 않는다.
4. Equipment → Rule Chain Library의 Import로 `ecs_candidate/SecsTransferReports03_IMPORT_CANDIDATE.json`을 별도 규칙으로 등록한다. 기존 Reports02를 덮어쓰지 않는다.
5. 새 규칙의 다섯 API 노드에 기존 Authorization 토큰을 넣는다: `QueryFleet_API_Check`, `WaitPickupStarted`, `WaitPickupCompleted`, `WaitDropStarted`, `WaitTransferCompleted`. 토큰 자리표시는 `PASTE_EXISTING_FLEET_TOKEN`이다. 메서드 POST/Content-Type application/json, 여섯 요청 필드, 응답 success=true/code=0은 파일에 들어 있다. submit 노드 재시도는 0을 유지한다.
6. 새 규칙을 저장하고 MES_SECS_TEST의 Rule Name을 `SecsTransferReports03`으로 변경하여 동기화/Enable한다. 통신은 기존 PASSIVE/5556/Session3000을 유지한다. nav-secs의 5555는 이번 작업 대상이 아니다.
7. 기존 Virtual MCS를 종료하고 `C:\work\KUKA\VirtualMCS\실행.cmd`로 v1.1.0을 실행한다. 자동 접속/전송은 하지 않는다.
8. PC에서 연결 후 실제 화물 위치에 맞는 편도 명령을 한 번만 보낸다. 동일 COMMANDID의 위 7개 CEID와 각각의 S6F12를 확인한다. 기존 Job/화물 위치와 최종 207도 확인한다. 607까지만 왔다고 새 작업을 보내지 않는다.

ECS Event 드롭다운은 vendor serializer 문제로 일곱 노드 모두 `TransferInitiated`를 사용한다. 본문의 CEID/RPTID가 실제 보고를 결정한다. 로컬 공급사 라이브러리로 네 RPTID5와 동적 COMMANDID/포트/화물 치환을 검사했다. 드롭다운을 603 등 이름으로 바꾸면 본문이 null이 되는 설치본 제한이 있다.

## 검증과 남은 범위

- 플러그인: 기존 요청/HTTP 검사 36개, 실제 저장된 WCS 콜백 13건 재생 및 음성 사례 84개, 구독 상태 검사 16개 통과. 실제 SBP/PF4J로 JAR 시작/6개 경로 등록/정지를 검사했다. 네 보고의 ECS 직렬화/동적 치환도 통과했다.
- Virtual MCS: 로컬 HSMS 송수신 검사 66개 통과. 이번 검사는 실제 Fleet에 운송을 보내지 않았다.
- 신규 602/603/606/607의 서버 적용과 실제 송수신은 아직 검증하지 않았다. 기존 0.2.2/Reports02의 208/211/207 정상 왕복은 앞선 사용자 운전에서 확인했다.
- 아직 미구현: 차량 할당/출발/도착/해제(604/605/601/610), 화물 설치/제거(301/302), 취소/중단(201~206), 일시중지/재개(209/210), 알람 set/clear, abnormal 종료 매핑, SKI 구조의 접수 결과 ACK, ACK 거부/재접속 시 보고 재전송 큐.
- ALID와 abnormal ResultCode는 임의 배정하지 않았다. 일반 경로 오류를 출발/도착 인터록 실패로 보고하지 않는다. vendor ECS 전송기는 ACK 내용을 검사하지 않으므로 exactly-once를 보장하지 않는다.

문제 시 새 전송을 멈추고 기존 작업 상태를 확인한 뒤, MES_SECS_TEST를 Reports02로 되돌리고 0.3.0 Disable/0.2.2 Enable로 복구한다. 미확인 작업을 새 COMMANDID로 재전송하지 않는다.
