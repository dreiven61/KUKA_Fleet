## 최신 준비본: Reports06 자동 배차 (2026-10-28)

기존 1번 고정을 풀고 KMP1500I 1/2/3을 Fleet 배차 후보로 전달하는 플러그인 0.5.0, 차량 할당·출발지/도착지 도착·출발·해제 보고 규칙 Reports06, PC1.3.0을 함께 준비했다. 실제 배차 번호를 끝까지 추적한다. 서버에는 미적용이다.

적용 파일과 검증/한계는 [Reports06 적용방법](Reports06_자동배차_적용방법.md)을 따른다. 아래 이전 버전 기록은 과거 단계다.

# 진행·완료 보고 적용 현황

## 최신 상태와 다음 적용

**최신 추가: Reports04 + Virtual MCS 1.2.0의 301/302 화물 적재·하차 보고.** 토큰 포함 파일을 준비했으며 Fleet JAR 0.3.0은 유지한다. 적용은 [Reports04_적용방법.md](Reports04_적용방법.md)를 따른다. 기존 600번대는 사용자 수신과 16:51 서버 송신을 확인했다. 신규 301/302는 로컬 구현/검증 완료, 서버 적용·실제 송수신 확인 전이다.

2026-10-27: 기존 0.2.2/Reports02에서 실제 208·211·207 수신과 S6F12, 정상 왕복을 확인했다. 이번에는 플러그인 **0.3.0**, 별도 규칙 **Reports03**, PC **Virtual MCS 1.1.0**에 602·603·606·607 보고를 추가했다. 현재 서버에는 이 신규 변경을 적용하지 않았다.

**다음 절차는 [Reports03_적용방법.md](Reports03_적용방법.md)를 따른다.** JAR, 규칙 Import, 토큰 입력 위치, 디바이스 연결과 편도 시험 순서를 기재했다. 아래 내용은 이전 0.2.1 적용 이력이며 최신 검증 상태가 아니다.

## 이전 적용 이력

2026-10-27 기준. 마지막 서버 확인 이후 상태는 변할 수 있다.

- 실제 시험 TR_TEST_001: PC의 CEID208 수신과 같은 SystemBytes의 S6F12 Binary 0 응답을 확인했다. 증거는 `CEID208_송수신확인_20261027.md`.
- 내부 플러그인 `../fleet_transfer_plugin/build/servostar-mes-transfer-0.2.1.jar`: 실제 픽업 시작 뒤 `/progress`, 드롭 종료와 TASK_COMPLETED 및 최종 Job/화물 위치 확인 뒤 `/completed`가 성공한다. 두 API는 운송을 제출하지 않는다.
- 36개 기존 검사와 42개 보고 검사, 격리된 gRPC 실행 파일/전송 제공자/스텁 로딩 및 패키지 시작·정지 검사를 통과했다. 실제 WCS 로그 13개 재생은 로컬 검사이며 신규 실제 운송 시험을 대신하지 않는다.
- Fleet에서 0.2.1 STARTED 및 preview/progress/completed API 응답을 확인했다. 이후 13:53 Docker 데몬 전체가 재기동했고, 14:00에 STANDARD-API와 0.2.1의 STARTED 및 조회 API 응답을 다시 확인했다.
- 별도 규칙 SecsTransferReports02를 등록하고 10개 노드/9개 연결, 세 API의 기존 Authorization 토큰, 208/211/207 저장을 Export로 확인했다. 규칙 ID: `46ae5700-d1c1-11f1-9ae2-4521225892e9`. 원래 SecsTransferTest는 보존했다.
- 14:03 MES_SECS_TEST를 SecsTransferReports02에 연결하고 Enabled로 저장했다. 0.0.0.0:5556 / PASSIVE / Session 3000을 유지했다. 실제 신규 운송의 211/207 수신 시험은 남아 있다.

## 보고 순서

`ReceiveTransfer → 검증 → submit(재시도 0) → 208 → 픽업 시작 확인 → 211 → 최종 완료 확인 → 207`.

진행·완료만 5초 간격으로 최대 120회 재조회한다. 실패·취소·단절·재시작 때 정상 완료를 꾸며 보내지 않는다. 구독 단절/재시작 때 미완료 명령은 검토 대상으로 차단한다. 기록 파일은 서버 로그 볼륨의 `interface/servostar-transfer-journal.jsonl`이며 토큰은 기록하지 않는다.

## 설치본의 제한

확보한 ECS 이벤트 노드의 TransferInitiated 분기만 본문을 생성한다. 세 보고 노드의 UI Event는 이 분기를 사용하며 본문 CEID는 각각 208/211/207이다. 공급사 라이브러리로 전체 211/207 본문이 그대로 보존됨을 확인했다. 실제 PC 수신은 아직 시험해야 한다.

설치본 전송 함수는 ACK 본문을 검증하지 않는다. ACK 유실 때 이벤트가 중복될 수 있으므로 exactly-once 보장은 없다. 정상 207만 작성했으며 abnormal/알람, 취소/중단, 전체 SKI 시나리오 준수는 완료되지 않았다.

`ecs_candidate/SecsTransferReports02_IMPORT_CANDIDATE.json`은 토큰을 제거한 재생성용 파일이다. 라이브 Export에는 토큰이 있으므로 공유하지 않는다. `build/ibsem-report-bodies-0.1.0.jar`는 참고용 본문 라이브러리이며 설치 플러그인이 아니다.
