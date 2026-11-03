# PortID·VehicleID 통일 — 플러그인 1.5.1 / MCS 2.4.0

**배포 파일 정정: 1.5.1 최종 패키징 오류로 현재 Import 대상은 `build/servostar-mes-transfer-1.5.2.jar`다.** 본문의 식별자 통일 기능을 그대로 포함하며 MCS는 2.4.0을 사용한다. [패키징 수정 안내](PACKAGE_REPAIR_1.5.2_20261103.md).

2026-11-03. MCS의 PORT_89/AMR_AGV002 입력과 SV58의 SECS_ST_89/SV25의 AGV002 보고가 다르던 구조를 수정했다.

## 기준과 흐름

Fleet가 외부 이름을 제공한 포트는 그 이름, 없는 포트는 실제 nodeCode가 PortID다. MCS는 SV58 PortID와 SV25 VehicleID를 그대로 선택/전송하며 로컬 매핑 XML을 읽지 않는다. 서버는 같은 PortID로 실제 Fleet nodeCode를 결정한다. 로봇의 Fleet 내부 정수 ID와 VehicleID 연결은 기존 서버 설정만 사용한다.

| 대상 | 상태 응답 값 | MCS 요청 값 | 서버 내부 대상 | 후속 보고 |
|---|---|---|---|---|
| 89 포트 | SECS_ST_89 | SECS_ST_89 | lay01-dist1-89 | SECS_ST_89 |
| 98 포트 | lay01-dist1-98 | lay01-dist1-98 | lay01-dist1-98 | lay01-dist1-98 |
| 적재 로봇 2 | AGV002 | AGV002 | Fleet RobotID 2 | AGV002 |

CommandID와 CarrierID는 변경하지 않는다. 위치 필드가 차량을 의미하면 VehicleID, 포트를 의미하면 PortID를 사용한다. 회수 SOURCEPORT에 AMR_ 접두어를 붙이지 않는다. 서버의 내부 옛 별칭은 저널/이력 호환 용도로 남지만 새 MCS가 별칭을 만들어 보내지는 않는다.

## 적용 파일

- JAR: `C:\work\KUKA\KUKA_Fleet\SECS\fleet_transfer_plugin\build\servostar-mes-transfer-1.5.1.jar`
- MCS: `C:\work\KUKA\KUKA_Fleet\VirtualMCS\dist-2.4.0\KukaVirtualMCS.exe`
- Visual Studio: 기존 디버깅을 종료하고 App 프로젝트를 다시 빌드/실행한다.

사용자가 새 JAR를 Import/Enable한다. 기존 진행 작업/미확인 보고를 정리한 상태에서 교체하고 MCS 연결을 다시 확립한다. 원본 작업 이력이나 저널은 삭제하지 않는다. 기존 서버 Robot XML은 유지한다. MCS용 XML을 따로 편집하거나 두 파일을 맞출 필요가 없다.

## 화면 확인

1. MCS 접속 후 `서버 식별자 사용 · Port ... / Vehicle ...` 표시를 확인한다. 자동 조회를 껐다면 `AGV·포트 다시 조회`를 누른다.
2. 상태 조회 SV58과 TO/화물 포트, SV25와 회수 FROM의 문자열이 같은지 확인한다. 89는 SECS_ST_89, 회수 로봇 2는 AGV002다.
3. 전송 전 미리보기 SOURCEPORT/DESTPORT가 선택값과 같은지 확인한다. 실제 이동은 현장 조건을 확인한 뒤 사용자가 시작한다.
4. 작업 이력/로그에서 같은 CommandID·CarrierID에 대해 SV23, 차량 TransferPort, CarrierLoc, 최종 CEID207의 출발/도착이 일치하는지 확인한다. 접수 HCACK4만으로 완료를 판단하지 않는다.

## 검증 범위

전체 서버 회귀와 격리된 JAR 로딩이 통과했고, 추가 canonical ID 시험 13개가 요청 정규화·동일 포트 거부·SV58/SV23·차량 보고·CarrierLoc·회수 VehicleID를 확인한다. C# 시험은 보고 ID를 그대로 S2F49에 쓰고 회수 소유자와 완료를 추적한다. Java와 .NET을 실제 로컬 HSMS로 연결해 포트/로봇 이름을 상태 조회에서 얻은 뒤 운송을 한 번만 제출하고 14개 정상 보고 및 완료 이력을 추적했다. 추가 화물/스캔을 포함해 ACK 19건을 확인했다. 이 시험의 Fleet는 로컬 fixture이므로 실제 로봇 이동 검증은 아니다.

실제 서버는 수정 전 1.5.0으로 조회했다. 소스/빌드와 서버 적용 상태를 구분한다. 새 버전은 이 작업 중 자동 Import하거나 실제 운송을 발행하지 않았다.
