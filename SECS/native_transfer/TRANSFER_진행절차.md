# SECS TRANSFER 메시지와 Fleet 사전 설정

대상은 기존 KMP1500i 가상 로봇과 lay01/dist1이다. 왕복 Workflow와 Windows REST 브릿지를 사용하지 않고 단방향 운송 명령을 시험한다.

**현재 상태:** 메시지 파일은 작성했고 설치본 파서로 구조를 검증했다. 현재 KMP1500i 가상 로봇을 기본 SECS/Order Engine 경로에 연결하는 설정과 GraphData에 Station을 저장하는 방법은 확인되지 않았다. 아래 1~2는 메시지 준비 및 SECS 설정 확인 절차다. 3~4가 완료되기 전 실제 운송 준비 완료로 보지 않는다. 이 문서는 서버 설정 적용 완료 기록이 아니다.

## 1. SEComSimulator 메시지 준비

File → Open에서 다음 파일을 연다.

`C:\work\KUKA\SECS\native_transfer\FLEET_TRANSFER_TEST.SMD`

라이브러리에는 다음 요청을 준비했다. 응답(Ack) 항목은 수동으로 전송하지 않는다.

| 요청 | 의미 | 최초 예시 COMMANDID | SOURCEPORT | DESTPORT |
|---|---|---|---|---|
| Transfer89To97 — S2F49 W H→E | 89→97 단방향 운송 | TR_TEST_001 | PORT_89 | PORT_97 |
| Transfer97To89 — S2F49 W H→E | 97→89 단방향 운송 | TR_TEST_002 | PORT_97 | PORT_89 |
| ResumeNativeTransfers — S2F41 W H→E | 기본 운송 시스템 RESUME | 해당 없음 | 해당 없음 | 해당 없음 |

PORT_89와 PORT_97은 이번 시험을 위해 정한 **등록 예정 값**이다. 현재 Fleet에 이미 등록된 값이라고 가정하지 않는다.

각 S2F49의 데이터 구조:

```text
L 4
  U4 DATAID = 0
  A  OBJSPEC = ""
  A  RCMD = "TRANSFER"
  L 2
    L 2
      A "COMMANDINFO"
      L 3
        L 2 → A "COMMANDID", A "TR_TEST_001"
        L 2 → A "PRIORITY", U2 1
        L 2 → A "REPLACE", U2 0
    L 2
      A "TRANSFERINFO"
      L 3
        L 2 → A "CARRIERID", A "SIM_CARGO_001"
        L 2 → A "SOURCEPORT", A "PORT_89"
        L 2 → A "DESTPORT", A "PORT_97"
```

Fleet 설치본 파서에서는 REPLACE도 필요하다. 값을 바꿀 때 ASCII Count는 문자열의 실제 바이트 길이와 맞아야 한다. 예: PORT_89=7, PORT_97=7, SIM_CARGO_001=13, TR_TEST_001=11. 새 운송 요청에는 새 COMMANDID를 사용한다. 접수 여부가 불명확한 요청은 새 ID로 즉시 재전송하지 않는다.

## 2. Fleet SECS 설정 확인

서버에서 `/opt/server/config/secs/secs-config.yml`을 확인한다. 기본 TRANSFER를 Order Engine에 전달하는 설정은 다음과 같다.

```yaml
convertTransfersToOrderCommands: true
createPayloadsForOrders: true
payloadTypeId: SECS

commandActionExecutors:
  TRANSFER: orderengine:50200
  CANCEL: orderengine:50200
  ABORT: orderengine:50200
  PAUSE: orderengine:50200
  RESUME: orderengine:50200
  default: orderengine:50200
```

같은 YAML 블록을 중복 추가하지 않는다. `convertTransfersToOrderCommands`, `createPayloadsForOrders`, `payloadTypeId`는 현재 설치본의 기본값도 각각 true/true/SECS다. 2026-10-26에 읽은 실제 파일에서도 TRANSFER는 orderengine:50200으로 매핑돼 있었다. 따라서 위 항목을 명시하는 것만으로 현재 문제를 해결한다고 볼 수 없다.

현재 방식은 stationCompartmentDelimiter를 지정하지 않는 구성을 전제로 한다. 이 경우 SOURCEPORT/DESTPORT는 전역적으로 고유한 Station compartment UUID다. 지도 화면의 노드 번호와 별개다.

설정을 실제로 변경할 경우 먼저 백업한다.

```bash
sudo cp -a /opt/server/config/secs/secs-config.yml /opt/server/config/secs/secs-config.yml.before-native-transfer
sudo nano /opt/server/config/secs/secs-config.yml
```

변경 후 SECS 서비스에만 적용한다.

```bash
sudo docker restart secs
sudo docker logs --tail 50 secs
```

이 설정 확인/변경은 SECS 접수 경로의 준비이며 로봇·Station 등록을 대신하지 않는다.

## 3. From/To에 필요한 Fleet 데이터

실제 GraphData를 조회한 결과:

