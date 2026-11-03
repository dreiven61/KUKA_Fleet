# S1F3 AGV 조회 실패 원인 분석 — 2026-11-02

## 결론

플러그인 1.3.0이 서로 다른 시점에 읽은 차량/화물 상태를 하나의 일관된 상태로 검증하다 실패한 문제다.
실제 Fleet API 기록의 차량 응답 72190과 화물 응답 72191에서 불일치를 확인했고, 당시 8개 API 응답을 배포 JAR에 재생하여 `Carrier snapshots disagree; requery before dispatch`를 재현했다.
바로 다음 8개 응답(72198..72205)은 같은 코드에서 통과했다.
추가 Import나 작업 재발행으로 해결할 문제가 아니다.

## 실제 통신 증거 — PC 로그 시각

- 10:35:11.002: S1F3 / SVID 25 요청, SystemBytes 0x5C4F9028.
- 10:35:11.150: 동일 SystemBytes의 S1F0 수신.
- 10:35:11.155: MCS가 152.5 ms 조회 실패 표시.
- 10:35:11.284: SVID 58 정상, 관리 포트 10건.
- 10:35:11.429: SVID 23 정상, 현재 운송 1건.
- 10:35:25.001: COMMANDID 795B502514064D0B8B3DA58B4BE201B9, CEID 207, Result 0, PORT_89 완료.
- 10:35:25.002: MCS가 완료 이벤트 ACKC6 0 송신.

운송은 workflow 6905 / AMR 2 / SIM_CARGO_001 / PORT_90 → PORT_89이며 정상 완료됐다.

## 실제 Fleet API 원본

Interface Platform의 Record 화면에서 당시 저장된 응답을 조회했다. 인증 헤더는 증거 파일에 저장하지 않았다.

| 기록 ID | API | AMR 2 또는 SIM_CARGO_001의 응답 |
|---|---|---|
| 72182 | robotQuery | containerCode="", liftStatus=0 |
| 72183 | containerQueryAll | isCarry=0 |
| **72190** | **robotQuery** | **containerCode="", liftStatus=1** |
| **72191** | **containerQueryAll** | **isCarry=1** |
| 72198 | robotQuery | containerCode="SIM_CARGO_001", liftStatus=1 |
| 72199 | containerQueryAll | isCarry=1 |

72190..72197의 robotQuery, containerQueryAll 및 상태별 jobQuery 6개는 모두 success=true다.
차량 응답에서는 리프트 상승을 확인했지만 화물 ID가 아직 비어 있다. 뒤이어 읽은 화물 응답에서는 이미 적재 중이다.
다음 조회에서 차량에도 화물 ID가 반영된다. 작업 조회의 중복/서로 다른 상태 행 충돌은 이 묶음에서 없었다.

서버 기록 시각은 2026-10-27 10:15:32이고 PC 로그는 2026-11-02다. 두 장비의 시계가 다르므로 절대 시각만으로 밀리초 단위 대응을 주장하지 않는다.
원본 응답 묶음, 동일 운송 식별자, 상태 전환 순서 및 로컬 재생 결과를 함께 근거로 사용했다.

## 발생 경로

1. `SelectedStatusService.read()`는 SVID 25만 요청해도 robotQuery → containerQueryAll → 상태별 jobQuery 6개를 순서대로 호출한다. 같은 시점의 원자적 조회가 아니다.
2. 외부 API 읽기를 끝낸 뒤 Native 상태만 잠근다. `synchronized(state)`는 앞서 읽은 Fleet 응답들의 시점을 일치시키지 못한다.
3. 요청한 SVID에 관계없이 SV23/25/21/58 값을 모두 만든다. AGV 조회도 화물 검증에 영향을 받는다.
4. `carrierValues()`는 차량 containerCode로 소유 차량을 찾는다. 72190에서는 빈 값이므로 owner가 없다.
5. 72191의 isCarry는 1이므로 `carried == 1 && owner.isEmpty()` 조건이 성립한다.
6. `SelectedStatusService.java:146`에서 `IllegalStateException("Carrier snapshots disagree; requery before dispatch")`를 던진다. 이 경로에 실제 재조회는 없다.
7. `NativeHostService.receiveStatus()`는 예외를 잡아 S1F0를 보낸다. 현재 서버 경고는 예외 클래스만 남기고 메시지는 버린다.
8. MCS의 Secs4Net 3.1.0은 Function 0 응답에 공통 예외 문구 `Equipment is not online mode`를 붙인다. MCS는 이 문구를 그대로 표시한다.

따라서 빈 작업 목록을 성공으로 반환하지 않은 방어 동작 자체는 필요하지만, 정상 상태 전환 중의 일시 불일치를 처리하는 재조회와 요청별 의존성 분리가 빠져 있다.

## 배포 JAR로 재현한 결과

JAR SHA256: `21F0480B1170CE48DD5CFAF9B31672BFB8FC749D7A7022B6EDB97E4C0DF8CA44`

- 원본 응답 72190..72197: SV25/58/23 모두 동일한 `Carrier snapshots disagree` 예외 재현.
- 원본 응답 72198..72205: SV25 3건, SV58 10건, SV23 1건 정상.
- 총 6개 원본 재생 검사 통과. 별도 합성 상태 조합 16개도 검사했다.
- 원본 재생은 과거 Native 영속 상태를 복원한 통합 시험이 아니다. 빈 Native 장부와 실제 Fleet 응답을 사용하여 해당 화물 검증 실패를 분리 검증했다.
- 당시 서버의 정확한 예외 메시지는 로그에 남지 않으므로 서버 스택을 직접 확인한 것은 아니다. 실제 응답에서 발견한 불일치와 배포 코드의 재현 결과가 원인 판단 근거다.

## 보완 방향

- 요청한 SVID에 필요한 데이터와 검증만 수행한다. SV25만 요청했을 때 화물 소유권 불일치로 전체 응답이 실패하지 않게 한다.
- 여러 데이터가 필요한 조회는 상태 전환 중 불일치에 대해 전체 시간 제한 안에서 제한적으로 재조회한다. 해결되지 않으면 명확한 조회 실패로 응답한다.
- 빈 작업/빈 재고 성공 응답이나 무조건적인 검증 우회로 해결하지 않는다. 미확정 운송과 미ACK 보고의 보존을 유지한다.
- 서버에 요청 SVID, 조회 식별자, 실패 단계와 안전한 원인 코드를 기록한다.
- MCS는 S1F0를 `상태 조회 중단/일관된 상태 확보 실패`로 구분하고 원문 응답도 표시한다. 실제 연결 단절과 구분한다.

이번 작업은 분석과 읽기 및 로컬 재생만 수행했다. 운영 소스/배포 JAR 수정, 플러그인 재시작, 장비 운전 명령은 수행하지 않았다.

## 로컬 증거

- `tmp/status_error_20261102/host_error_excerpt.log`
- `tmp/status_error_20261102/workflowId-6905.log`
- `tmp/status_error_20261102/record_responses.json` — 인증 헤더 제외.
- `tmp/status_error_20261102/CapturedStatusReplay.java`
- `tmp/status_error_20261102/captured_replay_result.txt`
- `tmp/status_error_20261102/StatusRaceProbe.java`
- `tmp/status_error_20261102/diagnostic_fixture_result.txt`
