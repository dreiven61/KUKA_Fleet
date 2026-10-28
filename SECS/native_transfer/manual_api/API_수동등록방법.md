# Station·Port를 API로 직접 등록하기

Windows PowerShell에서 한 단계씩 실행한다. 아래 등록 명령을 사용자가 실행하기 전에는 Fleet 데이터가 변경되지 않는다. 자동 등록 스크립트나 상시 실행하는 브릿지는 사용하지 않는다.

## 1. 준비된 파일과 등록 범위

폴더: `C:\work\KUKA\SECS\native_transfer\manual_api`

| 파일 | 내용 |
|---|---|
| grpcurl.exe | gRPC API를 호출하는 범용 명령 도구 |
| station_api.pb | 설치본의 API 정의로 만든 Protobuf descriptor |
| get_stations.json | StationData 조회 요청 |
| insert_station_89.json | SECS_ST_89와 하위 포트 PORT_89의 INSERT 요청 |
| insert_station_97.json | SECS_ST_97와 하위 포트 PORT_97의 INSERT 요청 |

이번 등록은 **Station 서비스에 식별자와 수용 공간을 생성하는 단계**다. 이름에 89/97이 들어가더라도 지도 노드가 자동으로 연결되지는 않는다. 지도 StationProperty 연결, PayloadType, 기존 KMP1500i 운송 능력 연계는 별도로 해결해야 한다. 아직 S2F49 운송 명령을 보내는 단계는 아니다.

## 2. 작업 폴더로 이동

```powershell
Set-Location 'C:\work\KUKA\SECS\native_transfer\manual_api'
```

Fleet 서버의 Ubuntu 터미널이 아니라, 파일을 준비한 **현재 Windows PC의 PowerShell**에서 실행한다. sudo나 REST API 토큰은 이 호출에 사용하지 않는다.

## 3. 먼저 현재 Station 목록 조회

```powershell
Get-Content -Raw .\get_stations.json | .\grpcurl.exe -plaintext -max-time 10 -protoset .\station_api.pb -d '@' 192.168.0.45:50110 kuka.dataprovider.v1alpha1.DataProviderService/GetData
```

2026-10-26 실제 조회에서는 다음과 같이 Station 항목이 없었다.

```json
{
  "data": {
    "@type": "type.googleapis.com/kuka.mobilerobotics.station.v1alpha1.StationData"
  }
}
```

등록 시점에는 이 조회를 다시 실행한다. 기존 Station 또는 다른 Station의 compartments에 `SECS_ST_89`, `SECS_ST_97`, `PORT_89`, `PORT_97`이 있으면 먼저 내용을 확인한다. 동일 항목을 다시 INSERT하거나 기존 항목을 덮어쓰지 않는다.

## 4. 89번 요청 파일을 열어서 확인

`insert_station_89.json`을 메모장이나 편집기로 연다. 핵심 구조는 다음과 같다. 실제 파일에는 고유한 action UUID와 작성 시각도 들어 있다.

```json
{
  "action": {
    "uuid": "요청마다 구분하는 고유 UUID - 실제 파일에는 생성된 값이 있음",
    "timestamp": "실제 파일에 UTC 시각이 있음",
    "command": {
      "@type": "type.googleapis.com/kuka.mobilerobotics.station.v1alpha1.ManageStationDataCommand",
      "type": "INSERT",
      "data": {
        "uuid": "SECS_ST_89",
        "name": "SECS test port at node 89",
        "capacity": {
          "compartments": [
            { "uuid": "PORT_89" }
          ]
        }
      }
    }
  }
}
```

- `action.uuid`: 이번 API 요청을 식별하는 값. SECS COMMANDID나 Station UUID와는 별개다.
- `command.type`: `INSERT`는 신규 등록이다.
- `data.uuid`: Station 식별자다.
- `data.name`: 사람이 읽는 표시 이름이다. 지도 연결 기능은 없다.
- `capacity.compartments[].uuid`: Station 하위의 수용 공간 식별자다. 현재 SECS 기본 설정에서 SOURCEPORT/DESTPORT에 사용할 값이다.

다른 곳을 새로 만들 때는 Station UUID와 Port UUID가 중복되지 않도록 정하고, action UUID도 새로 만든다. 통신 오류가 난 요청을 재시도하려고 action UUID부터 바꾸지 않는다. 먼저 조회로 반영 여부를 확인한다.

## 5. 89번 Station·Port 등록

**아래 명령부터 Fleet 데이터를 변경한다.** 지도와 로봇 이동 명령은 포함하지 않는다.

```powershell
Get-Content -Raw .\insert_station_89.json | .\grpcurl.exe -plaintext -max-time 10 -protoset .\station_api.pb -d '@' 192.168.0.45:50110 kuka.actionexecutor.v1alpha1.ActionExecutorService/SubmitAction
```

오류 응답 또는 타임아웃이 나오면 재등록하지 말고 먼저 3번 조회를 실행한다. 오류 코드나 message가 있으면 그 내용을 확인한다. 응답에서 error가 없거나 code가 0이어도, 그것만으로 등록 완료라고 판단하지 않는다.

3번 조회를 다시 실행해 아래 구조가 생겼는지 확인한다. 나머지 필드는 출력에서 생략될 수 있다.

```json
{
  "data": {
    "@type": "type.googleapis.com/kuka.mobilerobotics.station.v1alpha1.StationData",
    "stations": [
      {
        "uuid": "SECS_ST_89",
        "name": "SECS test port at node 89",
        "capacity": {
          "compartments": [
            { "uuid": "PORT_89" }
          ]
        }
      }
    ]
  }
}
```

즉시 보이지 않으면 잠시 후 **조회만** 다시 한다. 접수 응답과 실제 저장은 구분한다.

## 6. 97번 Station·Port 등록

89번의 조회 확인이 끝나면 `insert_station_97.json`을 열어 Station이 `SECS_ST_97`, compartment가 `PORT_97`인지 확인하고 실행한다.

```powershell
Get-Content -Raw .\insert_station_97.json | .\grpcurl.exe -plaintext -max-time 10 -protoset .\station_api.pb -d '@' 192.168.0.45:50110 kuka.actionexecutor.v1alpha1.ActionExecutorService/SubmitAction
```

3번 조회를 다시 실행해 두 Station과 각 하위 Port가 모두 있는지 확인한다. 이 결과가 **Station·Port 등록 완료**의 기준이며, 지도 연결이나 전체 운송 시험 완료를 뜻하지 않는다.

## 도구 출처와 검증

- grpcurl 공식 저장소: https://github.com/fullstorydev/grpcurl
- 버전: v1.9.4, Windows x86_64
- 배포 ZIP SHA256: `412dbe98dc6f3fddfbdc15db061768495d3b4c6ee24de998c83c36f79857680d`
- 공식 릴리스의 checksum과 다운로드 파일을 대조했다.
- `-d '@'`는 PowerShell 파이프로 받은 JSON을 요청 본문으로 사용한다. `-protoset`은 API 정의 파일을 지정한다.
- 실제 Fleet endpoint의 GetActionTypes와 GetData 조회를 검증했다. 두 INSERT 요청은 API 정의에 맞는지 로컬에서 검증하며, 작성 단계에서는 실제 Fleet에 전송하지 않는다.
