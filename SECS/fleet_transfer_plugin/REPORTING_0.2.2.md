# 0.2.2 교체 안내

파일: `build/servostar-mes-transfer-0.2.2.jar`

SHA256: `33598D9C5331B274E227A58E938E9CF16D9719E5F987CC30E8B3E2E536E7A023`

## 변경

- 상태 구독의 outbound onReady 대신 활성 구독 시도의 gRPC 채널 READY 상태를 추적한다. 반닫힌 server-streaming 요청의 onReady를 기다리며 제출을 막는 문제를 수정했다.
- READY는 전송 연결 상태이며 서버의 구독 승인이나 운송 완료 증거가 아니다. 진행·완료는 기존과 같이 해당 명령의 실제 수신 이벤트로 확인한다.
- 구독 종료/연결 단절 시 미완료 명령을 불확실 상태로 차단하고 5초 뒤 재구독한다. 이전 시도의 지연 콜백이 준비 상태를 복구하지 못하도록 한다.
- 제출 전 감시/기록 준비 실패는 503, `STATUS_MONITOR_OR_JOURNAL_UNAVAILABLE`, `submitted:false`로 구분한다. 실제 제출 시도 후 응답 유실은 기존 502 불확실 응답을 유지한다.
- 읽기 전용 preview 응답에 `statusMonitorReady`를 추가했다.

## 검증

2026-10-27 로컬 검증: 기존 36개 + 보고 42개 + 상태 감시 16개 = 94개 검사 통과. 격리된 SBP/PF4J 패키지 로딩·시작·4개 경로 등록·정지 검사 통과.
상태 감시 시험은 로컬 모의 gRPC 서버로 무이벤트 연결, 단절, 재연결, 종료, 접속 불가 및 제출 전 차단을 확인했다. 실제 Fleet에는 명령을 보내지 않았다.
서버 업로드 및 실제 SECS 진행/완료 보고는 미검증이다. abnormal 보고는 이 패치의 구현 범위에 포함되지 않는다.

## 사용자 교체 순서

1. SECS 메시지 전송을 멈추고 진행 중인 운송이 없는 상태에서 Interface Platform → Plugin Management를 연다.
2. custom `servostar-mes-transfer` 0.2.1을 Disable한다. STANDARD-API는 활성 상태를 유지한다.
3. 위 0.2.2 JAR를 Import한 뒤 Enable한다. 0.2.2의 STARTED를 확인한다. 기존 JAR는 삭제하지 않아도 된다.
4. STARTED 확인 후 다음 시험을 진행한다. 기존 COMMANDID를 반복 전송하지 않는다. 실제 화물 위치를 확인한 뒤 새 시험 명령을 준비한다.

ECS 규칙, 토큰, 포트는 이 패치 때문에 변경할 필요가 없다. 활성화 실패 시 반복 Enable이나 컨테이너 재시작을 하지 말고 오류를 확인한다.
