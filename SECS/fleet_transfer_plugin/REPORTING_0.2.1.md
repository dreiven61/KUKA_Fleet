# 보고 상태 확인 확장 0.2.1

기존 KMP1500i 가상 AMR 1 / SIM_CARGO_001 / PORT_89 ↔ PORT_97 범위를 유지한다.

- TransferJournal.java: COMMANDID 예약, 상태 상관관계 및 영속 JSONL 기록. 재시작/구독 단절 중 미완료 건 차단.
- WcsStatusMonitor.java: kwcs-engine:7998 작업 상태의 독립 읽기 전용 구독. WCS ACK, 배차, 로봇 동작을 호출하지 않는다.
- TransferService.java / TransferController.java: 기존 preview/submit 외 progress/completed 조회 추가. 미제출 명령은 UNKNOWN_COMMAND.
- package_runtime.py / MANIFEST.MF / build.ps1: 제공된 SDK에서 필요한 gRPC 실행 파일을 포함. 공식 STANDARD-API 2.15.5 의존.
- ReportingTest.java: 실제 로그 재생, 중복, 취소, 단절, 재시작 등 42개 검사.
- PluginLoadTest.java: 부모에서 gRPC를 숨긴 격리 환경에서 실행 파일/전송 제공자/스텁 로딩 및 패키지 시작/정지 확인. WCS RPC는 보내지 않는다.

0.2.0은 gRPC 클래스 누락으로 시작에 실패했다. 0.2.1은 실행 파일과 DNS 제공자를 포함했다. 2026-10-27 13:49 Fleet에서 STARTED와 세 조회 API 응답을 확인했다. 이후 13:53 Docker 데몬 전체가 종료됐다. 14:00 재활성화 후 STARTED와 조회 API 응답을 다시 확인했고, 14:03 ECS 장치에 보고 규칙을 연결했다.

파일: `build/servostar-mes-transfer-0.2.1.jar`.
SHA256: `A6037ED3542098D85698E343A395B70A62275225FA0267968EA713860BF2913C`.

ECS 보고 규칙의 장치 연결과 실제 호스트 시험이 필요하다. ACK 본문 검증/정확히 한 번 전달과 abnormal 보고는 추가 구현이 필요하다.
