# S1F3 상태 동기화 및 단절·ACK·재기동 시험

2026-10-30 Windows/KST 기준. 대상은 기존 SIM01 KMP1500I 3대와 Native SECS 5556이다. 실제 하드웨어 및 실제 운영 MCS 연결 시험은 아니다. ACS 소스와 SKI 원본 규격의 메시지 구조를 사용한 HSMS 시험 호스트로 검증했다.

## 변경

- 1.2.3 서버에 S1F3을 3회 전송했으나 모두 S1F0이었다. 정상 상태 조회가 구현되지 않은 상태였다.
- 1.3.0에 S1F3/S1F4를 추가했다. SV23 현재 운송, SV25 차량, SV58 관리 포트, SV21 화물, SV3 알람, SV17 제어 상태, SV46 TSC를 지원한다. 요청 순서·중복 SVID를 유지하고 알 수 없는 SVID는 해당 위치의 L0로 응답한다. 전체 요청 L0도 지원한다.
- 매 조회마다 Fleet의 차량·화물·활성 작업을 새로 읽는다. 조회 실패·잘림 가능성·서로 충돌하는 상태는 S1F0로 응답한다. 성공한 빈 작업 목록으로 바꾸지 않는다. 실패한 조회를 받은 MCS는 재조회해야 하며 빈 상태로 판단해 운송을 재발행하면 안 된다.
- 활성 Fleet 작업과 Native 보존 작업을 합친다. 운송이 끝나도 운송·차량·화물 보고 ACK가 남아 있으면 SV23에 WAITING(6)으로 유지한다. 같은 화물의 새 COMMANDID도 HCACK=2로 거부한다. 같은 COMMANDID는 HCACK=5이며 재접수하지 않는다.
- 상태 조회를 운송 명령 및 보고 ACK 대기와 별도 실행한다. ACK를 기다리는 동안에도 조회를 처리한다. 일반 Fleet 요청의 기존 타임아웃은 유지하고 상태 읽기 요청에만 짧은 연결·읽기 제한을 적용했다.
- 실서버 첫 적용에서 SDK 차량 enum의 플러그인 격리 문제가 발견되어 숫자 REST 상태 매핑으로 변경했다. private RCS protobuf 클래스를 숨긴 패키지 실행 검증을 추가했다. 최종 배포 SHA256은 `21F0480B1170CE48DD5CFAF9B31672BFB8FC749D7A7022B6EDB97E4C0DF8CA44`이다.

## 시험 기록

이번 범위의 통합 시험은 통과했다. 로컬 검증2,104개와 격리 패키지의 로딩·차량 조회·기동/정지 검사도 통과했다. 아래 범위 밖인 운송 중 재기동의 자동 완료 복구까지 통과했다는 뜻은 아니다.

| 시험 | 결과 |
|---|---|
| 운송·단절·ACK 대기 중 연속 상태 조회 | 1,617회 모두S1F4, 중앙78ms, 95%109ms 이내, 최대266ms |
| 차량25→포트58→현재 작업23을 응답 대기 없이 연속 송신 | 100묶음/300응답 모두통과, 중앙141ms, 95%234ms 이내, 최대343ms |
| 배차604 ACK 유실+MCS 단절 | DATAID378이125.009초 후 같은ID로 재전송, 운송6903 1건 |
| 완료207 ACK 거부+플러그인 재기동 | DATAID390/BLOCKED 보존, 명시적 보고 retry 후 동일ID/ACK0로 복구 |
| 연결 유지 중 복귀607 ACK 유실 | DATAID404가125.021초 후 같은ID로 재전송, 운송6904 1건 |
| 최종 복구 | 운송6903/6904 모두207/Result0, 미확인 보고0, 활성 알람0, 미완료 Native 작업0, TSC=AUTO |

SIM_CARGO_001은PORT90으로 복귀했고 기존3대는 빈 차·Idle·미션 없음이다. 시험 차단 영역ID1은비활성, 기존Virtual MCS2.2.0은COMMUNICATING으로 복원했다. 실제 운영 MCS 시험을 대신한 것으로 해석하지 않는다.

