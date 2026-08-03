# Code Clash Arena 개선 작업 트러블슈팅

이 문서는 현상 → 재현 조건 → 원인 → 임시 조치 → 근본 해결 → 검증 결과 순서로 갱신한다. 임시 조치가 성공해도 근본 해결 항목이 완료되기 전에는 해결로 표시하지 않는다.

## TS-001 프론트 설치가 권한 오류로 실패

- 상태: 환경 우회 완료, 저장소 개선은 2단계 예정
- 현상: `npm ci`가 npm cache 파일 `stat`과 `node_modules` 정리 중 `EPERM`으로 실패했다.
- 재현 조건: 제한된 Windows 실행 환경에서 사용자 AppData의 기본 npm cache를 사용한다.
- 원인: 기본 cache가 작업공간 밖에 있고 해당 사용자 프로필의 파일 권한이 현재 실행 계정과 다르다.
- 임시 조치: 승인된 사용자 권한으로 `npm.cmd ci --no-audit --no-fund`를 실행해 설치했다.
- 근본 해결: 저장소 문서/CI에서는 작업공간 또는 CI 전용 cache를 사용하고, PowerShell에서는 실행 정책 영향을 받는 `npm.ps1` 대신 지원되는 명령 경로를 안내한다.
- 검증 결과: 1,338개 패키지 설치 성공.

## TS-002 프론트 빌드는 성공하지만 테스트가 Axios에서 실패

- 상태: 미해결(P0)
- 현상: production build는 성공하지만 Jest는 `Cannot use import statement outside a module`로 suite 로드 단계에서 실패한다.
- 재현 조건: 현재 머신에서 `npm ci` 후 build와 test를 각각 실행한다.
- 원인: 프로젝트가 Axios를 선언하지 않았다. 모듈 해석이 우연히 `C:\Users\dlqnf\node_modules\axios` 1.8.4를 찾아 build는 성공하고, CRA 5 Jest는 그 ESM 파일을 변환하지 못한다.
- 임시 조치: 없음. 상위 폴더 의존을 기준선 성공으로 인정하지 않는다.
- 근본 해결: 호환 가능한 Axios 버전을 `package.json`과 lock에 고정하고, 테스트에서 HTTP 경계를 mock server/adapter로 대체한다.
- 검증 결과: `npm ls axios --depth=0`은 `(empty)`, `require.resolve`는 저장소 밖 경로를 반환했다.

## TS-003 Windows Maven Wrapper가 시작되지 않음

- 상태: 대체 경로 확인, 근본 해결 미완료(P0)
- 현상: `.\mvnw.cmd test`가 `Cannot index into a null array`, `Cannot start maven from wrapper`로 종료된다.
- 재현 조건: 현재 Windows PowerShell에서 Maven Wrapper 3.3.4 batch/PowerShell 스크립트를 실행한다.
- 원인: Wrapper가 일반 디렉터리인 Maven home의 `Target[0]`을 참조하는데 현재 PowerShell에서는 `Target`이 null이다.
- 임시 조치: Git Bash 로그인 셸에서 `./mvnw test`를 실행했다.
- 근본 해결: 검증된 Wrapper 버전으로 재생성하거나 Windows 호환 스크립트로 갱신하고 CI에 Windows Wrapper smoke를 추가한다.
- 검증 결과: 대체 경로에서 Maven 3.9.11이 시작되어 Java 소스 32개 컴파일까지 성공했다.

## TS-004 백엔드 컨텍스트 테스트가 데이터소스에서 실패

- 상태: 미해결(P0)
- 현상: `CodeApplicationTests.contextLoads` 1건이 ApplicationContext 로드 오류로 실패한다.
- 재현 조건: 개인 `application.properties` 없이 `./mvnw test`를 실행한다.
- 원인: DataSource URL이 없고 테스트 scope에 내장 데이터베이스도 없어 적합한 driver를 결정하지 못한다.
- 임시 조치: 개인 MySQL 설정을 넣어 테스트를 통과시키지 않는다. 이는 재현 가능한 테스트가 아니다.
- 근본 해결: `test` 프로필에 H2 또는 Testcontainers 기반 DB를 제공하고, Redis/OAuth/Docker 경계를 테스트 종류에 따라 mock 또는 컨테이너로 분리한다.
- 검증 결과: Surefire `Tests run: 1, Errors: 1`; 근본 원인은 `Failed to determine a suitable driver class`.

## TS-005 Docker 엔진을 실행할 수 없음

- 상태: 미해결(P0)
- 현상: AI/PvP Docker 통합 시나리오를 실행할 수 없다.
- 재현 조건: 백엔드가 참조하는 이미지와 README가 안내하는 이미지를 각각 inspect한다.
- 원인: 두 이미지 모두 현재 daemon에 없으며 README는 `code-execution-engine`, 백엔드는 `code-battle-engine`을 사용한다.
- 임시 조치: 없음. 잘못된 이름으로 임의 이미지를 만들지 않았다.
- 근본 해결: 이미지 이름을 설정 하나로 통일하고 개발용 Compose 또는 단일 bootstrap 명령에 build를 포함한다.
- 검증 결과: 두 이미지 모두 `No such image`.

## TS-006 마지막 턴 점수 불일치

- 상태: 재현 완료, 수정은 2단계(P0)
- 현상: 50턴 후 P1이 점령한 타일은 51개인데 최종 점수와 마지막 로그 점수는 50이다.
- 재현 조건: 벽/코인이 없고 매 턴 새 타일을 점령하는 결정적 bot으로 50턴 실행한다. 테스트에서는 코인 respawn을 비활성화해 영역 점수만 비교한다.
- 원인: `land_grab.run`이 각 턴의 이동과 점령 전에 점수를 계산하고, 마지막 행동 뒤 점수를 다시 계산하지 않는다.
- 임시 조치: 없음. UI나 DB에서 점수를 보정하면 규칙 원천이 둘로 나뉜다.
- 근본 해결: 행동과 코인/영역 갱신 후 점수를 계산하는 단일 순서를 정의하고, winner·로그·최종 응답이 같은 값 객체를 사용하게 한다.
- 검증 결과: `reported_score=50`, `last_board_area=51`, `turns=50`으로 안정적으로 재현.

## TS-007 맵의 벽 중복과 도달 불가능한 코인

- 상태: 재현 완료, 수정은 2단계(P1)
- 현상: 표면상 벽 개수와 실제 고유 벽 개수가 다르고 일부 코인에 도달할 수 없다.
- 재현 조건: 현재 생성기로 1,000개 맵을 만들고 벽 유일성과 `(0,0)`에서 코인까지의 도달성을 검사한다.
- 원인: 벽을 list에 추가할 때 중복을 막지 않고, 유효성 검사는 두 시작점 사이의 연결만 확인한 뒤 코인을 별도로 배치한다.
- 임시 조치: 매칭 스케줄러는 벽이 비어 있지 않은지만 확인하므로 문제를 막지 못한다.
- 근본 해결: set 기반 고유 벽 생성과 도달 가능한 셀 집합 기반 코인 배치, 고정 seed 속성 테스트를 추가한다.
- 검증 결과: 1,000개 중 중복 벽 990개, 도달 불가능한 코인 포함 맵 32개.
