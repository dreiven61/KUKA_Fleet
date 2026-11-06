# SECS 플러그인 1.5.3 호스트 명령

배포 파일은 `SECS/fleet_transfer_plugin/build/servostar-mes-transfer-1.5.3.jar`이다. 기존 SECS 포트와 세션 번호를 사용한다. Virtual MCS 2.4.0은 기존 연결 및 보고 수신에 사용하고, 신규 메시지 송신은 운영 MCS 또는 SECS 테스트 호스트에서 수행한다.

| 요청 | 응답과 확인 기준 |
|---|---|
| `S1F15 W` | Header only. `S1F16 B[1] 0`과 `S6F11 CEID 1`. `SV17=3`이며 새 TRANSFER 및 S2F41 제어를 거부한다. HSMS 연결과 조회, 이미 수락한 작업의 보고는 유지한다. |
| `S1F17 W` | Header only. `S1F18 B[1] 0`. OFFLINE에서 전환하면 `S6F11 CEID 3`, `SV17=5`. 같은 모드 재요청은 전환 이벤트를 반복하지 않는다. 저널에 전환이 기록된 뒤에는 재접속과 재기동 시에도 유지된다. 기존 저널의 첫 연결은 호환성을 위해 ONLINE이다. |
| `S5F5 W` | `L[0]`으로 알려진 전체 알람을 조회하거나 `L[n]`에 `U4` ALID를 담아 조회한다. `S5F6`의 각 행은 `L[3] { B[1] ALCD, U4 ALID, A ALTX }`. 설정에 있는 알람과 저널에서 관측한 알람을 ALID별로 반환하며 활성 알람은 ALCD 상위 비트를 설정한다. |
| `S2F41 UPDATE` | `COMMANDID` ASCII, `PRIORTY` 또는 `PRIORITY` U2, 선택 `DESTPORT` ASCII. 배차 대기 상태의 Native SECS 작업만 변경한다. `DESTPORT`는 빈 값 또는 현재 목적지로 지정한다. WCS 재조회로 반영을 확인하면 `CEID212`, 명시적 거부는 `CEID213`을 보낸다. 아직 결과를 확인하지 못한 업데이트는 저널에 남겨 조회만 수행한다. |
| `S2F41 BUZZER` | `PORTID` ASCII. 등록된 포트에 매핑된 PLC 트리거 레지스터에 Modbus TCP FC06을 한 번 쓴다. 응답이 요청과 일치할 때 `HCACK=0`. 미등록 포트는 `6`, 설정이 없거나 PLC가 명시적으로 거부하면 `2`. 쓰기 후 응답을 잃으면 결과 미확인으로 처리하며 플러그인에서 다시 쓰지 않는다. |

SKOJ2 MessageSpec의 UPDATE 표에는 `PRIORTY`로 적혀 있어 두 철자를 허용한다. 우선순위는 SKI의 `99=최고`를 WCS 큐의 `1=최고`로 변환한다. 이 버전의 UPDATE는 **목적지 변경을 거부한다**. 후보 `redirectMission`은 작업 재개와 하차 명령을 함께 호출한다. 일반 운송의 목적지 변경은 승인된 WCS 경로, 새 포트 검증, 변경 후 운송과 보고의 일치 확인이 필요하다.

BUZZER는 서버의 `servostar-secs.json`에 다음과 같은 `portBuzzers` 설정을 추가해야 동작한다. 아래 주소와 레지스터는 설정 형식 예시다. 현장 PLC 담당자가 정한 트리거와 값으로 대체한다.

```json
{
  "portBuzzers": {
    "SECS_ST_89": {
      "nodeCode": "lay01-dist1-89",
      "host": "plc-host.example",
      "port": 502,
      "unitId": 1,
      "register": 256,
      "value": 1
    }
  }
}
```

키는 SV58에서 보고되는 포트 ID이며 `nodeCode`가 현재 Fleet 바인딩과 일치해야 한다. `register`는 Modbus PDU의 0부터 시작하는 주소다. PLC가 트리거를 받아 부저를 울리고 지정 시간 뒤 자체적으로 해제하도록 구성해야 한다. FC06 응답은 레지스터 쓰기 확인이다. 실제 음향 출력은 현장 시험에서 확인한다.

시험은 로컬 HSMS, WCS gRPC 모의 서버, Modbus TCP 모의 PLC로 수행한다. 실제 Fleet의 우선순위 반영과 PLC의 부저 동작은 해당 서버에 적용한 뒤 확인한다.
