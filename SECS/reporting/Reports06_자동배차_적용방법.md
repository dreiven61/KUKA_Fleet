# Reports06: 차량 보고와 Fleet 자동 배차

2026-10-28 로컬 작성. 서버 업로드·설정 변경·운송 전송은 하지 않았다.

## 변경

기존 1번 고정을 해제한다. 플러그인 0.5.0의 submitMission은 `robotIds: ["1","2","3"]`, `robotModels: ["KMP1500I"]`, `robotType: "LIFT"`를 전달한다. Fleet이 이 후보 중 하나를 선택하며 PC가 거리순으로 차량을 찍어 보내는 방식이 아니다.

공식 근거: KUKA.AMR Fleet 2.15 V2 매뉴얼 인쇄 p.342~347의 Pre-allocation / Re-allocation / Path cost, 표준 상위 통신 API의 robotIds 복수 후보 지원. 경로 비용에는 현재 계획 비용 등이 반영될 수 있으므로 단순 직선거리의 최근접이나 항상 Idle 우선이라는 보장은 없다. 이번 변경에서 서버의 배차 가중치·재할당 설정은 수정하지 않았다.

WCS의 해당 COMMANDID·화물·작업 인스턴스에 실제 들어온 차량 번호를 기록한다. 604 이후 RPTID5 차량 번호와 RPTID3 차량 번호/301 위치가 그 번호를 사용한다. 완료 jobQuery와 해제 robotQuery도 같은 실제 차량을 대조한다. 차량 번호가 중간에 달라지면 미확인으로 막아 다른 차량의 통지로 완료하지 않는다.

범위는 기존 SIM_CARGO_001 / PORT_89↔PORT_97 및 KMP1500I AMR 1/2/3이다. 차량·화물·포트 추가는 별도 설정/코드 변경이 필요하다.

## 적용 순서

1. 현재 운송이 207까지 끝났는지, Fleet Jobboard와 화물 위치를 확인한다. 시험 동안 SIM_REPEAT_01/02/03 반복 이벤트를 꺼서 배차 경쟁을 없앤다. 로봇은 운용 가능 상태여야 한다.
2. Interface Platform → Plugin Management에서 기존 SERVOSTAR-MES-TRANSFER 버전만 Disable한다. STANDARD-API는 유지한다. `SECS/fleet_transfer_plugin/build/servostar-mes-transfer-0.5.0.jar`를 Import → Enable하여 STARTED를 확인한다. 기존 JAR는 롤백용으로 보관한다.
3. Equipment → Rule Chain Library에서 `SECS/reporting/ecs_candidate/SecsTransferReports06_Auto_Ready_LOCAL_WITH_TOKEN.json`을 Import한다. 기존 Fleet 토큰이 10개 API 노드에 미리 들어 있다. 공개/공유용 파일이 아니다. 수동 중첩 편집은 필요 없다.
4. MES_SECS_TEST의 Rule Name을 `SecsTransferReports06_Auto_Ready`로 바꾼다. 기존 E82 연결 주소/Passive 포트5556/Session3000은 유지하고 저장·동기화·Enable을 확인한다. Reports04/05와 새 규칙을 혼합하지 않는다.
5. 기존 Virtual MCS와 Visual Studio 디버그 실행을 종료한다. `C:/work/KUKA/VirtualMCS/실행.cmd`로 실행하고 v1.3.0 및 상단 Fleet 자동 배차 문구를 확인한다. PC Active로 연결한다.
6. 실제 화물 위치에 맞춰 From/To를 한 번 전송한다. PC는 COMMANDID를 자동 생성한다. 별도의 AGV 번호 입력은 없다.

## 수신 기준

예상 순서:
`208 → 604 → 601(출발지) → 211 → 602 → 301 → 603 → 605 → 601(도착지) → 606 → 302 → 607 → 610 → 207`

- 604의 VEHICLEID와 Fleet Jobboard의 실제 Robot ID가 같아야 한다. 1번이 선택돼도 실패가 아니다. 당시 다른 후보의 경로 비용과 작업 상태를 함께 봐야 한다.
- 2/3번이 선택된 시험에서는 이후 차량 보고도 같은 번호인지 확인한다. 301 위치는 그 차량 번호, 302 위치는 도착 포트다.
- 601은 동일 CEID가 두 번이며 포트와 수신 이력으로 출발지/도착지를 구분한다.
- 전체 정상 종료는 207 Result=0 + 최종 CarrierLoc=도착 포트이다. S2F50, 302, 607, 610만으로 완료를 판정하지 않는다.
- 가까운 후보 검증은 실제 지도 경로가 가까운 빈 AGV를 준비하고 다른 작업/충전을 정리한 상태에서 비교한다. Fleet 선택이 예상과 다르면 설정과 경로 비용을 읽어 확인한 뒤 조정한다. 단순 좌표 거리로 임의 대체하지 않는다.

## 보고 의미와 한계

- 604는 성공한 실제 WCS 실행 통지의 차량 번호 확인이다. 설정상 후보 번호만으로 보고하지 않는다. 정확한 배차 결정 순간의 전용 콜백은 아니다.
- 601은 해당 단계 MOVE_END, 605는 적재 후 목적지행 MOVE_BEGIN이다. 물리 센서/주행계로 별도 검증한 것은 아니다.
- 610은 완료된 동일 작업, 도착지 하차 화물, 해당 AGV의 Idle(3)·비점유(0)·빈 적재·도착 노드를 조회해서 보고한다. unlock을 강제로 보내지 않는다. 다음 작업/충전 등으로 로봇이 곧바로 바뀌면 보수적으로 대기할 수 있다. 그 경우 재전송하지 말고 작업/로봇 상태를 먼저 확인한다.
- 성공한 조회만으로 운송을 재발행하지 않는다. submit은 1회, 이후 9개 API는 읽기 전용 상태 확인이다. 조회 재시도는 5초/최대120회다.
- 현재 Fleet의 선행 S2F50 B=0, EventReportSend의 S6F12 내용 미검사, ACK 거부/연결 단절의 영속 재보고 큐, 전체 abnormal/Cancel/Abort는 해결된 것으로 간주하지 않는다. 전 사양 완료본은 아니다.

## 로컬 검증

- 플러그인: 실제 캡처된 13개 WCS 통지 재생, AMR1/2/3 변경 시험, 다른 차량/작업/화물 혼입, 해제 조회, 중복/단절/재시작/인증/단일 제출 검증 및 격리 SBP 클래스 로딩.
- 규칙: 28노드/27연결, 10개 API, submit1개. 설치본 ECS의 실제 HTTP 응답 매퍼 → metadata → 33개 차량 포함 SECS 본문(11종×3대) 직렬화 확인. 숫자형 vehicleId를 매핑해 ID에 JSON 따옴표가 섞이는 문제를 피했다.
- PC: loopback HSMS로 14단계 보고, 원문/ACK, 동적 차량 식별과 저장, 불일치 차단. 실제 Fleet 운송은 수행하지 않았다.
