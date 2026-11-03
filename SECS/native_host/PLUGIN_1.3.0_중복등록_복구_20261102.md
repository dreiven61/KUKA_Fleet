# 플러그인1.3.0 중복 Import 복구

2026-11-02 KST. 실서버192.168.0.45에서 확인·조치했다.

- 증상: 관리 화면은1.3.0 STOPPED이지만 Enable 시 `has started, please refresh`가 나왔다. 새로고침으로도 해결되지 않았다. Native 상태 API는1.3.0/healthy=true이고 실제 S1F3/S1F4는 정상 동작했다.
- 서버에1.3.0 JAR이2개 있었다. `...1130954794713031438.jar`와 `...8616379305762185964.jar`의 SHA256은 둘 다 `21F0480B1170CE48DD5CFAF9B31672BFB8FC749D7A7022B6EDB97E4C0DF8CA44`였다. 코드 차이·손상이 아니라 동일 Plugin-Id/버전의 중복 파일과 등록/실행 상태 불일치였다.
- 서버 이미지의 관리 프로그램을 읽기 전용으로 확인했다. 시작 처리는 이미 STARTED인 동일 Plugin-Id를 다시 시작하지 않는다. 정지 관리 API는 실제 실행본을 unload하고 등록 상태를 갱신한다. 삭제는 같은 Plugin-Id/버전의 저장소 JAR들을 정리한다. 사용자 정의 플러그인 소스 수정으로 해결할 문제가 아니었다.

## 조치

1. 미완료 Native 작업0·미확인 보고0·알람0을 확인하고 상태를 저장했다. 기존 작업은33건이었다.
2. 두1.3.0 JAR을 로컬에 별도로 백업하고 동일 해시를 검증했다.
3. 기존 관리 인증 세션의 정상 stop API로 실제 실행본을 정지했다. 화면에 정지 버튼이 없어서 일시적인 XHR 진단으로 시작 요청1건을 동일 Plugin-Id의 정지 URL로 변경해 전달했다. 인증 정보는 추출하거나 바꾸지 않았다. 진단 패턴은 해제했고 브라우저를 새로고침했다.
4. 관리 기능으로1.3.0 등록과 중복 JAR2개를 제거했다. 작업/보고 저널은 삭제·초기화하지 않았다.
5. 검증된 원래 빌드의 동일 JAR을 한 번만 Import하고 Enable했다.

현재 서버 파일은 `servostar-mes-transfer-1.3.0-sbp-7758139720937776810.jar` 하나이며 등록ID23이다. 해시는 기존과 같다. 구버전1.1.6/1.2.0/1.2.1의 STOPPED 등록과 STANDARD-API는 변경하지 않았다. Fleet 전체 컨테이너나DB를 재기동하지 않았다.

## 확인 결과

- 새로고침 후 관리 화면도1.3.0 **STARTED / Disable**로 표시된다.
- Native 상태 API1.3.0/healthy=true. 기존 작업33건의 공개 상태 필드는 정리 전과 동일하다. 미완료 작업0·보고 대기0·활성 알람0이다.
- 별도 HSMS 읽기 전용 조회3회 모두S1F4,172/110/140ms였다.
- 실제 Virtual MCS2.3.0에서 연결하고 접속 자동 조회3건을 확인했다. AGV3대131.7ms / 관리 포트10개244.8ms / 현재 작업0건376.4ms, 모두정상이다. 최종communicating=true이며 MCS 접속을 유지했다.
- 이번 복구에서는 운송·리프트·취소 명령을 발행하지 않았다.

## 재발 방지

같은1.3.0을 실행 중인 상태에서 다시 Import하지 않는다. 교체할 때는 진행 작업·미확인 보고를 정리하고 기존 플러그인을 Disable한 뒤, 백업을 확인하고 기존 등록을 제거한 다음 새 JAR을 한 번만 Import→Enable한다. 코드가 달라졌으면 버전을 올려 구분한다. 작업/보고 저널은 교체 대상이 아니다.

## 증거

- [복구 전 상태](../../tmp/plugin_state_20261102/before_status.json)
- [복구 직후 상태](../../tmp/plugin_state_20261102/after_status.json)
- [MCS 연결 후 상태](../../tmp/plugin_state_20261102/final_status.json)
- [MCS 자동 조회 결과](../../tmp/plugin_state_20261102/mcs_status_queries.txt)
- [정상 관리 화면](../../tmp/plugin_state_20261102/plugin_1.3.0_repaired.jpg)
- 백업: `tmp/plugin_state_20261102/backup_running_1.3.0.jar`, `backup_imported_1.3.0.jar`.
