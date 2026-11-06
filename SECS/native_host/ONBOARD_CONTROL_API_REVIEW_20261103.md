# 타사 차상 리프트·컨베이어 제어 API 검토

검토일: 2026-11-03 (한국 시간)

## 1. 판단

| 방식 | 확인한 인터페이스 | 판단 |
|---|---|---|
| AMR이 차상 PLC와 직접 통신 | Customized Actions + gRPC Action Executor / Data Provider | 확장 구조는 매뉴얼에 존재. 현재 KMP1500i에서 고객 프로그램을 설치·등록할 수 있는지는 미확인. 기본 Modbus/MC/S7 클라이언트가 AMR 안에 있다는 근거도 확보하지 못함. |
| Fleet이 차상 PLC와 통신 | ECS Device + Rule Chain + Workflow Device Command + 완료 callback | 서버 측 Modbus TCP·S7 통신과 작업 연동 방법이 명시되어 있음. 차상 PLC를 이 외부 장치로 취급하는 설계는 가능하다고 판단. 이동 장치의 통신, 배차 로봇과 장치 선택, 적재 상태·출발 허가 연동은 추가 설계·검증 대상. |

두 방식 모두 완제품 차상 교체 API가 확인된 것은 아니다. 첫 방식은 AMR 내부 확장 환경 확인이 먼저이고, 두 번째는 기존 서버 통신 기능을 활용해 시퀀스를 구성할 수 있는 근거가 더 구체적이다.

이 문서는 매뉴얼·웹 자료와 로컬 소스 조사 결과다. 서버 설정 변경, 플러그인 Import, PLC 통신 쓰기, AMR 움직임 시험은 수행하지 않았다. 현재 배포 버전·활성화 상태를 새로 확인한 결과로 해석하면 안 된다.

## 2. 검토 자료

### 로컬 KUKA 문서

1. `C:/work/KUKA/SW/KUKA.AMR_Fleet_2.15.1/KUKA_AMR_Fleet_2.15_en_V2.pdf`
   - 719페이지, 문서 표기 발행일 09.04.2026.
   - 중점 검토: 295~300, 348, 361~368, 370~372, 460~465, 476~532, 547~559, 637~652페이지.
2. `C:/work/KUKA/SW/KUKA.AMR_Fleet_2.15.1/KUKA.AMR Fleet 2.15.1 Release Notes - EN.pdf`
   - 2.15.1 항목 07/2026, 특히 1페이지의 기본 포트 비활성화.
3. `C:/work/KUKA/교육/KMP_1500I_diffDrive_en_V2.0.pdf`
   - 97페이지. 모델 운용·구성·네트워크 관련 내용 확인. 고객용 외부 차상 PLC 드라이버·등록 절차는 찾지 못함.
4. `C:/work/KUKA/교육/AMR training/KUKA.AMR Fleet Standard API Documentation_EN.pdf`
   - V1.0 / 05.2025. submitMission, templateCode, ROLLER_MOVE 확인.
   - 최신 로컬 2.15 통합 매뉴얼에 같은 주제와 변경된 actionType 설명이 있어 최신 문서를 우선함.

2.15 매뉴얼 296·524·551페이지는 이미지로 렌더링하여 조건 문구와 표를 확인했다. 문서 전체의 모든 문장을 검증했다는 의미가 아니라, 두 제어 방식에 직접 관련된 절과 참조 절을 검토한 것이다.

### 공식 웹 자료

- KUKA Fleet 제품 설명: https://www.kuka.com/en-se/products/amr-autonomous-mobile-robotics/amr-fleet-management-software
  - 주변 설비 신호 접근·설정·모니터링과 표준 API 연동을 설명한다. 특정 KMP1500i 차상 드라이버 SDK 제공의 증거는 아니다.
- my.KUKA Fleet 2.15 제품·다운로드 안내: https://my.kuka.com/s/product/kukaamr-fleet-215/01tc100000DEqdmAAD?language=en_US
  - 검색 결과의 공식 제품 정보에서 WCS gRPC 7998, ECS Swagger 8082 및 운용·프로그래밍 매뉴얼 다운로드 항목 확인.
  - 직접 페이지 열기는 CSS 오류로 본문 열람이 제한되었고, 다운로드 안내에는 로그인 요구가 표시됨. 공개 SDK를 내려받아 검증한 결과가 아니다.
