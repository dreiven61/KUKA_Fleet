# 화물 적재·하차 보고 301/302

2026-10-27. 새 로컬 파일만 작성했으며 Fleet 설정 변경/운송 명령은 실행하지 않았다.

## 파일

- `ecs_candidate/SecsTransferReports04_Ready_LOCAL_WITH_TOKEN.json`: 기존 토큰을 미리 넣은 별도 Import 파일, 규칙 이름 `SecsTransferReports04_Ready`.
- `ecs_candidate/SecsTransferReports04_IMPORT_CANDIDATE.json`: 토큰을 제거한 재생성/참고 파일.
- `C:\work\KUKA\VirtualMCS\dist-1.2.0\KukaVirtualMCS.exe`: RPTID3 수신을 추가한 PC 프로그램. 기존 `실행.cmd`가 이 버전을 연다.
- Fleet 플러그인 **0.3.0은 그대로 유지한다. 신규 JAR 업로드 없음.**

## 발생 근거와 본문

- 301 CarrierInstalled: 기존 WaitPickupCompleted `/pickup-completed` 성공 뒤. 동일 COMMANDID/AMR1/화물/작업 인스턴스/출발 노드의 정상 UP_CONTAINER ACTION_BEGIN 및 ACTION_END 기록을 근거로 한다. 순간적인 현재 위치를 역추정하지 않는다. 이 신호는 WCS의 성공적인 적재 동작 완료 통지이며 별도의 물리 적재 센서 검증을 추가한 것은 아니다.
- 302 CarrierRemoved: 기존 WaitTransferCompleted `/completed` 성공 뒤. 드롭 ACTION_END와 TASK_COMPLETED, 실제 Job 완료/최종 목적지/화물 비적재와 지도 위치가 모두 확인된 경우다.
- 두 메시지 모두 S6F11 W, RPTID3, `L[4] { A VehicleID, A CarrierID, A CarrierLoc, A CommandID }`.
- 이 시험 프로파일의 VehicleID는 `1`. 301의 CarrierLoc는 `1`, 302는 `${DESTPORT}`이다. 시작 시점 위치를 최종 위치로 사용하지 않는다.
- 순서는 `208 → 211 → 602 → 301 → 603 → 606 → 302 → 607 → 207`. 기존 600번대 보고를 보존한다. 301/302는 독립적인 전체 작업 완료 판정이 아니다.
- 기존 5초 조회 때문에 실제 동작과 수신 시각 사이 지연이 생길 수 있다. 적재 동작이 이미 완료됐더라도 해당 기록이 남아 있으므로 보고할 수 있다. 수신 시각을 물리 발생 시각이라고 표시하지 않는다.

## 적용

1. 진행 중 작업이 정상 207로 끝나고 Idle인지 확인한다. 기존 프로그램 종료 후 Virtual MCS v1.2.0을 실행한다. 자동 연결/운송은 하지 않는다.
2. Equipment → Rule Chain Library → Import에서 **Ready_LOCAL_WITH_TOKEN** 파일을 선택한다. `SecsTransferReports04_Ready`를 확인한다. 토큰 5곳은 이미 입력되어 있다. 기존 규칙을 삭제하지 않는다.
3. Equipment → Device → MES_SECS_TEST → Edit → Rule Name을 `SecsTransferReports04_Ready`로 바꾸고 OK, 동기화 안내 OK, Enabled 상태를 확인한다. IP/PASSIVE/5556/Session3000은 유지한다.
4. PC에서 연결하고 실제 화물 위치에 맞춰 편도 운송을 한 번 보낸다. 같은 COMMANDID의 9개 CEID와 S6F12를 확인한다. 특히 301의 위치=`1`, 302의 위치=실제 도착 포트인지 확인한다.
5. 최종 207과 실제 Job/화물 위치가 맞아야 완료다. 보고가 없으면 재전송하지 말고 해당 COMMANDID의 로그를 확인한다.

ECS Event 드롭다운은 기존과 동일하게 TransferInitiated 분기를 사용한다. 공급사 serializer 검사에서 CEID301/302, RPTID3의 필드 순서와 실제 동적 COMMANDID/화물/목적지 치환을 검증했다. enum을 CarrierInstalled/CarrierRemoved로 바꾸지 않는다.

## 검증 범위

- 기존 API 5개 설정과 submit 재시도 0을 바꾸지 않았음을 생성기에서 검사했다. 18개 노드/17개 연결, 기존 16개 노드 보존, 정상 픽업/최종 완료 gate 바로 뒤에만 301/302를 연결했다.
- 공급사 라이브러리로 두 본문과 동적 치환을 검사했다. PC 로컬 HSMS 82개 검사: ACK, 정상 순서, 잘못된 차량·화물·위치·명령, 잘못된 RPTID3, 302 완료 오판 방지, 이력 복구.
- 기존 602/603/606/607은 16:51 서버 송신 로그에서 확인했고 사용자가 수신을 확인했다. 301/302는 로컬 구현 완료이며 이번 변경 이후 실제 Fleet 송수신은 별도 시험해야 한다.
- 아직 남은 항목: 601/604/605/610, 알람 발생·해제, CANCEL/ABORT·일시중지/재개, abnormal 종료 및 SKI ACK/재접속 보고 큐. 이번 버전이 전체 SKI 시나리오 준수를 의미하지 않는다.

문제가 생기면 새 운송을 멈추고 기존 작업을 확인한 뒤 MES_SECS_TEST의 Rule Name을 기존 `SecsTransferReports03_Ready`로 되돌린다. 새 COMMANDID로 미확인 운송을 재전송하지 않는다.
