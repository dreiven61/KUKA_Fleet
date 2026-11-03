# 플러그인 1.4.0 Enable 실패 및 1.4.1 수정

작성일: 2026-11-02. 사용자 화면에서 1.4.0 Import는 완료됐으나 Enable 후 fail, 상태 CREATED를 확인했다. 기존 1.3.1은 STOPPED다. 이 기록 작성 중 서버 플러그인을 삭제/Import/Enable하거나 로봇을 운전하지 않았다. 사용자가 직접 Import하기로 한 요청을 유지한다.

## 재현한 원인

Spring은 TransferJournal bean을 먼저 생성한다. 이 생성자는 기존 JSONL의 EVENT를 복원하면서 SOURCEPORT/DESTPORT를 실제 노드로 해석한다. 1.4.0에서는 그 해석을 동적 WorkPorts 카탈로그로 바꿨지만, 카탈로그 초기 스캔은 그 뒤에 생성되는 NativeHostService.start에서 수행했다. 따라서 기존 EVENT가 있는 최초 시작은 카탈로그 준비 전에 실패한다.

```text
IllegalStateException: FLEET_CATALOG_UNAVAILABLE: Fleet facility scan has not completed
  WorkPorts.require → WorkPorts.node → TransferJournal.node
  → TransferJournal.apply → TransferJournal.<init>
```

기존 시험은 FixtureRunner가 카탈로그를 먼저 설치했다. 패키지 로딩 시험은 offlineTest로 실제 저널 읽기와 Fleet 초기화를 비활성화했다. 두 시험 모두 이 순서 결함을 놓쳤다.

서버 로그 디렉터리는 현재 SSH 계정에 읽기 권한이 없고 sudo 비대화형 인증도 허용되지 않아 서버의 시작 스택은 확보하지 못했다. 대신 서버에 업로드된 JAR와 같은 SHA-256의 로컬 JAR를 실제 분리된 플러그인 classloader로 로딩하고, 기존 회수반송 6894의 저널을 읽어 위 실패를 재현했다. 단순 추측이나 재Import 성공 가정으로 결론 내리지 않는다.

## 1.4.1 변경

- 저널은 형식을 검사하며 기존 파일을 읽어 둔다. 카탈로그 준비 전에는 과거 EVENT의 포트 해석을 보류한다.
- 준비 전에는 새 운송과 완료 성공 보고를 허용하지 않는다. 알려진 명령의 도착 이벤트는 파일에 보존하고 복원 대기열에 유지한다.
- 카탈로그 준비 후 기존 이벤트를 복원한다. 완료되지 않은 과거 작업은 기존과 같이 UNCERTAIN으로 두고 운송을 재제출하지 않는다.
- 새 예약에는 실제 출발/도착 노드 바인딩을 기록한다. 적재 차량 소유권 복원은 현재 로봇 목록의 변경 여부에 의존하지 않는다.
- 보고용 실제 robotId를 정수로 강제 변환하던 잔여 코드를 보완했다. 기존 숫자용 vehicleId 필드는 정수 범위일 때 기존 형식을 유지하며, 문자열/큰 숫자는 실제 값을 보존한다. PC/ECS용 선택 경로와 native SECS 경로는 별도로 검증해야 한다.

## 검증

ColdStartCatalogTest는 FixtureRunner 없이 별도 JVM에서 실행한다. 카탈로그가 비어 있는 상태에서 기존 저널을 생성하고, 준비 전 제출 차단, 준비 후 캡처된 회수반송 완료 복원, 파일 무변경, 과거 CommandID 중복 차단, 손상된 기록 거부, 문자열/큰 숫자 로봇 ID 처리 등 13건을 통과했다.

기존 전체 회귀시험과 실제 JAR 분리 로딩 시험도 통과했다. 분리 로딩 시험에 실제 과거 저널을 카탈로그보다 먼저 읽는 검사를 추가했다. 동일 검사는 원본 1.4.0 JAR에서 실패하고 1.4.1 JAR에서 통과한다. 실제 서버 Enable 및 Fleet 스캔은 사용자 Import 후 확인할 단계다.

증거:

- `tmp/spec_audit_20261102/cold_start_1.4.0_repro.log`
- `tmp/spec_audit_20261102/packaged_1.4.0_cold_repro.log`
- `tmp/spec_audit_20261102/plugin_1.4.1_build.log`

## 적용 파일과 순서

수정본: `SECS/fleet_transfer_plugin/build/servostar-mes-transfer-1.4.1.jar`

SHA-256: `C4D626044390581D0200C0A0401D3E863439C8C716A0F7377497EEE34C563639`

실패한 서버 1.4.0 / 로컬 원본의 동일 SHA-256: `78A5764C5086F54BA977B0230CCF8ED2AC54E073D5DD6E99903DDD751B389B79`

1. 실패한 1.4.0 행만 Delete한다. 실패한 버전의 런타임 등록이 남으면 다른 버전 Enable에서 버전 불일치가 발생할 수 있다.
2. 1.4.1을 한 번 Import하고 Enable한다. 기존 다른 버전과 서버의 작업·보고 저널 파일은 보존한다.
3. 새로고침 후 STARTED를 확인하고, mes-secs/status에서 version=1.4.1, catalog.ready=true, 실제 차량/지도/포트/모델과 저널 건강 상태를 확인한다.
4. MCS 2.3.2를 연결해 S1F3/S1F4 상태 조회를 확인한다. 시작 성공과 실제 운송·통신 단절·재기동 통합 검증은 구분한다.

## 사용자 적용 후 서버 확인 — 2026-11-02

사용자가 1.4.1을 적용한 뒤 실제 서버를 확인했다. version=1.4.1, enabled=true, catalog.ready=true, catalog.error는 빈 문자열, 저널 healthy=true였다. Fleet 스캔 결과는 차량 1/2/3, 작업 포트 10개, 지도/층 1개, 화물 모델 SIM_TRAY_001이다.

실제 HSMS 접속의 S1F14도 FLEET/1.4.1을 반환했다. SVID 25/58/23/21 조회는 각각 차량 3 / 포트 10 / 현재 운송 0 / 화물 3건을 93ms에 반환했다. 이어 25→58→23을 응답 대기 없이 요청하는 100묶음 시험에서 300/300 응답이 정상이고 실패는 없었다. 응답 시간 P50=78ms, P95=125ms, 최대266ms였다.

실제 화물 SIM_CARGO_001의 PORT_89→PORT_97 preview도 success=true, submitted=false, statusMonitorReady=true였다. 동적 배차 후보 1/2/3, 차량 모델 KMP1500I, 화물 모델 SIM_TRAY_001을 확인했다. 이 preview는 실제 기존 운송 저널의 지연 복원이 완료되고 상태 구독이 준비됐음을 확인하며, 운송을 제출하지 않는다.

시험 후 기존 jobCount=36이 유지됐고 현재 운송/미확인 보고는 0이었다. 시험용 HSMS 연결은 종료했다. 실제 운송 시험과 운송 중 단절·재기동 검증은 아직 별도이며, MCS 2.3.2의 화면 표시는 사용자 실행 후 확인한다.

증거는 같은 tmp 폴더의 live_1.4.1_status.json, live_1.4.1_initial_query.results.json, live_1.4.1_burst.results.json, live_1.4.1_preview.json, live_1.4.1_after_checks.json에 있다.
