# Station / 포트 등록 방법

이 절차는 Fleet의 Station 서비스에 두 Station과 그 하위 compartment(포트)를 등록한다. Fleet이 제공하는 `ManageStationDataCommand`의 INSERT를 호출하는 **일회성 설정 도구**다. 상시 실행하는 SECS→REST 브릿지가 아니며, 실행 후 창을 닫아도 된다.

등록 대상:

| Station UUID | compartment UUID(=SECS 포트 코드) |
|---|---|
| SECS_ST_89 | PORT_89 |
| SECS_ST_97 | PORT_97 |

## 실행

현재 Windows PC에서 순서대로 실행한다. 별도의 YAML 편집이나 API 토큰 입력은 필요하지 않다. 현재 PC에 이미 있는 Python/grpc 의존성을 사용한다.

1. `C:\work\KUKA\SECS\native_transfer\Station_01_등록전확인.cmd`를 더블클릭한다. 조회만 수행한다. 두 항목에 INSERT_REQUIRED가 나오면 아직 등록되지 않은 상태다. ALREADY_PRESENT라면 동일한 내용이 이미 등록돼 있다. 오류가 나오면 등록을 진행하기 전에 원인을 확인한다.
2. `C:\work\KUKA\SECS\native_transfer\Station_02_등록실행.cmd`를 더블클릭한다. 이 파일은 실제로 위 두 Station과 포트를 등록한다.
3. 각각 REGISTERED_AND_VERIFIED가 나오고 마지막에 BOTH_STATIONS_VERIFIED가 나오는지 확인한다. 접수 응답만 보지 않고 StationData를 다시 읽어 실제 데이터와 대조한다.
4. 첫 번째 조회 파일을 다시 실행하면 두 항목 모두 ALREADY_PRESENT가 나와야 한다.

```text
REGISTERED_AND_VERIFIED SECS_ST_89 PORT_89
REGISTERED_AND_VERIFIED SECS_ST_97 PORT_97
BOTH_STATIONS_VERIFIED
```

## 보존·오류 처리

기존 Station UUID나 다른 Station의 포트 UUID와 충돌하면 덮어쓰지 않고 중단한다. 기존 내용과 완전히 동일한 항목은 다시 등록하지 않는다. 통신이 끊겨 접수 여부가 불명확하거나 재조회로 확인되지 않으면 자동 재전송하지 않는다. 결과는 `station_registration\runs\registration.json`에 보관하고, 최초 StationData의 원본 Protobuf도 base64로 보관한다.

등록 프로그램을 두 개 동시에 실행하지 않는다. 프로세스 잠금으로 같은 도구의 중복 실행을 막는다. 오류 후 journal을 삭제해 재전송하지 말고 조회 결과와 오류를 먼저 확인한다.

## 이 등록의 범위

Station 서비스에 이 데이터가 생기는 것과 지도 노드 89/97에 연결되는 것은 별도다. 도구는 지도, 로봇, PayloadType 또는 운송 주문을 변경하지 않는다. 현재 KMP1500i 시뮬레이터의 RobotData/운송 능력 연계와 지도 StationProperty 저장 경로는 아직 해결되지 않았다. BOTH_STATIONS_VERIFIED가 나와도 전체 TRANSFER 운송 준비 완료를 뜻하지 않는다.

2026-10-26 확인: 실제 `192.168.0.45:50110`이 등록용 Action 타입과 StationData 타입을 제공함을 확인했고, 등록 전 조회에서 두 항목 모두 INSERT_REQUIRED였다. 실제 등록은 사용자가 위 등록 실행 파일을 실행할 때 수행한다.
