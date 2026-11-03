# 설정 적용 전에 ABORT된 적재 차량 복구 — 플러그인 1.2.1

## 이번 장애와 변경

작업 6892 / COMMANDID `053D6C1EA6694DA9B3A98E179A9A5830`은 대기 이동 옵션 없이 ABORT되었다. 원래 ABORT의 201·610 보고와 ACK는 완료됐지만 `recoveryArmed=false`이므로, 설정 추가나 재기동만으로 이동시키지 않는다. 조회 당시 AMR 2번은 SIM_CARGO_001을 적재하고 97번에서 Idle이었다. 이 값은 과거 조회 결과이며 적용 시 재검증한다.

1.2.1은 기존 보호 조건을 유지하면서 **작업 한 건을 명시적으로 확인해 대기 이동 대상으로 지정하는 API**를 추가한다. 저널·화물·작업 이력을 삭제하거나 기존 ABORT를 재전송하지 않는다. 일반적인 새 ABORT는 설정된 차량에 한해 기존 자동 대기 이동을 사용한다.

`POST /interfaces/api/mes-secs/status`에 실제 실행 버전 `version`, 프로세스가 읽은 `loadedAbortWaitNodes`, 작업별 `recoveryArmed`, `recoveryReviewSource`, `recoveryReviewNode`를 표시한다. 파일 내용과 프로세스가 읽은 설정을 구분할 수 있다.

## 설치 — 사용자 수행

1. 새 운송 전송을 멈춘다. 현재 설정·기존 저널은 보존한다.
2. Plugin Management에서 SERVOSTAR 1.2.0을 Disable한다. 이전 JAR를 `/opt/server/plugin-backups/` 등 **플러그인 검색 폴더 밖**에 백업한 후, UI에서 1.2.0을 Delete한다. `/opt/server/plugins` 아래에는 같은 Plugin-Id의 JAR를 여러 개 남기지 않는다. STOPPED JAR도 재시작 시 중복 로드될 수 있다.
3. `SECS/fleet_transfer_plugin/build/servostar-mes-transfer-1.2.1.jar`를 Import → Enable한다. STANDARD-API 2.15.5는 STARTED로 유지한다. SECS는 기존 5556 / Session3000이다. Virtual MCS는 기존 2.2.0을 그대로 사용할 수 있다.
4. Virtual MCS를 재연결하고 S1F13 통신을 확립한다. TSC=AUTO, 활성 Fleet 작업 없음, 해당 화물의 실제 적재 상태를 확인한다.

## 현재 작업의 읽기 전용 미리보기

Windows PowerShell에서 실행한다. 기존 로컬 설정 파일의 토큰을 읽으며 화면에 출력하지 않는다. 기본 설정 파일이 없다면 `-ConfigPath`로 현재 사용 중인 토큰 설정 JSON을 지정한다. 토큰을 채팅이나 소스 코드에 넣지 않는다.

```powershell
& 'C:\work\KUKA\KUKA_Fleet\SECS\native_host\recover_loaded_abort.ps1' `
  -CommandId '053D6C1EA6694DA9B3A98E179A9A5830' `
  -CarrierId 'SIM_CARGO_001' -VehicleId '2' `
  -SourceNode 'lay01-dist1-97' -WaitNode 'lay01-dist1-105'
```

`success=true`, `armed=false`라면 검사를 통과한 것이며 이동 요청은 하지 않았다. 검사에는 정확한 ABORT 완료·차량 해제 증거와 ACK, 후속 화물 작업 유무, 실제 유일한 화물 소유 AMR, 리프트 상승·Idle·비점유·미션 없음, 활성 Fleet 작업 없음, 적재 재고 상태·모델, 대기 노드 점유·예약, 실제 위치와 설정 매핑을 포함한다. 이 검사는 지도 경로의 통행 가능성이나 실차의 공간 여유를 검증한 결과는 아니다.

## 사용자가 대기 이동을 요청할 때

위 미리보기의 작업·화물·AMR·출발·대기 노드가 맞는 것을 확인한 뒤 **같은 명령 끝에 `-Apply`를 한 번만 추가**한다. 이는 해당 가상 AMR의 적재 대기 이동을 요청하는 조작이다.

API는 검증된 지정 내용을 저널에 먼저 기록한다. 이후 기존 처리 루프가 실제 적재·점유·출발 위치·설정을 다시 확인하고, 기존 방식의 대기 이동 미션을 한 번 제출한다. 대기 목적지에는 DOWN 액션을 넣지 않는다.

- `armed=true`는 요청 기록이며 도착 완료가 아니다.
- `9004/MOVING`은 Fleet 이동 요청 접수, `9004/READY`는 같은 화물을 실은 같은 차량의 실제 대기 도착·작업 완료·해제 확인이다.
- READY 및 ACK 후 Virtual MCS에서 `CARRIERID=SIM_CARGO_001`, `FROM=AMR_2`, `TO=빈 회수 작업 포트`로 **새 COMMANDID**를 보낸다. 이 API가 회수반송 명령까지 자동으로 보내지는 않는다.
- 최초 적용 후 응답을 잃었거나 중복 적용하면 상태를 조회한다. 복구 대상이 이미 지정됐거나 MOVING/UNCERTAIN/BLOCKED이면 이를 초기화하거나 새 이동을 재전송하지 않는다.
- 같은 차량의 현재 위치가 미리보기 이후 바뀌었거나 설정된 대기 노드가 바뀌면 이동 전 BLOCKED로 남긴다. 자동 재시도하지 않는다.

## API 본문과 거부

`POST /interfaces/api/mes-secs/recover-loaded-abort`, 기존 Authorization 헤더를 사용한다. 여섯 필드가 모두 필요하다.

```json
{
  "commandId": "053D6C1EA6694DA9B3A98E179A9A5830",
  "carrierId": "SIM_CARGO_001",
  "vehicleId": "2",
  "sourceNode": "lay01-dist1-97",
  "waitNode": "lay01-dist1-105",
  "apply": false
}
```

`apply`는 JSON Boolean이다. 문자열 `"true"`는 거부한다. `false`는 조회, `true`만 명시적 적용이다. apply=true에는 현재 SECS 통신 상태도 필요하다. HTTP401은 인증 거부, 400은 본문 오류, 409는 `code`에 표시된 복구 조건 불충족, 503은 결과 확인이 필요한 상태다. HTTP200만으로 AMR 도착을 판단하지 않는다.

주요 사유: `RECOVERY_ALREADY_INITIALIZED_QUERY_STATUS`, `ABORT_COMPLETION_UNCONFIRMED`, `ABORT_REPORT_NOT_ACKED`, `ABORT_REPORT_EVIDENCE_MISSING`, `ABORT_IDENTITY_MISMATCH`, `NEWER_CARRIER_JOB_EXISTS`, `SOURCE_NODE_CHANGED`, `SOURCE_AMR_HAS_FLEET_WORK`, `WAIT_NODE_OCCUPIED`, `WAIT_NODE_CONFIG_MISMATCH`, `SECS_NOT_COMMUNICATING`.

## 검증 범위

실제 서버에 이 버전을 설치하거나 이동 요청을 보내는 것은 사용자 적용 단계다. 로컬 회귀 검사는 이전 ABORT의 자동 재실행 방지, 명시적 조회와 적용 구분, 원래 제어 보고 ACK 확인, 위치·화물·차량·점유 변화 거부, 적용 후 재시작, 응답 유실·중복 호출 시 이동 재전송 방지, 인증·본문·런타임 상태 표시를 검증한다. 실제 서버의 적재 대기 이동·회수반송 완료는 별도 확인이 필요하다.
