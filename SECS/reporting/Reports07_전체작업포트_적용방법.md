# 전체 작업 포트 적용

작성: 2026-10-28. 로컬 파일 작성·빌드·모의 검증 완료. 서버 업로드·규칙 교체·운송 전송은 실행하지 않았다.

## 범위

Virtual MCS 1.4.0 + Fleet 플러그인 0.6.0 + Reports07을 함께 적용한다.

`lay01/dist1` 작업 포트 10개:

`PORT_84, PORT_86, PORT_89, PORT_90, PORT_93, PORT_94, PORT_95, PORT_96, PORT_97, PORT_98`

- 현재 지도 Export `MAP/map_lay01_dist1/map_lay01_dist1_yft.json`의 SIM_TRAY_001 작업 기능 노드에서 확인했다. 충전소 87/88, 충전 입구 99/100, 일반 경유 노드는 제외한다.
- 서로 다른 포트의 90개 조합을 허용한다. 포트 숫자를 임의의 지도 노드로 바꾸지 않는다.
- 화물은 기존 `SIM_CARGO_001`을 유지한다. 실제 화물이 있는 포트를 From으로, 화물이 없는 작업 포트를 To로 선택한다. 다른 화물 이동/삭제는 이 변경에 포함하지 않았다.
- 신규 포트에 External Code를 추가할 필요는 없다. 기존 89/97의 `SECS_ST_89/97`은 그대로 둔다. 나머지는 현재 지도 UUID로 매핑한다. 실제 containerQuery에서도 84/94의 UUID 반환을 확인했다.
- 향후 지도에 작업 포트를 추가하거나 External Code를 변경하면 카탈로그도 갱신해야 한다. 현재 실행 중인 지도를 자동 검색하는 기능은 아니다.

## 사용자 적용 순서

1. 진행 중인 운송이 끝난 뒤 Virtual MCS 기존 창과 Visual Studio 디버그 실행을 종료한다. 실행 중인 이전 프로그램은 새 EXE를 눌러도 자동 교체되지 않는다.
2. Fleet → Interface Platform → Plugin Management에서 기존 `servostar-mes-transfer-0.5.0`을 Disable한다. STANDARD-API는 유지한다.
3. `C:\work\KUKA\SECS\fleet_transfer_plugin\build\servostar-mes-transfer-0.6.0.jar`를 Import하고 Enable한다. **0.6.0 STARTED**를 확인한다. 다른 버전의 같은 플러그인은 Enable하지 않는다.
4. Equipment → Rule Chain Library에서 `C:\work\KUKA\SECS\reporting\ecs_candidate\SecsTransferReports07_AllPorts_Ready_LOCAL_WITH_TOKEN.json`을 Import한다. 기존 Fleet 인증 토큰이 들어 있는 로컬 적용 파일이다. 외부로 공유하지 않는다. 토큰이 제거된 공개용 파일은 `_IMPORT_CANDIDATE.json`이다.
5. Equipment → Device → MES_SECS_TEST를 Disable하고 편집한다. Rule Name을 **SecsTransferReports07_AllPorts_Ready**로 변경한다. 기존 E82 Passive / 5556 / Session3000 연결 설정은 유지한다. 저장 → 동기화 → Enable한다.
6. `C:\work\KUKA\VirtualMCS\실행.cmd`로 실행한다. 하단 **v1.4.0**, From/To 목록 **10개**를 확인한다. 연결 전에는 운송을 보내지 않는다.
7. PC Active로 연결하고 실제 화물 위치에 맞춰 새 경로를 한 번 보낸다. 예: 현재 화물이 89에 있고 98이 비어 있다면 **PORT_89 → PORT_98**. 이 문서를 작성하며 운송은 전송하지 않았다.

## 메시지 확인

기존 정상 보고 순서는 동일하다.

`208 → 604 → 601(From) → 211 → 602 → 301 → 603 → 605 → 601(To) → 606 → 302 → 607 → 610 → 207`

14건의 S6F11에 같은 SystemBytes의 S6F12 **ACKC6=0**이 각각 있어야 한다. 207 Result=0, CarrierLoc=선택한 도착 포트까지 확인한다. S2F50 하나만으로 접수·완료를 판정하지 않는다.

11:03:16~11:04:23 실제 로그에서는 AMR3의 97→89 작업이 정상 207까지 끝났지만, 실행 파일이 Debug 1.2.0이라 604/601/605/610에 ACKC6=1을 보냈다. 새 PC 배포본의 기존 1.3.0 보고 처리 코드와 1.4.0 포트 확장을 사용해야 한다.

## 로컬 검증 / 한계

- PC: 250개 검사 통과. 90개 포트 조합의 실제 S2F49 본문, 같은 포트/충전/일반 노드 거부, 14단계 보고·ACK를 loopback HSMS로 검사했다.
- 플러그인: 90개 경로의 캡처 WCS 콜백 재생, 207/610 최종 위치 확인, 잘못된 화물 위치 거부를 포함한 1,172개 추가 검사 통과. 기존 인증·중복 방지·상태 단절·SBP 클래스 로딩 검사도 통과했다.
- 규칙: PC/Java/지도 포트 목록 일치, 서버 설정과 UI 그래프 양쪽의 포트 검증식, 28노드/27연결/API10개/submit1개를 검사했다.
- 새 포트 실제 운송, 목적지 점유·가용 경로, 모든 보고의 실제 ACK0은 서버 적용 후 확인한다. 미확인 명령 자동 재전송과 Fleet 선행 S2F50/ACK 영속 큐의 기존 한계는 그대로다.
