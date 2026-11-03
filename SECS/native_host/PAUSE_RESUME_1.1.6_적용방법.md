# 1.1.5 이후에도 RESUMING으로 거부되는 경우: 1.1.6

2026-10-29 실제 조회 결과:

- 실행 중 버전은 1.1.5. 새 TRANSFER 거부는 HCACK2 / TSC_NOT_AUTO / RESUMING.
- 차단 작업: `40B92E1C2A5A45E5A4299B94BA5DEE2D`, 인스턴스6875, AMR3.
- `jobQuery`는 COMMANDID·workflowId·완료 상태로 각각 조회해도 `success=true, data=[]`.
- 화물001은 SECS_ST_89에서 하차 상태. AMR3은 105번에서 Idle·비점유·빈 적재.
- AMR JSON은 미할당 `missionCode`, `rcsMissionCode`, `rcsMissionStatus` 필드를 생략함.
- AMR3의 대기·실행·취소 중·경고 등 상태10/20/25/28/50/60 조회 모두 빈 목록.

1.1.5는 완료 작업 행이 여전히 조회되고 미할당 필드가 빈 문자열로 반환되는 경우를 검증했습니다.
실제 서버의 빈 목록과 생략된 nullable 필드까지 처리하지 못했습니다.
빈 목록이 된 내부 원인(삭제/이력 이동 등)은 이 결과만으로 단정하지 않습니다.

## 1.1.6의 완료 검증

빈 jobQuery 자체를 완료로 보지 않습니다. 아래가 **모두** 맞아야 복구합니다.

1. 예약·접수한 정확한 COMMANDID/화물/인스턴스/AMR에 대해 누락 없는 WCS 정상 픽업·이동·하차·TASK_COMPLETED 이력이 보존됨.
2. 실제 화물이 요청 목적지에 있고 지도 안이며 하차 상태.
3. 동일 AMR이 Idle·비점유·빈 적재이며 다른 작업에 연결되지 않음. 미할당 nullable 필드의 생략/null/빈 문자열을 동일하게 처리.
4. 해당 AMR에 상태10/20/25/28/50/60의 Fleet 작업이 없다는 조회가 모두 성공함.

일치하는 현재 작업 행이 있으면 기존 완료 상태30·완료 시각·최종 노드 검증을 유지합니다.
오류 응답·잘못된 행·관측 누락·화물 위치 불일치·진행 중 다른 작업은 보류합니다.
운송·제어 명령을 재전송하거나 상태 파일을 강제 초기화하지 않습니다.

## 적용

1. Virtual MCS에서 추가 전송을 멈추고 종료합니다.
2. Fleet Plugin Management에서 1.1.5 Disable.
3. `SECS/fleet_transfer_plugin/build/servostar-mes-transfer-1.1.6.jar` Import → Enable → STARTED 확인.
4. 기존 MCS **2.1.3**을 실행하고 연결합니다. PC 프로그램 교체는 필요 없습니다.
5. 기존 작업의 CEID210/103 및 누락된 진행·해제·완료 보고를 확인합니다.

포트5556, Session ID3000, 토큰, JSON/JSONL 상태 파일을 유지합니다.
이전 취소 작업 전용 `recover_stale_pause.py`를 다시 실행하지 마세요.
복구 시 `mes-secs/status`의 `controlEvidence`는 `COMPLETED_RETAINED_WCS_RELEASED`로 표시됩니다.

로컬 테스트에서는 빈 완료 작업 목록, nullable 필드 생략, 대기 위치105, 현재 화물 위치, 다른 진행 작업, 조회 실패, 관측 누락을 검증했습니다.
현재 서버를 직접 수정하거나 운송을 실행하지 않았습니다. 교체 후 실제 상태를 다시 확인해야 합니다.
