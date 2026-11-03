# SECS 사양 대조 및 동적 Fleet 데이터 구성

작성일: 2026-11-02. 아래의 로컬 빌드/시험 결과는 서버 배포나 실제 장비 운전 결과가 아니다. 확인 시 실행 중인 서버 플러그인은 1.3.1이며, 새 로컬 빌드는 플러그인 1.4.0 / Virtual MCS 2.3.2이다.

## 사양·구 로그 대조

기준 문서: SKBJ2 MCS IB-SEM BasicSpec v0.4, MessageSpec v0.4, ScenarioSpec v0.5(2021-07-22). 사용자 지정 HOST01/HOST02 SECS-II 및 SECS-I 로그, back 로그를 읽었다. 추출 증거는 `tmp/spec_audit_20261102`에 보관한다.

| 항목 | 근거와 판단 | 수정 |
|---|---|---|
| VehicleID | BasicSpec p.34는 ASCII 1~32자. 숫자로만 구성된 `1`이나 `100001`도 문자열로 전송하면 유효하다. 과거 로그의 `C0VGVA01_VC05`는 해당 현장의 ID이며 모든 현장이 따라야 할 고정 이름은 아니다. | Fleet 응답의 실제 robotId를 그대로 읽는다. 임의로 영문 이름을 만들어 붙이지 않는다. |
| 항목명 표시 | BasicSpec p.32의 SV25는 L[n] → L3(VehicleID, VehicleState, VehicleStatus). SECOM의 `[VehicleID]` 같은 이름은 로그 주석이다. | 상태 표와 SML에 사양 이름을 표시하되 wire 구조는 유지한다. |
| 현재 운송 | BasicSpec p.31~32의 SV23은 EnhancedTransfers → CommandInfo(CommandID, Priority), TransferState, TransferInfo 목록. 조사한 구 S1F4 3건의 현재 운송은 모두 L0였다. | 활성 운송이 있는 상태는 사양과 로컬 fixture로 검증한다. 구 로그에 활성 운송 예제가 있었다고 주장하지 않는다. 실제 활성 운송이 없으면 CommandID를 만들어 표시하지 않는다. |
| S1F3/S1F4 | MessageSpec p.7. 응답 값은 요청한 SVID 순서이며 S1F4 안에 SVID/항목명을 별도 문자열로 추가하지 않는다. | SystemBytes와 요청 SVID를 연결해 로그 주석을 붙인다. 요청 정보가 없거나 알 수 없는 보고 구조는 이름을 추측하지 않는다. |
| S2F49 COMMANDINFO | MessageSpec p.15~17의 기본 항목은 COMMANDID와 PRIORITY다. | MCS는 기본 두 항목을 보낸다. 플러그인은 기본 형식과 이전 REPLACE=0 확장 형식을 모두 받는다. REPLACE=1 교체 동작은 허용하지 않는다. |
| S2F50 | 항목명은 HCACK/CPACK이다. | 로그 이름을 맞춘다. |

SECS-I 2024-04-25 14시 로그의 S1F4는 header 10바이트/body 178바이트였다. body를 시험 fixture로 추출해 현재 인코더의 바이트 배열과 대조한다. 주석 표시 전후의 wire 바이트가 같아야 한다. 구 로그는 INVALID/UNKNOWN 및 응답이 없는 사례도 포함하므로 무조건 정상 사양으로 간주하지 않는다.

## Robot ID의 정수 제한과 문자열 자료형

Standard API SDK의 RobotQueryDto/RobotDto.robotId와 WCS InsertRobRequest.robId는 String이다. Fleet 매뉴얼도 API의 robotId를 String으로 정의한다(p.684, p.694). 그러나 New Sims는 별도 시뮬레이터 등록 경로를 사용한다. API 자료형만으로 이 등록 경로의 영문 ID 허용 여부를 결론 낼 수 없다. 현재 등록 경로에서 정수만 받는 경우 숫자 ID를 유지해도 동적 연동에는 문제가 없다.