- Mitsubishi Electric SLMP Reference Manual: https://dl.mitsubishielectric.com/dl/fa/document/manual/plc/sh080956eng/sh080956engl.pdf
  - MC/SLMP 프레임을 사용하는 별도 통신 규약임을 확인. 이 자료는 KUKA의 MC 지원을 증명하지 않는다.

비공식 재게시 매뉴얼·판매점 설명은 지원 여부 판단의 근거로 사용하지 않았다.

## 3. 방식 1: AMR과 차상의 직접 통신

### 매뉴얼에 존재하는 구조

2.15 매뉴얼 295~298페이지, 5.3.3 Customized actions:

1. 사용자 명령을 protobuf로 정의한다.
2. 해당 명령의 Data Provider와 Action Executor를 구현한다.
3. Action Executor를 AMR에 등록한다.
4. protobuf를 Fleet의 `/opt/server/config/apis`에 배치한다.
5. Fleet 재시작 후 Graph/Workflow의 Customized Action 또는 WCS gRPC를 통해 호출한다.

따라서 구현 후보 구조는 다음과 같다. 아래 PLC 드라이버 부분은 우리가 구현해야 하는 설계이며, KUKA가 기본 제공한다고 확인한 기능이 아니다.

```text
Fleet Workflow / WCS gRPC
    -> AMR 내부의 사용자 Action Executor
        -> Modbus TCP / MC / S7 드라이버
            -> 차상 PLC
                -> 리프트 / 컨베이어
```

### 실제 API 이름

2.15 매뉴얼 637~646페이지의 API framework:

| 역할 | RPC | 사용 목적 |
|---|---|---|
| 명령 종류 확인 | GetActionTypes | 서비스가 처리할 수 있는 명령 타입 조회 |
| 명령 접수 | SubmitAction | 동작 명령 접수. 성공 응답 자체가 물리 동작 완료를 의미하지 않음 |
| 실행 상태 | SubscribeActions | 상태와 오류·실행 정보를 구독 |
| 취소·변경 | CancelActions / UpdateAction | 해당 executor가 지원하는 경우에 사용 |
| 데이터 종류 | GetDataTypes | 제공되는 데이터 타입 확인 |
| 상태 조회 | GetData / SubscribeData | 단발 조회 또는 상태 구독 |

기존 로컬 추출본에도 `kuka/actionexecutor/v1alpha1/action_executor_service.proto`와 `kuka/dataprovider/v1alpha1/data_provider_service.proto`가 존재하며 서비스 메서드 명칭을 대조했다. 이 파일의 존재만으로 현재 로봇에 설치 가능한 고객 SDK가 확보되었다고 판단하지 않는다.

Action 상태의 FINISHED에는 오류 종료도 포함될 수 있으므로 오류 정보를 함께 확인해야 한다. CancelActions 접수 후에도 정지·정리 작업이 남을 수 있다(640~643페이지).

### 현재 KMP1500i에서 미확인인 부분

296페이지의 전제 조건은 **KUKA.Navigation을 사용하는 로봇만 지원하며 예로 KMR iisy**를 든다. KMP1500i라는 모델명, 제품 소개의 Navigation Solution 표현, Fleet에서 로봇이 움직인다는 사실만으로 이 조건 충족을 단정할 수 없다.

637페이지는 로봇 서비스가 로봇 호스트 네트워크 안에서 접근되는 구조라고 설명한다. 외부 PC에서 해당 내부 포트를 바로 호출하면 된다는 뜻도 아니다.

현재 확보한 문서에서 확인하지 못한 항목:

- 현재 KMP1500i의 컨트롤러·펌웨어가 해당 executor 등록을 지원하는지.
- 고객용 SDK, 빌드·설치·실행 환경, 서비스 등록 및 업그레이드 유지 절차.
- AMR에 기본 제공되는 외부 PLC용 Modbus TCP·MC·S7 클라이언트.
- 기존 내장 리프트 명령을 외부 차상 동작으로 대체하는 공식 연결점.
- 차상 점유 센서가 Fleet의 적재·소유 상태 및 주행 허가에 연결되는 공식 인터페이스.

