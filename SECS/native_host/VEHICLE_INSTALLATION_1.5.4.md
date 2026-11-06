# Fleet 차량 설치·제거 SECS 보고

Fleet 플러그인 1.5.4와 Virtual MCS 2.4.1을 사용한다. 기존 HSMS 주소·포트·세션과 차량 이름 XML을 사용한다.

| Fleet 조회에서 확인한 변화 | ACS 호환 보고 |
|---|---|
| 지도에 있던 차량이 `Departure(1)`로 전환 | `S6F11 W / 609 VehicleRemoved` |
| `Departure(1)`였던 차량이 지도에 돌아옴 | `S6F11 W / 608 VehicleInstalled` |
| 기준 목록을 만든 뒤 새 차량이 지도에 나타남 | `S6F11 W / 608 VehicleInstalled` |
| 지도에 있는 차량의 상태가 Idle·Offline·Abnormal 등으로 바뀜 | 차량 설치·제거 보고 없음 |
| API 실패, 잘못된 응답 또는 특정 차량 행 누락 | 마지막 확인 상태를 유지하고 차량 설치·제거 보고 없음 |

플러그인은 `robotQuery`를 2초 간격으로 실행한다. 두 조회 사이에 상태가 바뀌었다가 돌아오면 감지하지 못할 수 있다. 최초 정상 조회는 기준 상태를 기록한다. 1.5.4를 적용하기 전에 Remove한 차량의 과거 `609`는 생성되지 않는다. 이후에는 상태와 미확인 보고를 함께 저장하며, 재기동·재접속 시 같은 전이를 다시 만들지 않는다. ACK를 받지 못한 보고는 기존과 같이 같은 `DATAID`로 보낸다.

`RPTID 5`는 기존 ACS와 같은 `L[4] { VehicleID, CommandID, TransferPortList, CarrierIDList }` 구조다. VehicleID는 SV25와 같은 XML 매핑을 사용하며, 설치·제거 메시지에는 작업 연관성을 추정하지 않고 CommandID A0, 포트·화물 L0를 보낸다. `S1F3 / SV25` 조회는 기존대로 가능하다.

1. 운영 MCS에서 `608/609` 수신을 지원하는지 확인한다. Virtual MCS를 쓰는 경우 2.4.1로 먼저 교체한다. 2.4.0은 해당 `RPTID 5` 보고에 `ACKC6=1`을 보내 대기열을 막을 수 있다.
2. 미완료 작업과 미확인 보고를 확인하고 기존 설정과 저널을 백업한 뒤 `servostar-mes-transfer-1.5.4.jar`를 Import·Enable한다. 1.5.4는 차량 설치 상태를 저널에 추가한다. 이전 버전으로 복귀해야 하면 저널 포맷과 적용 이후 작업·보고를 먼저 대조한다.
3. MCS를 연결하고 `mes-secs/status`의 `state.vehicleInstallations`에 기준 상태가 들어왔는지 확인한다. 이미 제거된 차량은 `false`다.
4. 시험 AMR 한 대를 Fleet 지도에 Insert한다. Virtual MCS의 메시지 로그에서 `608`, 동일한 `VehicleID`, 같은 HSMS `SystemBytes`의 `S6F12 ACKC6=0`을 확인한다.
5. 같은 AMR을 Remove하고 `609`와 `ACKC6=0`을 확인한다. SV25에서 `VehicleState=1`도 확인한다. 이후 같은 상태에서 `609`가 새 `DATAID`로 반복 생성되지 않아야 한다.

로컬 HSMS 시험은 실제 Fleet `robotQuery`의 응답 구조를 모의해 수행한다. 배포 서버와 실제 차량에서의 수신 로그는 별도로 확인한다.
