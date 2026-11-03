# PAUSE / RESUME 수정본 적용

- Fleet 플러그인: `../fleet_transfer_plugin/build/servostar-mes-transfer-1.1.5.jar`
- PC 프로그램: `../../VirtualMCS/dist-2.1.3/KukaVirtualMCS.exe`
- 포트 5556 / Session ID 3000 / 기존 토큰과 서버 설정은 그대로 사용합니다.

## 원인과 수정

실제 RCS는 PAUSED → RESUMING → EXECUTING → FINISHED로 전환했지만,
플러그인은 REST 조회에서 동일 작업의 EXECUTING 상태를 확인할 때만 RESUME을 끝냈습니다.
그 조회 시점을 놓치면 Fleet 작업이 완료되어도 RESUMING에 남고 다음 TRANSFER가 HCACK2로 거부됐습니다.
첫 번째 재개의 지연을 일으킨 개별 REST 필드까지는 확정하지 않았습니다.

1. 제어 대기 중 확인 주기를 1초에서 250ms로 줄였습니다. 실제 API 지연은 별도입니다.
2. 이동(W-)뿐 아니라 동일 작업 인스턴스의 상하차(C-) 실행 상태도 확인합니다.
3. 실행 상태를 놓친 경우, 누락 없는 WCS 상하차·완료 이력 + 정확한 작업/인스턴스/AMR + Fleet 정상 완료 + 실제 목적지 화물 하차를 모두 검증한 뒤 RESUME과 후속 보고를 처리합니다.
4. PAUSE 확인 전에 작업이 먼저 완료되면 가짜 CEID209를 만들지 않고 실제 완료를 보고합니다. 시스템은 PAUSED를 유지하며, 이후 RESUME으로 AUTO로 전환합니다.
5. 완료 후 AMR이 대기 위치로 이동해도 Idle·비점유·빈 적재·작업 연결 해제를 검증하면 CEID610을 보고합니다.
6. 확인된 보고를 한 건당 1초씩 늦추지 않고 순서대로 큐에 넣습니다. 중간 이력 확인에 불필요한 HTTP 조회도 제거했습니다.
7. 제어 완료 후 TSC 전환을 먼저 저장합니다. 이후 완료 위치 조회가 실패해도 확인된 RESUME을 RESUMING에 묶지 않습니다.
8. PC는 정상/비정상 최종 CEID207 수신 시 남은 제어 대기를 정리합니다. 늦은 제어 보고로 완료 작업을 다시 열지 않습니다.

REST 접수 ACK, status=30 단독, 과거 MOVE_BEGIN 콜백만으로 완료/재개를 만들지 않습니다.
관측 누락·인스턴스 불일치·실제 화물 위치 불일치는 계속 보류합니다. 운송·제어 POST 자동 재전송은 없습니다.

## 적용 순서

1. 새 명령 전송을 멈추고 기존 Virtual MCS를 종료합니다.
2. Fleet → Interface Platform → Plugin Management에서 **1.1.4를 Disable**합니다.
3. **1.1.5 JAR를 Import → Enable**하고 STARTED를 확인합니다. 동일 플러그인 여러 버전을 동시에 활성화하지 않습니다.
4. **VirtualMCS/실행.cmd** 또는 dist-2.1.3의 EXE를 실행하고 연결합니다. Visual Studio 빌드도 소스 버전 2.1.3을 사용합니다.
5. 기존 완료 작업의 누락 보고와 CEID210/103, CEID610/207을 확인합니다. 1.1.5는 보존된 완료 이력을 읽어 검증하며 운송을 다시 보내지 않습니다.

기존 JSON/JSONL 상태 파일을 삭제하거나 이전 `recover_stale_pause.py`를 다시 실행하지 마세요.
이전 스크립트는 별도의 취소된 작업 전용입니다. 현재 완료 작업을 복구하는 방법이 아닙니다.
새 버전을 올리기 위해 compose/YAML이나 포트를 바꿀 필요는 없습니다.

## 확인 순서

1. 화물이 있는 실제 출발 포트로 운송 1건을 시작합니다.
2. 이동 중 PAUSE → HCACK4 → CEID107 → CEID209 → CEID105 확인.
3. RESUME → HCACK4 → CEID210 → CEID103 확인.
4. 같은 작업에서 PAUSE/RESUME을 한 번 더 반복합니다. 보고 전에 버튼을 연타하지 않습니다.
5. 실제 하차 후 CEID610과 CEID207(Result=0), 다음 운송 접수를 확인합니다.

`mes-secs/status`의 각 작업에는 `instanceId`, `controlGeneration`, `controlEvidence`가 추가됩니다.
확인 근거는 `RCS_PAUSED`, `RCS_EXECUTING`, `COMPLETED_LIFECYCLE` 등으로 구분합니다.
관측 이력이 부족하면 강제로 해제하지 않습니다. 이 경우 상태와 로그를 확인해야 합니다.

로컬 회귀 테스트와 루프백 통신 검증만 수행했습니다. 서버 교체와 실제 AMR 재시험은 별도입니다.