**판단: 인터페이스 계열은 존재한다. 현재 장비에서 바로 구현 가능하다는 확정에는 KUKA의 모델·버전별 지원 자료가 더 필요하다. 미확인은 미지원 확정과 다르다.**

Generic Action Executor(643~644페이지)는 하드웨어 동작을 모사하는 테스트용 stub으로 소개되며 갱신·취소를 지원하지 않는다. 이를 바로 생산용 차상 제어 솔루션으로 채택할 근거는 없다.

## 4. 방식 2: Fleet과 차상의 통신

### 확인한 구성 요소

2.15 매뉴얼의 근거:

| 구성 요소 | 위치 | 확인 내용 |
|---|---|---|
| Workflow Device Command | 348, 362~365, 463~465페이지 | 도착·출발 시 외부 장치 명령 실행, 작업 파라미터 전달, 완료 연동 |
| ECS Device / Rule Chain | 476~485페이지 | 장치 연결과 이벤트 처리 구성 |
| Read / Write data node | 487~489페이지 | PLC 값 읽기·조건 확인·명령 쓰기·실패 분기 |
| API call node | 490~491페이지 | 장치 이벤트 완료 후 WCS 등에 callback |
| Modbus TCP | 524~528페이지 | ECS=client/master, PLC=server/slave |
| S7 | 529~531페이지 | ECS=S7 client, PLC=S7 server |
| Conveyor Line 사례 | 547~559페이지 | Modbus/S7 허가·반송·완료·신호 초기화 시퀀스 |

차상 PLC에 서버에서 도달할 수 있는 IP/포트가 있다면 다음 구성이 구현 후보가 된다. **이동 차상을 ECS 장치로 연결하는 부분은 문서의 일반 외부 장치 기능을 적용한 설계 판단이며, KMP1500i 교체 리프트 전용 예제가 확인된 것은 아니다.**

```text
MCS -> 기존 SECS API -> Fleet Workflow
                          -> ECS Device Command / Rule Chain
                              -> 무선망 또는 라우팅된 Ethernet
                                  -> 차상 PLC -> 리프트 / 컨베이어
```

차상 통신모듈은 단순히 연결되어 있는 것만으로 충분하지 않다. Fleet 서버에서 PLC 주소로 실제 접근할 수 있어야 하고 로밍·재연결·응답 지연을 처리해야 한다. PLC의 내부 제어와 통신 유실 시 동작 정책도 별도로 정의해야 한다.

### 기존 기능을 활용하는 구현 순서

1. Equipment > Rule Chain Library에서 Custom Device 또는 적합한 장치 유형의 규칙을 만든다.
2. Equipment > Device에서 PLC IP·포트·프로토콜·레지스터 맵을 등록한다.
3. Workflow에 도착 후 차상 명령과 완료 대기를 배치한다.
4. 명령 쓰기 성공과 실제 동작 완료를 분리한다. PLC의 완료·센서·오류 상태를 확인한 후 WCS에 완료 callback을 반환한다.
5. 완료와 적재 상태가 확인된 뒤 다음 주행 단계로 진행하도록 구성한다.

예시 시퀀스(사용자 설계, KUKA 표준 명령 이름이 아님):

```text
출발 포트 도착
 -> 배차 로봇에 연결된 차상 선택
 -> Ready / 동작 허가 확인
 -> Load 요청
 -> Ack / Busy / 실제 Load 완료 및 센서 확인
 -> 작업 상태 및 적재 위치 반영
 -> 목적지 이동
 -> Unload 요청
 -> 실제 Unload 완료 및 센서 확인
 -> 작업 완료 보고
```

명령 재전송이 재동작을 만들지 않도록 요청 식별자·완료 식별자·통신 재접속 시 상태 대조가 필요하다. 이는 설계 요구이며 기존 ECS가 자동으로 모든 중복 방지를 보장한다고 확인한 것은 아니다.

### 자동 배차에서 꼭 해결할 항목

ECS의 장치 IP 설정과 Workflow의 Device Selection이 존재한다는 사실만으로, 자동 배차된 로봇에 맞는 차상 PLC가 자동 선택되는 것은 아니다.

