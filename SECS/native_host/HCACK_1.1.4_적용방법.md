# HCACK 오류 구분: 플러그인 1.1.4 / Virtual MCS 2.1.2

작성: 2026-10-29. 소스 수정 및 로컬 검증용 산출물이며 서버 자동 적용·운송 전송은 하지 않는다.

## 현재 거부 원인

PC 로그의 09:22:41, 09:23:11 운송 요청은 각각 COMMANDID `0F901BD5E5BA47B0B81EB49E490C09F3`, `2722F07867AE46F1B2F7A8DEE18155BD`다. 두 요청 모두 SIM_CARGO_001 / PORT_97 → PORT_89이고 S2F50 HCACK=2가 반환됐다.

조회 시 서버 상태는 `tsc=PAUSING`이다. 이전 COMMANDID `30E6733474C44F419675E499C61F6B8E`가 native SECS 저널에 ACTIVE / control=PAUSE / controlState=ACCEPTED로 남아 있다. 반면 Fleet jobQuery에는 workflowId=6874 / status=31 / warnFlag=1 / completeTime 존재, AMR3에는 해당 미션 연결 정보가 없다. 따라서 같은 작업의 실제 PAUSED 상태를 확인할 수 없어 확인 대기가 유지되며 신규 TRANSFER가 차단된다.

HCACK2는 규격상 맞는 응답이다. 기존 PC의 “완료 아님” 문구만으로는 이 사유를 알 수 없었다. **1.1.4는 상세 진단을 추가하며 이 기존 PAUSING 상태를 자동으로 초기화하지 않는다.** 기존 작업을 완료했다고 꾸미거나, 저널을 삭제하거나, 새 COMMANDID로 기존 작업을 우회 재전송하지 않는다. 이 상태는 별도로 실제 작업과 SECS 저널을 대조하여 정리해야 한다.

서버와 PC 시간이 다르므로 날짜 대신 COMMANDID로 대조했다.

## 표준 응답

SKI MessageSpec 3.16 / 3.18, 문서 15–18쪽 기준이다. S2F42와 S2F50은 `L[2](B[1] HCACK, L[n] 오류 파라미터)`를 유지한다.

| HCACK | 의미 | 이번 구현 예 |
|---|---|---|
| 0 | 명령 실행 확인. SKI 운송은 4 사용 | 구 ECS 호환 수신만 지원. 운송 완료로 처리하지 않음 |
| 1 | 지원하지 않는 명령 | 알 수 없는 RCMD |
| 2 | 현재 실행 불가 | AUTO 아님, 목적지 점유, 기존 작업 존재, 상태 조회/인증 실패, Fleet API 명시적 거부 |
| 3 | 파라미터 오류 | 잘못된 SOURCEPORT, 필수 값/타입 오류. CPNAME와 CPACK 표시 |
| 4 | 접수, 결과는 이벤트로 보고 | 실제 Fleet 접수 확인. 운송 완료 아님 |
| 5 | 이미 요청됨 | COMMANDID 중복 또는 제어 처리 중. 기존 작업 결과 확인 필요 |
| 6 | 대상 객체 없음 | 미등록 CARRIERID, 존재하지 않는 제어 COMMANDID |

CPACK1=이름 없음/필수 항목 누락, 2=값 오류, 3=형식 오류다. HCACK != 3이면 오류 파라미터 목록은 비어 있다. 임의의 사용자 정의 HCACK 번호나 가짜 STATE 파라미터를 만들지 않았다. PC는 알 수 없거나 손상된 응답을 성공/확정 거부로 처리하지 않고 확인 필요 상태로 둔다.

실제 submitMission/제어 POST 후 응답 유실은 기존처럼 UNCERTAIN으로 남긴다. 이 경우 실행하지 않았다는 거부 응답을 만들거나 자동 재전송하지 않는다.

## 상세 진단 메시지: 시험용 확장

**CEID9003 / RPTID92는 ServoStar 시험용 확장이다. SKI 표준 이벤트가 아니다.** 기존 9001(화물 조회), 9002(입출고 결과)와 별도로 사용한다.

```
S6F11 W
<L[3]
  <U4[1] DATAID>
  <U2[1] 9003>
  <L[1]
    <L[2]
      <U2[1] 92>
      <L[6]
        <A COMMANDID>
        <A RCMD>
        <B[1] HCACK>
        <A REASON>
        <A TSC>
        <A BLOCKING_ID>
      >
    >
  >
>
```

예: TRANSFER / HCACK2 / TSC_NOT_AUTO / PAUSING / 이전 작업 ID.

- 신규 운송이 시스템 상태에 막히면 해당 요청 COMMANDID와 원인 작업 ID를 함께 보고한다.
- CANCEL·ABORT의 픽업 단계 제한, 기존 제어 대기, Fleet 거부, 화물 사전 검증 오류도 이유를 구분한다.
- PAUSE/RESUME은 시스템 명령이므로 COMMANDID가 빈 문자열이며 관련 작업은 BLOCKING_ID에 표시된다.
- 파라미터 해석 자체가 실패한 요청은 표준 HCACK3/CPACK만으로 오류를 알린다. 통신 미확립 상태의 HCACK2에는 9003을 보장하지 않는다.
- PC는 원문 송수신, 작업 메시지 요약, 운송 거부 작업 이력에 한글 설명·REASON·TSC·관련 ID를 표시한다. 9003은 작업 완료 보고가 아니다.
- 보고는 기존 영속 outbox로 전달하며 S6F12 ACKC6=0을 받는다. 앞선 보고가 BLOCKED이거나 통신이 끊겨 있으면 상세 진단도 대기한다. DATAID를 유지해 재전송하며 운송 명령을 재전송하지 않는다.
- 인증된 기존 `/interfaces/api/mes-secs/status`의 `state.recentRejections`에도 최근 20건을 노출한다. 서버 로그에도 RCMD·COMMANDID·HCACK·REASON·TSC·관련 ID를 기록한다. 토큰과 원본 HTTP 인증 헤더는 기록하지 않는다.

## 사용자가 적용할 순서

1. Virtual MCS를 종료한다. 진행 중인 작업이 있다면 먼저 실제 상태를 확인한다.
2. Fleet → Interface Platform → Plugin Management에 다음 파일을 Import한다.

   `C:\work\KUKA\KUKA_Fleet\SECS\fleet_transfer_plugin\build\servostar-mes-transfer-1.1.4.jar`

3. servostar-mes-transfer 기존 버전을 Disable하고 **1.1.4 하나만 Enable**한다. STANDARD-API는 활성 상태로 둔다. 기존 설정·토큰·저널·5556 매핑은 유지한다.
4. `C:\work\KUKA\KUKA_Fleet\VirtualMCS\실행.cmd` 또는 `dist-2.1.2\KukaVirtualMCS.exe`를 실행한다. **이전 2.1.1 창은 자동으로 새 버전이 되지 않는다.**
5. 기존 `192.168.0.45:5556`, Active, Session3000으로 연결하고 S1F14 버전 1.1.4를 확인한다.
6. 거부 응답은 HCACK 설명을 확인한다. 9003 수신 후에는 작업 메시지의 이유와 관련 ID까지 확인한다. 이번 기존 PAUSING 상태에서는 교체만으로 정상 운송이 재개되지 않는다.

로컬 빌드는 `SECS/fleet_transfer_plugin/build.ps1`, `VirtualMCS/build.ps1`이다. 실제 Fleet 적용 후 메시지 및 동작 확인은 별도다.
