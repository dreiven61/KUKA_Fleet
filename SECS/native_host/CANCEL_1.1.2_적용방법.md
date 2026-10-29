# CANCEL 이동 중단 변경 — 플러그인 1.1.2

2026-10-28. PC Virtual MCS 2.1.1 및 외부 포트 5556을 유지한다.

## 변경

- CANCEL의 Fleet `missionCancel.cancelMode`를 `NORMAL`에서 `FORCE`로 변경한다. 현재 이동 목표까지 완료하는 일반 취소 대신 이동 중단을 요청한다.
- CANCEL은 기존처럼 픽업 시작 전만 허용한다. 픽업 시작 이후에는 HCACK2로 거부하고 ABORT를 사용한다.
- ABORT도 기존처럼 FORCE이며 픽업 시작 이후에만 허용한다.
- CANCEL은 206 → 성공204/실패205, ABORT는 203 → 성공201/실패202 보고를 유지한다.
- API 접수 HCACK4만으로 완료를 보고하지 않는다. 실제 WCS 취소 결과 callback 이후 최종 보고를 생성한다. 응답 유실 시 자동 재시도하지 않는다.
- FORCE는 비상정지가 아니다. 실제 정지 위치·감속·응답 시간은 Fleet과 로봇 동작에 따른다. 적재 화물을 자동 하차하지 않는다.

## 빌드 및 적용

1. 로컬 PowerShell에서 실행한다.

   ```powershell
   cd C:\work\KUKA\KUKA_Fleet\SECS\fleet_transfer_plugin
   .\build.ps1
   ```

2. 진행 중인 SECS 운송이 모두 종료되고 미확인 제어·미보고가 없는 상태에서 Virtual MCS 연결을 해제한다. 저널과 기존 설정은 유지한다.
3. Fleet → Interface Platform → Plugin Management에서 현재 SERVOSTAR-MES-TRANSFER를 Disable한다. STANDARD-API는 유지한다.
4. `C:\work\KUKA\KUKA_Fleet\SECS\fleet_transfer_plugin\build\servostar-mes-transfer-1.1.2.jar`을 Import하고 Enable → STARTED를 확인한다. 이전 버전을 동시에 Enable하지 않는다.
5. Virtual MCS를 기존 5556에 다시 연결한다. S1F14에서 `FLEET / 1.1.2`를 확인한다.
6. 새 운송을 한 번 발행한다. 로봇이 출발 화물로 이동 중이고 픽업을 시작하기 전에 해당 작업을 선택해 CANCEL을 한 번 전송한다. 실제로 기존 목표까지 이동하지 않고 중단하는지, S2F42 HCACK4와 S6F11 206/204 및 원래 화물 위치를 확인한다.
7. ABORT도 별도 새 운송에서 픽업 이후 시험한다. 203/201 보고와 적재 화물 잔류 위치를 확인한다.

로컬 모의·루프백 검증은 Fleet에서의 실제 정지 성공 증거가 아니다. 서버 업로드·활성화와 실제 로봇 동작 시험은 별도다.

## 로컬 검증 결과

- `build.ps1` 전체 통과: 기존 회귀 1454개 + SECS 제어 등 66개 + 화물 58개, 합계 1578개.
- CANCEL/ABORT 모두 FORCE 요청, 픽업 이후 CANCEL 거부, callback 전 최종 보고 금지, 실패·응답 유실·중복 제어 방지 확인.
- 패키징된 JAR의 격리 SBP/PF4J 로딩·시작·경로 등록·중지 검사 통과.
- 로그: `fleet_transfer_plugin/build/cancel-1.1.2-build.log`.
- JAR SHA256: `C279A4CA88B51E12E827B67A172A3EEC2D769ACF30287D38A7175015627AC4F1`.
- 서버 교체·실제 CANCEL 정지 시험은 수행하지 않았다.