`Fleet 내부 Robot ID -> 차상 Device ID / PLC endpoint` 연결을 설정 데이터로 관리하고, 실제 배차 결과에 따라 선택하는 경로를 구현·검증해야 한다. 이는 기존 MCS VehicleID·PortID에 별칭을 추가하는 작업과 다르다. 외부 식별자는 현재 일치시킨 값을 유지한다.

464~465페이지는 Business Data로 작업·컨테이너 관련 파라미터 전달을 설명하지만, 장치 IP·Device Selection 전체를 배차 Robot ID로 자동 치환하는 기능은 검토 문서만으로 확정하지 못했다. KUKA에 지원 방법을 확인하거나 서버 어댑터에서 처리할 부분이다.

## 5. 프로토콜별 범위

| PLC 통신 | AMR 직접 제어 | Fleet ECS 제어 |
|---|---|---|
| Modbus TCP | 현 KMP1500i 기본 클라이언트·확장 절차 미확인 | 내장 지원 확인. IP, 포트(기본 502), UnitID, FC, 주소·비트·데이터형·바이트 순서 설정 |
| Siemens S7 | 현 KMP1500i 기본 클라이언트·확장 절차 미확인 | 내장 지원 확인. IP, 포트(기본 102), Rack/Slot, DB, 주소·비트·데이터형 설정 |
| MC / SLMP | 해당 AMR에서 드라이버 실행 환경 확인 후 별도 구현 검토 | 검토한 지원 목록에서 기본 드라이버 확인 못함. 별도 어댑터 또는 프로토콜 게이트웨이 검토 |
| 제조사 전용 TCP | AMR 확장 환경과 통신 규격 필요 | TCP 송수신 노드는 존재하나 프레이밍·파싱 호환 확인 필요 |
| HTTP / OPC UA 등 | AMR별 미확인 | ECS 지원 절이 있음. 장치 제공 기능에 따라 선택 가능 |

TCP 지원 자체가 MC 지원을 의미하지 않는다. 531~532페이지 ECS TCP 프레이밍은 ETX 구분자 또는 4바이트 길이 Header/Body 방식으로 설명된다. 따라서 MC/SLMP 프레임을 주소 입력만으로 바로 사용할 수 있다고 판단할 수 없다.

MC 장치라면 구현 후보는 `ECS HTTP 또는 지원 프로토콜 -> 서버 어댑터 -> MC/SLMP -> PLC`이다. 외부 어댑터의 API와 PLC 드라이버는 개발 범위다.

Modbus/S7의 문서상 polling 최소값은 200ms, 기본값은 1000ms다. 실제 지연은 별도 측정해야 한다. 서버를 통한 시퀀스는 동작 지령·완료 확인에 사용하고, 모터의 세부 제어와 안전 정지는 장치 측 적합한 제어 체계에 맡기는 설계가 필요하다.

551페이지 Conveyor 예제 주소는 문서 자체가 참고용이라고 명시한다. 표의 일부 Function Node 표현도 526페이지의 표준 FC 설명과 일치하지 않으므로 실제 PLC 사양과 526페이지 FC 정의를 기준으로 확인해야 한다. 예시 주소를 현장 표준으로 고정하면 안 된다.

## 6. Standard API와의 관계

2.15 매뉴얼 647페이지:

```text
POST /interfaces/api/amr/submitMission
```

- 648~649페이지: `templateCode`로 구성된 Workflow를 호출할 수 있다.
- 649~650페이지: `RACK_MOVE`의 `UP_CONTAINER` / `DOWN_CONTAINER`.
- 651~652페이지: `ROLLER_MOVE`의 `ROLLER_RECEIVE` / `ROLLER_SEND`, `deviceCode`, `rollerLevel`, `actionConfirm`, `actionInform`.

이 API는 Fleet에 작업을 요청하는 상위 인터페이스다. 임의의 외부 PLC 주소에 리프트/컨베이어 명령을 직접 전송하는 드라이버 API가 아니다. `ROLLER_MOVE`가 존재해도 KMP1500i에 타사 컨베이어를 장착하는 것만으로 해당 로봇이 지원 Conveyor AMR로 변환된다는 의미는 아니다.