숫자 ID `100001`은 SECS에 `<A "100001">`로 전송할 수 있다. 현재 조회한 Standard API 응답과 Robot List 편집 화면에서는 별도 robotName 필드를 확인하지 못했다. Detail의 Call Code는 화면 코드상 DB의 id이며 이름으로 간주하지 않는다. 영문 현장 이름이 필요하면 Fleet이 제공하는 별도 필드/설정 경로와 양방향 식별자 규칙을 확인해야 한다. 없는 이름을 만들어 동적으로 받았다고 표시하지 않는다.

## Fleet/API의 동적 구성

- 시작 시 robotQuery로 차량 ID/모델을, Basic Data의 GetAllMapInfo/GetMapFloor로 전체 지도/층/노드를, queryFunctionNode(functionType=1)로 작업장 포트를, queryAllContainerModelCode로 화물 모델을 읽는다. 로봇이 있는 지도만 찾는 방식은 사용하지 않는다.
- 결과를 하나의 카탈로그로 검증한 뒤 교체한다. 기본 gRPC 주소는 서버 설정 metadataGrpcTarget으로 변경할 수 있다. 현장 로봇/지도/포트/모델 목록은 운영 소스에 넣지 않는다.
- 5초 주기 갱신 및 새 운송 전 갱신을 수행한다. 한 요청의 운송 구성/완료 확인/상태 조회는 같은 카탈로그 스냅샷을 사용한다. 작업장별 허용 로봇 모델과 실제 화물 모델을 배차 후보에 반영한다.
- 포트 ID는 Fleet의 externalCode가 있으면 그 값을, 없으면 실제 nodeCode를 사용한다. 이전 PORT_숫자 별칭은 Fleet nodeLabel에서 유도하며 충돌하는 별칭은 허용하지 않는다.
- 스캔 실패 시 정적 목록으로 대체하지 않고 새 운송을 제출하지 않는다. 기존 작업은 마지막 확인된 바인딩을 읽는다. 실행 중 미완료 작업이 있는 동안 포트 바인딩 교체는 차단한다. 배포 후 재기동·스캔 실패·메타데이터 변경 통합 시험은 별도 필요하다.
- loadedAbortWaitNodes는 자동으로 추정할 수 없는 이동 정책이다. 차량 ID 제한은 제거했지만 실제 안전한 대기 노드는 현장 설정으로 지정해야 한다.
- MCS 시험 도구의 입력용 정적 목록은 사용자 요청대로 유지한다. Fleet/API의 운영 데이터 목록과 구분한다.

## 검증과 남은 범위

플러그인의 기존 회귀시험 및 패키지 분리 로딩 시험에 더해 DynamicCatalogTest는 실제 HTTP/gRPC 어댑터를 로컬 서버에 연결한다. 기존 현장과 다른 지도/층/노드/모델, 숫자 ID 100001 및 영문 ID AGV_WEST_01, 차량 교체, 다른 지도 발견, 모델 선택, 스캔 실패 시 submitMission 미전송, 마지막 확인된 포트 보존을 검사한다. 영문 ID fixture 통과는 실제 Fleet 등록 지원의 증거가 아니다.

MCS 2.3.2는 347개 기본 검증 및 34개 화물 / 37개 ACK / 15개 적재 복구 검증을 통과해 dist-2.3.2에 게시했다. 구 SECOM wire fixture 및 항목명 표시 시험을 포함한다. 실행 중인 이전 창은 새 실행 파일로 자동 교체되지 않는다.

남은 실제 검증: 서버 1.4.0 배포 후 catalog.ready와 실제 차량/지도/포트/모델 확인, 변경된 실제 Robot ID 조회, MCS 2.3.2 실행 화면과 실제 S1F3/S1F4 주석 로그 확인, 운송 중 메타데이터 변경/단절/재기동 통합 시험. 이 문서 작성 시 이를 완료했다고 보고하지 않는다.

전체 사양 적합성의 별도 제한: CommandID는 사양상 최대64자지만 현재 프로젝트 SECS 프로필은 downstream 저장 한계를 고려한32자 제한을 유지한다. 설치 시각 A0(미상), 빈 포트 ProductEmpty=0은 현재 프로필 동작이며 사양의 완전한 대체값이라고 주장하지 않는다.
