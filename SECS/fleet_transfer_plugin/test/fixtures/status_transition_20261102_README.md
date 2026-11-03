# 픽업 상태 전환의 실제 Fleet 응답

2026-11-02에 Interface Platform Record에서 읽은 workflow 6905의 연속 두 조회 묶음이다.
서버 시계는 2026-10-27 10:15:32로 PC와 다르다. 요청의 인증 헤더는 제외했다.

- 72190..72197: 차량의 liftStatus=1/containerCode="", 화물의 isCarry=1.
- 72198..72205: 차량에도 SIM_CARGO_001 소유권이 반영된 정상 묶음.
- 두 묶음 모두 robotQuery/containerQueryAll/jobQuery 6개 응답이 success=true다.
- `SelectedStatusTest.capturedTransition`은 관련 없는 SVID 조회 분리, 실제 불일치의 제한 재조회와 지속 불일치 거부를 검증한다. 네트워크/장비 명령은 사용하지 않는다.
- 원본 확인 및 구버전 재생 결과: `../../../native_host/STATUS_QUERY_오류원인분석_20261102.md`.