현재 사용 중인 SECS API와 상위 MCS 명령은 유지하면서 하위 작업 실행을 바꾸는 방향으로 설계할 수 있다. 어느 경로를 선택해도 실제 상태 확인과 완료 보고의 근거는 다시 연결해야 한다.

## 7. 현재 프로젝트에 필요한 변경 범위

로컬 소스 확인:

- `SECS/fleet_transfer_plugin/src/servostar/fleet/transfer/TransferService.java:219,239,242`
  - 현재 LIFT / RACK_MOVE 및 UP_CONTAINER / DOWN_CONTAINER 기반 작업 생성.
- `SECS/fleet_transfer_plugin/src/servostar/fleet/transfer/TransferJournal.java:214~232`
  - 해당 내장 리프트 ACTION_BEGIN / ACTION_END에 맞춘 단계 추적.
- `SECS/fleet_transfer_plugin/src/servostar/fleet/transfer/LoadedRecovery.java:74,119,201~204`
  - liftStatus와 containerCode 기반 적재·회수반송 판정.

따라서 외부 PLC에 쓰기 명령 하나를 추가하는 것만으로 작업이 끝나지 않는다. 명령 생성, 배차 로봇과 차상 연결, 적재 소유·위치, 시작·완료·취소·알람·회수반송과 재접속 복구까지 실제 차상 상태에 연결해야 한다. 기존 liftStatus를 외부 장치의 센서 확인 없이 완료 근거로 재사용하면 안 된다.

## 8. KUKA에 확인할 구체적 내용

### AMR 직접 방식

1. 당사 KMP1500i의 컨트롤러·펌웨어가 Fleet 2.15 매뉴얼 5.3.3의 Customized Actions를 지원하는가?
2. 고객용 Action Executor / Data Provider SDK와 예제, AMR 설치·등록·롤백 절차를 제공할 수 있는가?
3. AMR 내부에서 타사 PLC에 Modbus TCP·MC·S7 클라이언트 통신을 수행할 수 있는가? 기본 드라이버와 고객 개발 드라이버 중 어떤 방식인가?
4. 내장 리프트를 제거할 때 지원되는 로봇 모델 설정, 전기·안전 인터페이스, 적재 상태·출발 허가 및 완료 상태 연동 방법은 무엇인가?

### Fleet 직접 방식

1. 이동하는 차상 PLC를 ECS Custom Device로 운영하는 공식 권장 구성과 예제가 있는가?
2. 자동 배차 결과 Robot ID에 따라 해당 차상 Device ID를 선택하는 지원 기능/API는 무엇인가?
3. 외부 차상 완료와 Fleet의 컨테이너 소유·적재 상태를 일치시키는 지원 인터페이스는 무엇인가?
4. 통신 단절, 작업 취소, Fleet 재기동 후 차상 동작을 중복 실행하지 않는 권장 복구 방법은 무엇인가?
5. 당사 버전의 MC/SLMP 공식 드라이버 또는 지원 게이트웨이가 있는가?

## 9. 구현 우선순위 판단

현재 확보한 근거로는 **방식 2의 Modbus TCP 또는 S7 + ECS + Workflow**가 첫 검증 후보로 적절하다. 다만 이것은 구현 경로의 구체성에 따른 판단이며 현장에서 동작 검증을 마쳤다는 뜻은 아니다.

차상 동작을 서버 무선 통신과 분리해 로컬에서 실행해야 하는 요구가 있다면 방식 1의 지원 SDK 확보를 우선해야 한다. 직접 방식에서도 상위 명령은 Fleet에서 오므로, 서버 연결이 끊겨도 작업 전체가 계속된다고 가정할 수는 없다.

PLC 제조사·모델·맵과 KUKA의 연결 방법이 확정되기 전에는 레지스터 주소와 동작 코드를 임의로 고정하지 않는다. 장치 타입·프로토콜·IP·주소·데이터형·timeout·Robot ID 연결은 외부 설정으로 관리한다.

2.15.1 릴리스 노트 1페이지는 ECS Swagger를 포함한 디버그 포트(8082 등)가 기본 비활성화라고 명시한다. Swagger가 안 열린다고 기능이 없다고 판단하거나 포트를 임의 개방하면 안 된다. 현장 접근 방법은 해당 배포 설정과 제공 API 문서로 확인한다.
