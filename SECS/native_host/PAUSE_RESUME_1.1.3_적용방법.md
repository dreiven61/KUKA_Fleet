# PAUSE/RESUME 1.1.3 적용방법

작성: 2026-10-29. 서버 설정·운송 명령은 변경하지 않은 로컬 빌드 결과다.

## 수정 내용

- 기존 1.1.2는 TASK_SUSPENDED 콜백을 기다렸다. 실제 Fleet은 PAUSED로 전환됐지만 이 콜백이 없어, 플러그인 PAUSING 및 PC PendingControl=PAUSE가 해제되지 않았다.
- Standard API의 jobQuery와 robotQuery로 같은 COMMANDID, workflowId, CARRIERID, 배차 AMR, 연결된 RCS 미션을 확인한다. SDK RcsMissionStatus.PAUSED=12 / EXECUTING=9를 기준으로 판정한다.
- PAUSING=11, RESUMING=13, 예외 SUSPENDED=2, API 접수 응답, 이동 콜백만으로는 완료라고 보고하지 않는다. 조회 실패·누락·중복·다른 작업/AMR·경고 상태는 확인 대기를 유지한다.
- 실제 정지 확인 후 작업209 → 시스템105, 실제 재개 확인 후 작업210 → 시스템103을 보고한다. 각 보고는 S6F12 ACK를 받는다.
- 제어 회차를 저널에 저장해 PAUSE/RESUME을 반복해도 보고가 중복 키 때문에 누락되지 않게 했다.
- 기존 저널의 ACCEPTED 상태 제어는 조회로 확인하며 제어 API를 재전송하지 않는다. SENDING/UNCERTAIN은 자동으로 성공 처리하지 않는다.
- CANCEL/ABORT 및 운송 완료 판정은 기존 규칙을 유지한다. PAUSE/RESUME은 운송 시스템 단위이며 로컬 작업 하나만 대상으로 하지 않는다.

## 교체

1. 먼저 현재 멈춘 작업의 실제 상태를 Fleet에서 확인한다. 현재 진행 작업을 Fleet 화면에서 사용자가 재개하여 종료하거나 취소한 뒤 교체 시험하는 것을 권장한다. 통신 구독 단절로 생긴 운송 감시 이력의 불확정 상태까지 이 수정이 자동 복원하지는 않는다.
2. Interface Platform → Plugin Management에서 새 JAR를 Import한다.

   `C:\work\KUKA\KUKA_Fleet\SECS\fleet_transfer_plugin\build\servostar-mes-transfer-1.1.3.jar`

3. 기존 servostar-mes-transfer 1.1.2를 Disable하고 1.1.3만 Enable하여 STARTED인지 확인한다. STANDARD-API 2.15.5는 활성 상태로 둔다.
4. Virtual MCS 2.1.1을 기존 주소 192.168.0.45:5556 / Session ID3000으로 다시 연결한다. S1F13 응답의 버전이 1.1.3인지 확인한다. PC 프로그램 교체, 새 토큰 입력, 포트 변경은 필요 없다.

기존 저널·작업 기록은 삭제하지 않는다. PC의 대기 해제는 Fleet 작업 취소가 아니다. 기존 작업을 새 COMMANDID로 우회 재전송하지 않는다.

## 사용자 운전 확인 순서

1. 새로운 정상 운송 1건을 시작하고 차량 할당을 확인한다.
2. PAUSE 1회: S2F41 → S2F42 HCACK4 → 107 → 실제 정지 확인 → 209 → 105. S6F12 ACKC6=0을 확인한다.
3. RESUME 1회: 실제 S2F41 송신 → S2F42 HCACK4 → 실제 실행 재개 → 210 → 103을 확인한다.
4. 같은 운송에서 PAUSE/RESUME을 한 번 더 반복해 보고가 다시 나오는지 확인한다.
5. 마지막 정상 완료207은 실제 운송 완료 때만 나와야 한다.

HCACK4는 접수 응답이다. 209/210 없이 중지·재개 완료로 해석하지 않는다. 현재 PC와 서버 날짜/시각이 다르므로 COMMANDID와 SystemBytes로 송수신을 대조한다.

## 로컬 검증

- Java 전체 검사1,631건 통과. 이 중 실제 정지·재개 상태, 잘못된 작업/화물/AMR, 콜백 없는 반복, 저널 복구 및 loopback HSMS 검사51건.
- Virtual MCS 검사317건 통과. 209 수신 후 대기 제어가 해제되어 RESUME 송신이 가능하고, 210 수신 후 작업 상태가 재개되는 것을 loopback으로 확인했다.
- 패키징된 JAR의 격리 SBP/PF4J 로딩·시작·경로 등록·종료 검사 통과.
- 실제 Fleet 서버 적용 및 AMR 운전 시험은 아직 하지 않았다.

로그: `SECS/fleet_transfer_plugin/build/pause-resume-1.1.3-build.log`, `pause-resume-1.1.3-mcs-tests.log`.

SHA-256: `4C5F78F6C0745B738FDC7E25392E9AEDDAFCADF629D40E9C23D740B07CBF91FA`
