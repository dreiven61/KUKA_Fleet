# Robot ID와 SECS VehicleID XML 매핑

2026-11-02. 플러그인 1.5.0 / Virtual MCS 2.3.3 로컬 수정본. 서버는 현재 1.4.1이며 이 기능은 아직 서버에 적용하지 않았다. Import는 사용자가 직접 진행한다.

## 목적

Fleet 등록 ID가 정수로 제한돼도 MCS에는 현장 이름이나 앞자리 0이 있는 VehicleID를 사용할 수 있다. Fleet의 실제 Robot ID는 변경하지 않고 XML로 연결한다. 이름은 예시이며 실제 현장/MCS와 합의한 값을 사용해야 한다.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<RobotNames version="1">
  <Robot fleetId="1" vehicleId="AGV001" />
  <Robot fleetId="2" vehicleId="AGV002" />
  <Robot fleetId="3" vehicleId="AGV003" />
</RobotNames>
```

`vehicleId="001"`도 문자열 그대로 지원한다. 현재 프로젝트 입력 규칙은 1~32자의 영문·숫자·`_ . : -`다. 공백/한글, 중복 이름/ID, 작업 포트 별칭과 충돌하는 이름은 거부한다. 매핑 활성화 시 현재 Fleet이 반환하는 모든 Robot ID의 항목이 있어야 한다. 예비/이전 차량의 매핑 항목을 함께 유지할 수 있지만, XML에 있다고 Fleet 차량을 생성하지는 않는다.

양식: `servostar-robot-names.example.xml`. 실제 내용을 채운 뒤 `servostar-robot-names.xml` 이름으로 저장한다.

## 파일 배치와 반영

현재 Fleet PC의 플러그인 폴더 `/opt/server/plugins/servostar-robot-names.xml`에 배치한다. 이 폴더는 Interface 컨테이너에 `/workspace/plugins`로 연결돼 있다. 컨테이너의 기본 조회 경로는 `/workspace/plugins/servostar-robot-names.xml`이다. 기존 배포는 `/opt/nav/config`에 servostar-secs.json 단일 파일만 마운트하므로, 단순히 그 옆에 XML을 복사하면 자동으로 보이지 않는다.

다른 배치를 쓰려면 기존 servostar-secs.json의 `robotNamesFile`에 **컨테이너에서 읽을 수 있는** 전체 경로를 지정한다. 이 JSON 경로 설정 변경은 플러그인을 다시 시작해야 반영되지만, XML 내용 수정은 재빌드/재Import가 필요 없다.

- 기본 XML이 없고 한 번도 이름 매핑이 활성화되지 않은 경우 기존 Fleet ID 보고를 유지한다.
- XML은 시작 시, 5초 주기, S1F13 연결 확립 시, 새 운송 수신 전 읽는다.
- MCS 연결 중 또는 미완료 작업/미확인 보고/활성 알람이 있으면 이름 변경을 보류하고 `robotNames.ready=false`와 사유를 노출한다. 기존 보고는 마지막 승인된 이름으로 전송해 ACK·알람 발생/해제의 식별자가 바뀌지 않게 한다.
- 이름을 변경할 때는 작업과 보고·알람을 정리하고 MCS를 끊은 상태에서 XML을 저장한 뒤 다시 접속한다. 같은 매핑 파일을 원복하면 오류 상태에서 재시작 없이 복구할 수 있다.
- 적용된 매핑은 SECS 저널에 보존한다. 재기동 후에도 미확인 보고가 남아 있으면 파일의 새 이름으로 임의 전환하지 않는다. 활성 XML이 삭제되거나 손상돼도 기존 이름을 숫자로 바꿔 보내지 않는다.

## SECS 및 Fleet 경계

Fleet 조회·배차·작업 이력·알람 상관관계는 실제 Robot ID를 사용한다. XML 변환은 SECS 메시지를 만들고 해석하는 경계에서만 수행한다.

변환 대상은 SV25 VehicleID, SV21의 적재 차량/차량 위 화물 위치, SV3 알람 UnitID, RPTID5 차량 보고, RPTID3 화물 보고, RPTID6의 701/702 알람 보고, 운송 보고/현재 운송의 차량 출발지와 차량 위 화물 위치, 시험용 적재 복구 9004의 VehicleID다. S5F1은 VehicleID 필드 자체가 없으므로 해당 메시지에 임의의 차량 항목을 추가하지 않는다. 내부 노드 코드·작업 포트·CommandID·CarrierID는 로봇 이름으로 치환하지 않는다.

MCS가 `SOURCEPORT=AGV002` 또는 호환 입력 `AMR_AGV002`를 보내면 XML로 Fleet ID 2를 찾아 기존 적재 회수 검증을 수행한다. 실제 submitMission의 `robotIds`에는 `["2"]`가 들어간다. 이름 매핑이 활성화된 경우 매핑에 없는 숫자나 이름을 임의의 다른 차량으로 대체하지 않는다.

Virtual MCS 2.3.3의 FROM은 직접 입력할 수 있다. 적재 회수 시험에서는 `AMR_AGV002` 또는 `AMR_001`처럼 입력한다. 해당 차량이 실제 화물을 적재하고 회수 READY 조건을 만족해야 한다. 목적지 포트 시험 목록은 기존 시험 데이터다.

## 회수반송 FROM의 현재 매칭

서버 1.4.1에서 FROM은 두 유형이다.

| 유형 | 입력 예 | 해석 |
|---|---|---|
| 작업 포트 | PORT_89, SECS_ST_89, lay01-dist1-89 | Fleet에서 스캔한 동일 작업장 노드로 해석 |
| 적재 차량 | AMR_2 또는 2 | Fleet Robot ID 2의 적재 화물과 현재 위치를 조회 |

현재 회수 대기 정책은 차량1=lay01-dist1-103, 차량2=lay01-dist1-105, 차량3=lay01-dist1-102다. 이는 ABORT 후 대기할 위치와 회수 READY 검증 조건이다. 차량 이름을 고정 위치로 치환하는 목적지 표가 아니다.

회수 요청은 과거 작업의 차량·CarrierID와 READY 상태를 찾고, 9004 회수 준비 보고 ACK, 실제 화물 소유권·리프트 적재·유휴/작업 해제·현재 대기 위치, 대상 포트 점유/허용 모델을 확인한다. 그 뒤 현재 로봇 노드를 출발점으로 고정하고 해당 로봇만 지정해 목적지 포트로 운송한다. 실제 주행 경로 계산은 Fleet에 맡긴다.

사용자가 제공한 SKOJ2 목적지 경로표의 `3.설비별 목적지 LIST`에는 O열 No., P열 NAME, Q열 MCS EQP ID, R열 MCS UNIT ID가 있다. 예를 들어 O613/R613은 `2_901` / `E0PGV011_VC01`이며, O633/R633은 `21_003` / `E2CTA050_P01`이다. 기존 표는 차량과 포트 모두의 현장 식별자 사전 역할을 한다. 현재 코드는 포트 사전은 Fleet 메타데이터에서 구성하고, 현장 차량 이름 사전은 이 XML로 보완한다. 이 과거 번호/이름을 현재 Fleet ID 1/2/3에 자동 대응시키지는 않았다.

## 확인 방법과 검증 범위

인증된 `mes-secs/status`에서 version, catalog.ready, robotNames.ready/enabled/bindings/error를 확인한다. 설정값은 실제 Fleet ID와 MCS용 이름을 나란히 보여준다.

추가 로컬 시험 59건은 앞자리 0 보존, 양방향 SOURCEPORT 변환, 실제 Fleet mock 제출에 숫자 ID 유지, 조회/이벤트/알람의 동일 이름, ACK/작업/알람 중 이름 변경 차단, 재기동 보존, 이름 누락/충돌 및 잘못된 XML 거부, 로컬 HSMS S1F4/S1F0와 복구를 검사했다. 기존 플러그인 전체 회귀시험과 실제 패키지 XML 파서 분리 로딩도 통과했다.

MCS 2.3.3은 기본 377건 및 화물 34 / ACK 37 / 적재 회수 26건의 로컬 시험을 통과했다. 실제 현장 이름 파일은 아직 제공되지 않았으며, 서버 배포·실제 이름 조회·실제 회수반송은 별도 확인해야 한다. PC/ECS 선택 경로에 XML 이름 변환을 자동 적용하지 않는다.
