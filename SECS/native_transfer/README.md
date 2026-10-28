# Fleet 기본 SECS TRANSFER 시험

**2026-10-26 최신 진행:** Station·Port 등록 및 지도 연결을 완료하고 실제 조회로 확인했다. 지도 노드의 External Code에 Station UUID를 입력하면 basic-data의 MapGraphMapper가 StationProperty로 변환한다. `manual_api/graph_station_links_verified.json` 참조. 기존 New Sims의 native Order Engine 연계는 아직 확인되지 않았다. 별도 Fleet 내장 ECS/E82 경로가 발견되어 규칙 체인 설정 가능성을 조사 중이며, 이를 운송 검증 완료로 해석하지 않는다. 최신 상태는 `manual_api/20261026_추가확인결과.md`에 있다. 아래 초기 조사 시점의 지도 연결 미확인 기록보다 이 내용을 우선한다.

사용자 진행 절차는 `TRANSFER_진행절차.md`, SEComSimulator 시험 라이브러리는 `FLEET_TRANSFER_TEST.SMD`에 준비했다. 실제 운송 전에 절차서의 미확인/중단 지점을 확인한다. 메시지 작성 완료와 Fleet 운송 설정 완료를 구분한다.

현재 사용자용 문서는 `manual_api/API_수동등록방법.md`와 `manual_api/API_필드매핑.md`다. 2026-10-26 사용자가 수동 API 명령을 실행한 뒤 실제 StationData를 재조회해 `SECS_ST_89/PORT_89`, `SECS_ST_97/PORT_97`의 등록 완료를 확인했다. 등록 명령을 다시 실행할 필요는 없다. 기존 자동 등록 도구 대신 JSON 요청과 grpcurl 명령을 직접 검토하는 절차를 사용한다. 지도/로봇 연계와 전체 운송은 별도 단계다.

목표: Host의 S2F49 TRANSFER를 Fleet SECS 서비스가 받아 내장 Order Engine과 WCS로 운송한다. 사용자 정의 RUN_WORKFLOW, REST Workflow 호출, Windows 브릿지는 이 시험에 사용하지 않는다.

시험 대상은 기존 KMP1500i 가상 로봇 1/2/3으로 유지한다. 사용자는 별도 KMR iisy/Nav 시뮬레이터로 변경하지 않고 KUKA 공식 연계 방법을 확인하기로 선택했다. 공식 Nav 시뮬레이터의 설치/시작은 진행하지 않는다.

## 현재 확인된 상태

- SECS 서비스는 기본 TRANSFER/CANCEL/ABORT를 OrderCommand로 변환하도록 구성할 수 있다. 설치 이미지의 convertTransfersToOrderCommands 기본값은 true다.
- 실행 중인 Order Engine(192.168.0.45:50200)은 OrderCommand를 제공한다. 컨테이너가 실행 중이라는 사실만으로 운송 준비 완료를 의미하지 않는다.
- RobotManager:50090의 RobotData에는 UUID가 0으로 된 서버 항목만 있으며, 현재 웹 시뮬레이터의 AMR 1/2/3은 없다. TransportCapacity 데이터도 광고되지 않는다.
- 최초 조회 때 같은 경로의 StationData, PayloadData는 비어 있었다. 이후 Station 서비스에 두 Station·Port를 등록했고 직접 조회로 확인했다. 현재 확인 결과는 `manual_api/station_data_registered.json`과 `manual_api/registration_verification.json`에 있다. Payload 준비 완료는 확인되지 않았다.
- 사용자 제공 Order Engine 로그에도 같은 RobotData/PayloadData가 나타난다.
- `Not an order-engine task: SECS_API_ONCE_...` 또는 `SECS_WF_...`는 앞서 REST로 만든 작업 상태를 보고 나온 메시지다. 이것만으로 Order Engine 자체 장애로 판단하지 않는다.
- GOAWAY/NO_ERROR/max_age 뒤 재구독 로그는 연결 수명에 따른 갱신이며, 이 기록만으로 운송 실패 원인으로 단정하지 않는다.

현재 컨테이너 환경은 Graph=krcs-basic-data:7999, Fleet=fleetmanager:50070, RobotManager=robotmanager:50090, Station=station:50110, WCS=kwcs-engine:7998이다. 각 주소를 다른 서비스 이름으로 임의 교체하지 않는다. 외부에서 포트에 접속할 수 없다는 사실도 Docker 내부 서비스의 미실행을 증명하지 않는다.

## Docker 내부 조회 결과

사용자가 공식 CLI로 조회한 결과:

| 서비스 | GetDataTypes 결과 |
|---|---|
| krcs-basic-data:7999 | kuka.mobilerobotics.graph.v1alpha1.GraphData |
| krcs-robot-control:6999 | No message types are provided by DataProvider. |
| krcs-mission-manager:6689 | No message types are provided by DataProvider. |

GraphData가 제공된다는 사실은 확인됐다. 나머지 두 서비스가 현재 표준 DataProvider 타입을 광고하지 않으므로 RobotManager 주소만 이들로 바꾸는 방법은 검증된 해결책이 아니다. 이 조회로 해당 제품의 모든 버전/모드/플러그인의 SECS 지원 여부를 단정하지 않는다.