| 역할 | Graph UUID | 실제 Node UUID | 위치(m) | 등록할 Station UUID 예시 | 등록할 compartment UUID 예시 |
|---|---|---|---|---|---|
| From | lay01-dist1 | lay01-dist1-89 | X=12, Y=23 | SECS_ST_89 | PORT_89 |
| To | lay01-dist1 | lay01-dist1-97 | X=12, Y=11 | SECS_ST_97 | PORT_97 |

각 Station은 capacity.compartments에 해당 PORT를 가진 StationData로 등록돼야 한다. 데이터 형식 예시는 `station_setup_requests.json`에 있다. 이 파일은 RPC 요청 데이터 참조용이며 Fleet GUI의 Import 파일이라고 가정하지 않는다. 적용하지 않았다.

Station 서비스에 두 Station/포트를 등록하는 일회성 실행 파일은 별도로 준비했다. `Station_01_등록전확인.cmd`는 조회만 하고, `Station_02_등록실행.cmd`는 실제 등록한다. 사용 방법은 `Station_등록방법.md`를 따른다. 이는 StationData 등록 절차이며 다음 문단의 지도 연결과 4번의 로봇 연계를 완료하지는 않는다.

등록에 쓰이는 공식 메시지는 `kuka.mobilerobotics.station.v1alpha1.ManageStationDataCommand`이고 INSERT는 type=1이다. Station 서비스는 station:50110이다. 별도의 PayloadType `SECS`도 Payload 서비스에 있어야 한다. 웹 Container 목록의 SIM_CARGO_001과 기본 SECS의 PayloadData는 현재 조회상 자동으로 연계돼 있지 않다.

Station을 등록한 뒤에는 GraphData의 89/97 노드에 각 Station UUID를 연결해야 한다. 설치본 Order Engine은 노드 GraphProperty의 StationProperty를 읽어 Station 위치를 찾는다. 현재 조회한 그래프에는 이 StationProperty가 없다. 등록할 속성의 예시는 `station_setup_requests.json`에 함께 적었다.

**2026-10-26 해결:** 현재 basic-data의 `GraphEditorService/UpsertNodes`는 UNIMPLEMENTED였지만, 배포 코드에서 노드 External Code를 StationProperty.station_uuid로 변환하는 경로를 찾았다. 사용자가 GUI에서 89번 External Code=`SECS_ST_89`, 97번=`SECS_ST_97`로 저장한 뒤 실제 GraphData에서 두 연결을 확인했다. 지도 연결은 완료됐다. 자세한 근거는 `manual_api/20261026_추가확인결과.md`와 `manual_api/graph_station_links_verified.json`에 있다. 이전에 작성한 지도 UpsertNodes 요청 예제는 전송하지 않는다.

## 4. 로봇과 Pick/Drop 연결

Order Engine이 사용할 로봇은 RobotData에 등록돼 있어야 하고, 화물을 실을 TransportCapacity, 배터리 상태, 해당 로봇의 Pick/Drop 실행 경로가 필요하다. 현재 RobotManager:50090에는 서버 항목만 있고 기존 KMP1500i 가상 로봇 1/2/3은 없다.

이 로봇 연결 절차도 현재 설치본/공개 문서/조회한 표준 API에서 확인하지 못했다. 로봇 이름만 정적 RobotData에 넣는 방식으로 실제 운송 연계가 완료됐다고 처리하지 않는다. 기존 KMP1500i New Sims에 대한 공식 연계 모듈·설정 확인이 필요하다.

## 5. 준비가 완료된 뒤의 전송 순서

1. 준비된 포트와 실제 화물 위치가 맞는지 확인한다. 다른 반복 시험이 같은 로봇·화물을 사용하지 않도록 한다.
2. SEComSimulator: Host, HSMS Active, 192.168.0.45:5555, Device ID 3000.
3. Open Connection 후 S1F13 ConnectEquip(L0, H→E)을 보내 S1F14 COMMACK=0을 확인한다.
4. S1F17 Request ON-LINE을 보내 S1F18 응답을 확인한다.
5. 기본 운송 제어가 PAUSED 상태라면 ResumeNativeTransfers(S2F41 RESUME)를 보내고 S2F42 응답을 확인한다. RESUME는 접수된 기존 운송 명령의 진행에 영향을 줄 수 있으므로 대기 작업도 확인한다.
6. Transfer89To97의 **S2F49 요청**을 보낸다. S2F50은 접수 결과이므로 이것만으로 운송 완료로 판정하지 않는다. 거부 시 추가 명령을 보내기 전에 원인을 확인한다.
7. 실제 Pick→이동→Drop 및 해당 COMMANDID의 완료 보고를 확인한다. 그 뒤 필요하면 Transfer97To89를 새 COMMANDID로 별도 전송한다. 자동 왕복으로 처리하지 않는다.

이번 확인에서는 HSMS 명령이나 운송 주문을 전송하지 않았다. 현재 제공한 것은 검증된 메시지 구조, 실제 지도 식별자, 필요한 설정 데이터와 적용이 막힌 지점이다. 전체 운송이 작동하는 Fleet 사전 설정 완료 절차로 오인하지 않아야 한다.
