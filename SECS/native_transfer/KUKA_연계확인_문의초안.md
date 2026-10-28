# KUKA.AMR Fleet 2.15.1 / KMP1500i 시뮬레이터 SECS 직접 운송 연계 문의

원하는 구성은 MES/SEComSimulator가 S2F49 TRANSFER를 전송하면 Fleet이 기존 KMP1500i 가상 로봇으로 운송하는 방식입니다. 외부에서 별도로 제작한 SECS→REST 브릿지나 기존 왕복 Workflow 호출 방식은 사용하지 않으려 합니다. Fleet에 설치하는 KUKA 공식 모듈/플러그인은 검토 가능합니다.

환경:

- KUKA.AMR Fleet 2.15.1, STANDARD-API Plugin 2.15.5
- Fleet 웹 New Sims로 생성한 KMP1500i 가상 로봇 3대
- nav-secs 서비스 구동, HSMS Passive/5555, Device ID 3000
- SEComSimulator Host/Active와 S1F13/S1F14 통신 수립 시험 수행
- 기본 명령 매핑: TRANSFER/CANCEL/ABORT → orderengine:50200

확인 사항:

1. 이 버전에서 **New Sims의 KMP1500i**를 기본 SECS TRANSFER/Order Engine으로 운송하는 구성을 공식 지원하는지요? 실제 KMP1500i와 시뮬레이터의 지원 범위가 다른지도 확인 부탁드립니다.
2. 가능하다면 필요한 KUKA 공식 연계 모듈/플러그인, 버전, 라이선스와 설정 예제를 요청드립니다. 기본 nav-secs를 사용하는지, 다른 공식 SECS/GEM 커넥터를 사용하는지도 구분 부탁드립니다.
3. 현재 RobotManager:50090의 RobotData에는 서버 항목만 있고 가상 로봇 1/2/3은 없으며, StationData/PayloadData도 비어 있습니다. 기존 RCS 로봇/화물/작업장을 이 데이터 모델에 연결하는 공식 설정은 무엇인가요? TransportCapacity/BatteryState/FleetData 제공 경로도 요청드립니다.
4. SOURCEPORT/DESTPORT와 기존 lay01/dist1의 지도 노드·작업장 코드 사이의 공식 매핑 방법 및 S2F49 샘플을 요청드립니다. 초기 단방향 시험은 89→97로 계획하고 있습니다.
5. TRANSFER 접수부터 운송 완료까지 S2F50/S6F11 이벤트와 취소/중단 동작을 확인할 수 있는 해당 버전의 SECS 인터페이스 명세 및 시운전 절차를 요청드립니다.

현재 조회 증거:

| 대상 | 결과 |
|---|---|
| orderengine:50200 GetActionTypes | kuka.mobilerobotics.order.v1alpha1.OrderCommand |
| robotmanager:50090 RobotData | UUID 00000000-0000-0000-0000-000000000000의 서버 항목만 존재 |
| krcs-basic-data:7999 GetDataTypes | kuka.mobilerobotics.graph.v1alpha1.GraphData |
| krcs-robot-control:6999 GetDataTypes | No message types are provided by DataProvider. |
| krcs-mission-manager:6689 GetDataTypes | No message types are provided by DataProvider. |
| kwcs-engine:7998 GetActionTypes / GetDataTypes | ActionExecutor has nothing to execute. / No message types are provided by DataProvider. |
| kuka-interface:8076 GetActionTypes / GetDataTypes | ActionExecutor has nothing to execute. / No message types are provided by DataProvider. |

제공 매뉴얼 5.17의 SECS Service/Order Engine 설명은 확인했습니다. 기존 가상 KMP1500i를 유지하는 구체적인 공식 연계 설정이 필요합니다.