WCS와 Interface Platform의 추가 조회 결과도 확인됐다.

| 서비스 | GetActionTypes | GetDataTypes |
|---|---|---|
| kwcs-engine:7998 | ActionExecutor has nothing to execute. | No message types are provided by DataProvider. |
| kuka-interface:8076 | ActionExecutor has nothing to execute. | No message types are provided by DataProvider. |

따라서 조회한 현재 구성에서는 TRANSFER의 대상 주소를 이들로 바꾸는 공식 연계 방법이 확인되지 않았다. 이 결과는 표준 ActionExecutor/DataProvider 조회 범위에 한정되며 WCS의 별도 Mission gRPC API나 Interface Platform의 REST API 부재를 뜻하지 않는다. 이들 서비스가 실제로 사용 중인 Workflow를 실행/관찰한다는 사실과 모순되지 않는다.

현재 KMP1500i New Sims 환경에 대한 기본 TRANSFER 직접 운송 지원 여부, 필요한 공식 연계 모듈 및 설정은 KUKA 확인이 필요하다. 공식 기능이 없거나 별도 라이선스가 반드시 필요하다고 단정하지 않는다. 현재 확인된 자료만으로 적용할 수 있는 검증된 설정 변경은 없으며, 서버의 SECS/Order Engine 설정이나 기존 로봇/맵은 변경하지 않았다.

2026-10-26에 실제 GraphData를 확보했다. graph UUID=lay01-dist1, node UUID=lay01-dist1-89 및 lay01-dist1-97이며, Station 등록 후 재조회에서도 이 그래프의 노드에는 StationProperty가 없다. 현재 basic-data의 GraphEditorService/UpsertNodes에 노드 0개인 요청을 보내 API 구현 여부를 확인했으며 UNIMPLEMENTED가 반환됐다. 실제 노드 변경은 없었다. Station 두 항목은 사용자의 수동 API 호출로 등록됐고 PayloadType은 준비 완료가 확인되지 않았다. 기존 노드 속성을 보존한 지도 연결 요청 예제는 `manual_api/map_station_links.reference.json`이며 문서 검토용으로만 작성·파싱 검증했다. 서버에는 전송하지 않았다.

## 조회 명령

공식 CLI의 `data --types`로 Docker 내부 RCS 서비스가 표준 DataProvider API와 어떤 데이터 타입을 제공하는지 확인한다. 데이터 조회만 하며 Action 명령을 보내지 않는다.

```bash
cd /opt/server
sudo docker compose run --rm --no-deps -T cli data --types --host krcs-basic-data --port 7999
sudo docker compose run --rm --no-deps -T cli data --types --host krcs-robot-control --port 6999
sudo docker compose run --rm --no-deps -T cli data --types --host krcs-mission-manager --port 6689
```

설치본에는 `/opt/server/simulation`의 별도 공식 Nav 시뮬레이터 템플릿도 있다. robotcontroller, TransportCapacity 제공자, BatteryState 및 PickCommand/DropCommand 시뮬레이션이 포함된다. 현재 웹 New Sims와 구성이 다르다. 기존 RCS 가상 로봇을 기본 SECS 경로와 연결하는 설정이 있는지 먼저 확인하고, 필요 시 이 공식 시뮬레이터를 검토한다. 해당 파일의 존재만으로 현재 맵에서 운송이 가능하다고 확정하지 않는다. 이번 확인 과정에서는 설치/시작 명령을 실행하지 않았다.

2.15.1 릴리스 노트와 제공 매뉴얼을 확인했지만, 현재 New Sims의 KMP1500i를 nav-secs/Order Engine에 연결하는 모델별 설정 절차는 찾지 못했다. 공개 KUKA 제품 페이지는 Fleet의 SECS/GEM 지원을 설명하지만 이 특정 버전과 시뮬레이터 조합의 지원 범위를 확정하지 않는다: https://www.kuka.com/amr-fleet

## 메시지 참조

transfer_reference.sml은 **구조 검토용**이다. SOURCE_COMPARTMENT_TBD / DEST_COMPARTMENT_TBD를 실제 Station compartment UUID로 확정하기 전 전송하지 않는다. 기본 설정에서는 SOURCEPORT/DESTPORT가 compartment UUID로 해석된다. 웹 지도 노드 번호 89/97과 동일하다고 가정하지 않는다. stationCompartmentDelimiter를 설정했다면 해석이 달라진다.

현재 설치 이미지의 CommandParser 기준 COMMANDINFO 안에 COMMANDID(A), PRIORITY(U2), REPLACE(U2)가 필요하다. 기존 가상 MCS의 메시지에는 REPLACE가 없으므로 그대로 사용하지 않는다. 이 사실은 설치 이미지 파서로 오프라인 검증하며, 실제 운송 실행과 구분한다.

참조: KUKA_AMR_Fleet_2.15_en_V2.pdf 621~623쪽(SECS), 635~636쪽(Order Engine 의존 데이터), 593~595쪽(별도 시뮬레이터). 실제 설치 이미지의 설정/proto/파서도 함께 확인했다.