- 원본: `../../tmp/reconnect_test_20261030/integration_run1/wire.jsonl`
- COMMANDID 사전 보존: `../../tmp/reconnect_test_20261030/integration_run1/commands.json`
- 자동 판정: `../../tmp/reconnect_test_20261030/integration_run1/results.json`
- 연속3요청 판정: `../../tmp/reconnect_test_20261030/burst.results.json`
- 빌드: `../../tmp/reconnect_test_20261030/build_1.3.0.log`
- 재기동 직후 BLOCKED DATAID390 보존: `../../tmp/reconnect_test_20261030/restart_after_status.json`

1. 대기 중 연속 조회 102회: 차량3/관리 포트10/운송0, 최대140ms, 전부 S1F4.
2. `B07DCDED36CF4B3BAC744A938E668D65`, PORT90→97, Fleet6903: 배차 보고604 DATAID378의 ACK를 유실하고 MCS 접속을 끊었다. 재접속 후 기존 작업이 즉시 조회됐다. 완료까지 같은 작업이 목록에 유지됐다.
3. 완료207 DATAID390에 ACK=1을 반환했다. BLOCKED가 보존되고 계속 조회됐다. 같은 COMMANDID는HCACK5, 같은 화물·새 COMMANDID는HCACK2, 실제 Fleet 작업은6903 1건이었다.
4. 완료 보고가 BLOCKED인 상태에서 플러그인을 Disable→Enable했다. 같은 COMMANDID와 DATAID390 및 BLOCKED 상태가 유지됐다. 명시적 retry-report 후 같은 DATAID390이 재전송됐고 ACK0 후 SV23에서 제거됐다. 운송 명령은 재전송하지 않았다.
5. `F1AD223C00584AD08218B72FC3E558AA`, Fleet6904, PORT97→90 복귀 중 보고607 DATAID404의 ACK를 1회 유실했다. 연결을 유지하고 연속 조회하면서125.021초 후 동일DATAID 재전송 및ACK0를 확인했다. 뒤이어610과207/Result0이 전송됐고 보고 대기열과 현재 운송 목록이 비워졌다. 운송 등록은1회뿐이다.

## 범위와 남은 확인

- **이번 재기동 시험은 실제 운송 완료 후 미확인 보고가 남은 시점이다. 이동·리프트 동작 중 플러그인 재기동의 자동 완료 복구를 통과했다고 판단하면 안 된다.** 현재 TransferJournal은 재기동/콜백 단절 시 미완료 항목을 STATUS_UNCERTAIN으로 보존한다. 누락된 픽업·하강·해제 사실을 추측해 정상 완료 보고를 만들지 않는다. 운송 중 재기동의 완전 자동 보고 복구는 별도 보완·통합 시험이 남는다.
- ACK 유실 후 재전송은 운영 설정 T3=120초의 영향을 받는다. 상태 조회가 빠르게 응답하는 것과 보고 재전송 지연은 구분한다.
- SV21의 InstallTime은 Fleet API에 권위 있는 값이 없어 A0(미상)이다. SV58의 InService는 기존 10개 포트 프로필 기준이며 PLC 인터록 확인값이 아니다. 실제 운영 MCS가 이 값을 수용하는지도 별도로 확인해야 한다.
- Fleet REST 조회 사이의 상태 변화로 충돌이 감지되면 S1F0를 반환한다. 서로 다른 외부 클라이언트가 동시에 등록하는 운송을 전역 원자 예약하는 기능을 구현한 것은 아니다.
- 기존 PC/ECS 경로, 공식 SKI ALID, 로봇 종류, 맵, 포트 및 다른 작업의 저널은 변경하지 않았다.

## 재실행

기존 Virtual MCS 연결을 먼저 해제한다. `live_reconnect_test.py --execute-sim --config <기존 로컬 토큰 설정> --output <새 증거 폴더>`는 기존 SIM01 3대가 대기 중이고 SIM_CARGO_001이 PORT90에 있을 때만 진행한다. COMMANDID를 전송 전에 저장하며, 같은 출력 폴더로 다시 실행해 기존 명령을 재전송하지 않는다. `restart_ready.json` 이후 설치된 동일 플러그인의 정지·시작을 확인한 다음 `restart_done` 파일로 시험을 이어간다. 실패한 명령은 보존된 COMMANDID로 상태를 조사한다.
