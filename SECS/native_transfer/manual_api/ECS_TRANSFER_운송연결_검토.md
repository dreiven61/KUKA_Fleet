# ECS TRANSFER 운송 연결 검토 — 2026-10-26

## 현재 확인 결과

- PC Host Active → Fleet ECS E82 Passive, 192.168.0.45:5556, Session ID 3000 연결.
- S1F13 → S1F14 COMMACK=0 확인.
- TRANSFER S2F49 수신 및 COMMANDID/PRIORITY/REPLACE/CARRIERID/SOURCEPORT/DESTPORT 해석 로그 확인.
- 현재 저장된 SecsTransferTest는 Start → Inbound request → ReceiveTransfer까지만 있다. 운송 생성 연결은 미완성이다.
- S2F50의 단일 B=0 응답은 운송 생성/완료의 증거가 아니다. 설치된 Enhanced Remote Command Acknowledge 구현에서 운송 결과와 연결되는 정식 ACK 처리는 확인되지 않았다.

## 확인한 후보 경로

PC S2F49 TRANSFER → ECS ReceiveTransfer → 포트/노드 매핑 → ECS Grpc api call의 external system → Fleet Standard API submitMission → WCS 운송 생성.

외부 브릿지 프로그램은 필요하지 않는 후보지만, 내부적으로 HTTP API를 이용한다. 기본 nav-secs → Order Engine이 기존 KMP1500i New Sims를 직접 제어하는 경로를 검증한 것은 아니다. 이 경로를 적용했다는 사실도 아직 없다.

### API 요청

- Method: POST
- 외부에서 접근하는 기존 주소: http://192.168.0.45:5000/interfaces/api/amr/submitMission
- Header: Authorization에 기존 Fleet Access Token, Content-Type은 application/json. 이 문서에는 토큰을 저장하지 않는다.
- 본문 예시: ecs_transfer_candidate_89_to_97.json. 검토용이며 전송하지 않았다.
- templateCode는 넣지 않는다. 설치된 TaskManager.buildNodes 구현은 templateCode가 있으면 해당 템플릿 경로를 우선 사용한다.

### SECS 필드 매핑

| SECS | Standard API | 처리 |
|---|---|---|
| COMMANDID | requestId, missionCode | 주문 식별자로 사용. 동일 주문 재시도 시 새 식별자를 만들지 않는다. 중복 처리 동작은 별도 검증 필요. |
| PRIORITY | priority | 숫자 타입 유지 |
| CARRIERID | containerCode | SIM_CARGO_001 등 |
| SOURCEPORT | missionData[0].position | PORT_89 → lay01-dist1-89, PORT_97 → lay01-dist1-97 |
| DESTPORT | missionData[1].position | 같은 매핑 사용 |
| REPLACE | 직접 대응 확인 안 됨 | 임의로 무시하거나 API 필드로 추가하지 않는다. 최초 시험은 0만 허용하는 검사 필요. |

PORT_89/PORT_97 자체를 Standard API의 지도 노드 position에 넣으면 안 된다. ECS 표현식/분기에서 매핑해야 한다. 해당 표현식 문법과 실제 노드 구성은 아직 입력·검증하지 않았다.

## 오프라인 검증

설치 배포본 STANDARD-API-2.15.5-jar-with-dependencies.jar의 RackMoveNodeActionHandler.doHandle에 예시 JSON을 로컬에서 전달했다. 서버 애플리케이션 기동, DB 접근, HTTP 요청, 로봇 명령 없이 변환 메서드만 호출했다.

결과:

```text
1 lay01-dist1-89 UP_CONTAINER
2 lay01-dist1-97 DOWN_CONTAINER
OFFLINE_CONVERSION_OK; NO_REQUEST_SENT
```

검증 도구: tmp/rcs_native_link_inspect/CheckTransferCandidate.java.
검증용 의존 라이브러리는 tmp/rcs_native_link_inspect/offline_deps에만 저장했다. Fleet 서버 설치본은 변경하지 않았다.

이 결과는 JSON → 내부 노드 동작 변환의 증거다. 라이브 Fleet의 접수, 경로 계획, 화물 상태, 운송 실행, 완료 이벤트의 증거는 아니다.

## 남은 적용 작업

1. 브라우저 제어 복구 후 미저장 임시 Grpc api call 노드 상태부터 확인한다. 기존 ReceiveTransfer 본문은 보존한다.
2. 포트 매핑 및 미등록 포트/REPLACE 검사 구성.
3. external system의 URL/Headers/RequestBody와 success 응답 검사 구성. 기존 wcs/interface(HTTP) 콜백을 운송 생성 기능으로 사용하지 않는다.
4. 조회 API로 ECS의 HTTP 연결·인증·응답 검사를 먼저 검증한다.
5. 가상 로봇/화물/도착지/기존 작업 상태를 조회한 뒤 단일 신규 COMMANDID로 한 번만 운송 시험한다. 응답 불명일 때 자동 재전송하지 않는다.
6. S2F50 ACK, 실패 응답, 완료 이벤트와 COMMANDID 연결은 별도 구현·검증해야 한다. 위 후보만으로 SECS 전체 연동 완료라고 판단하지 않는다.

현재 브라우저 도구는 “Local browser use is disabled in Work settings”를 반환했다. 임시 노드는 저장하지 않았으며, 이 상태에서 화면 정리나 설정 저장은 수행하지 못했다.
