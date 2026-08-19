# Code Clash Arena 개선 작업 트러블슈팅

이 문서는 현상 → 재현 조건 → 원인 → 임시 조치 → 근본 해결 → 검증 결과 순서로 갱신한다. 임시 조치가 성공해도 근본 해결 항목이 완료되기 전에는 해결로 표시하지 않는다.

## TS-001 프론트 설치가 권한 오류로 실패

- 상태: 해결(실행 안내 보완)
- 현상: `npm ci`가 npm cache 파일 `stat`과 `node_modules` 정리 중 `EPERM`으로 실패했다.
- 재현 조건: 제한된 Windows 실행 환경에서 사용자 AppData의 기본 npm cache를 사용한다.
- 원인: 기본 cache가 작업공간 밖에 있고 해당 사용자 프로필의 파일 권한이 현재 실행 계정과 다르다.
- 임시 조치: 승인된 사용자 권한으로 `npm.cmd ci --no-audit --no-fund`를 실행해 설치했다.
- 근본 해결: README의 Windows 명령을 `npm.cmd`로 통일하고 `.env.example` 기반 실행 절차를 추가했다. cache 권한은 저장소가 아닌 실행 계정 정책이므로 CI에서는 전용 cache를 사용한다.
- 검증 결과: 의존성 설치 후 테스트 5건과 production build 성공.

## TS-002 프론트 빌드는 성공하지만 테스트가 Axios에서 실패

- 상태: 해결
- 현상: production build는 성공하지만 Jest는 `Cannot use import statement outside a module`로 suite 로드 단계에서 실패한다.
- 재현 조건: 현재 머신에서 `npm ci` 후 build와 test를 각각 실행한다.
- 원인: 프로젝트가 Axios를 선언하지 않았다. 모듈 해석이 우연히 `C:\Users\dlqnf\node_modules\axios` 1.8.4를 찾아 build는 성공하고, CRA 5 Jest는 그 ESM 파일을 변환하지 못한다.
- 임시 조치: 없음. 상위 폴더 의존을 기준선 성공으로 인정하지 않는다.
- 근본 해결: 호환 가능한 Axios 버전을 `package.json`과 lock에 고정하고, 테스트에서 HTTP 경계를 mock server/adapter로 대체한다.
- 검증 결과: Axios가 `package.json`과 lock에 선언되며 프론트 테스트 5건과 경고 없는 build가 통과한다.

## TS-003 Windows Maven Wrapper가 시작되지 않음

- 상태: 해결
- 현상: `.\mvnw.cmd test`가 `Cannot index into a null array`, `Cannot start maven from wrapper`로 종료된다.
- 재현 조건: 현재 Windows PowerShell에서 Maven Wrapper 3.3.4 batch/PowerShell 스크립트를 실행한다.
- 원인: Wrapper가 일반 디렉터리인 Maven home의 `Target[0]`을 참조하는데 현재 PowerShell에서는 `Target`이 null이다.
- 임시 조치: Git Bash 로그인 셸에서 `./mvnw test`를 실행했다.
- 근본 해결: 검증된 Wrapper 버전으로 재생성하거나 Windows 호환 스크립트로 갱신하고 CI에 Windows Wrapper smoke를 추가한다.
- 검증 결과: `mvnw.cmd test`가 Windows PowerShell에서 정상 시작되고 백엔드 테스트 17건이 통과한다.

## TS-004 백엔드 컨텍스트 테스트가 데이터소스에서 실패

- 상태: 해결
- 현상: `CodeApplicationTests.contextLoads` 1건이 ApplicationContext 로드 오류로 실패한다.
- 재현 조건: 개인 `application.properties` 없이 `./mvnw test`를 실행한다.
- 원인: DataSource URL이 없고 테스트 scope에 내장 데이터베이스도 없어 적합한 driver를 결정하지 못한다.
- 임시 조치: 개인 MySQL 설정을 넣어 테스트를 통과시키지 않는다. 이는 재현 가능한 테스트가 아니다.
- 근본 해결: `test` 프로필에 H2 또는 Testcontainers 기반 DB를 제공하고, Redis/OAuth/Docker 경계를 테스트 종류에 따라 mock 또는 컨테이너로 분리한다.
- 검증 결과: H2 MySQL 호환 테스트 프로필로 외부 MySQL 없이 Spring context와 전체 테스트 17건이 통과한다.

## TS-005 Docker 엔진을 실행할 수 없음

- 상태: 해결
- 현상: AI/PvP Docker 통합 시나리오를 실행할 수 없다.
- 재현 조건: 백엔드가 참조하는 이미지와 README가 안내하는 이미지를 각각 inspect한다.
- 원인: 두 이미지 모두 현재 daemon에 없으며 README는 `code-execution-engine`, 백엔드는 `code-battle-engine`을 사용한다.
- 임시 조치: 없음. 잘못된 이름으로 임의 이미지를 만들지 않았다.
- 근본 해결: 이미지 이름을 설정 하나로 통일하고 개발용 Compose 또는 단일 bootstrap 명령에 build를 포함한다.
- 검증 결과: 이름을 `code-battle-engine`으로 통일하고 이미지를 빌드했다. 5개 언어 Docker 계약 테스트가 모두 50턴 대전을 완료했다.

## TS-006 마지막 턴 점수 불일치

- 상태: 해결
- 현상: 50턴 후 P1이 점령한 타일은 51개인데 최종 점수와 마지막 로그 점수는 50이다.
- 재현 조건: 벽/코인이 없고 매 턴 새 타일을 점령하는 결정적 bot으로 50턴 실행한다. 테스트에서는 코인 respawn을 비활성화해 영역 점수만 비교한다.
- 원인: `land_grab.run`이 각 턴의 이동과 점령 전에 점수를 계산하고, 마지막 행동 뒤 점수를 다시 계산하지 않는다.
- 임시 조치: 없음. UI나 DB에서 점수를 보정하면 규칙 원천이 둘로 나뉜다.
- 근본 해결: 행동과 코인/영역 갱신 후 점수를 계산하는 단일 순서를 정의하고, winner·로그·최종 응답이 같은 값 객체를 사용하게 한다.
- 검증 결과: 마지막 행동 뒤 계산한 점수·로그·winner가 동일함을 회귀 테스트로 확인했다.

## TS-007 맵의 벽 중복과 도달 불가능한 코인

- 상태: 해결
- 현상: 표면상 벽 개수와 실제 고유 벽 개수가 다르고 일부 코인에 도달할 수 없다.
- 재현 조건: 현재 생성기로 1,000개 맵을 만들고 벽 유일성과 `(0,0)`에서 코인까지의 도달성을 검사한다.
- 원인: 벽을 list에 추가할 때 중복을 막지 않고, 유효성 검사는 두 시작점 사이의 연결만 확인한 뒤 코인을 별도로 배치한다.
- 임시 조치: 매칭 스케줄러는 벽이 비어 있지 않은지만 확인하므로 문제를 막지 못한다.
- 근본 해결: set 기반 고유 벽 생성과 도달 가능한 셀 집합 기반 코인 배치, 고정 seed 속성 테스트를 추가한다.
- 검증 결과: set 기반 고유 벽과 도달 가능한 셀 기반 코인 배치를 적용했고 고정 seed 속성 테스트가 통과한다.

## TS-008 격리 환경에서 Maven Central 접근 실패

- 상태: 해결(검증 환경 권한)
- 현상: 기존에 내려받지 않은 Spring Boot parent POM을 가져오는 과정에서 `Permission denied: getsockopt`로 테스트가 중단됐다.
- 재현 조건: 네트워크가 차단된 제한 실행 환경에서 새 Maven 의존성을 처음 해석한다.
- 원인: 프로젝트나 POM 오류가 아니라 제한 환경의 외부 네트워크 정책이다.
- 임시 조치: 같은 명령을 승인된 네트워크 환경에서 실행했다.
- 근본 해결: CI에서 Maven dependency cache를 사용하고, 새 환경 bootstrap 단계에 네트워크 요구 사항을 명시한다.
- 검증 결과: 허용된 환경에서 동일 `mvnw.cmd test`가 의존성을 해석하고 17건을 통과했다.

## TS-009 PowerShell npm 실행 정책 차단

- 상태: 해결
- 현상: `npm test`가 `npm.ps1` 실행 정책 오류로 시작되지 않았다.
- 재현 조건: PowerShell script 실행이 제한된 Windows에서 확장자를 생략해 npm을 호출한다.
- 원인: PowerShell이 `npm.cmd`보다 `npm.ps1`을 먼저 선택한다.
- 임시 조치 및 근본 해결: Windows 문서와 검증 명령에서 `npm.cmd`를 명시한다.
- 검증 결과: 동일 셸에서 `npm.cmd test -- --watchAll=false`와 `npm.cmd run build`가 성공했다.

## TS-010 capability 제거 후 플레이어 격리 초기화·종료 실패

- 상태: 해결
- 현상: private tmpfs를 도입한 첫 Docker 검증에서 모든 언어 compile이 `Operation not permitted`로 실패했고, 소유권 전달 뒤에는 run 종료 시 심판이 플레이어 process에 SIGTERM을 보낼 수 없었다.
- 재현 조건: `--cap-drop ALL` container에서 root 심판이 `/run/players/p1|p2`를 서로 다른 UID로 `chown`하고 해당 UID process를 시작·종료한다.
- 원인: root UID도 제거된 capability를 우회하지 못한다. 또한 디렉터리를 먼저 UID 10001/10002로 넘기면 DAC/FOWNER가 없는 심판이 다시 탐색·chmod할 수 없다.
- 임시 조치: capability 전체 복원이나 player를 root로 되돌리는 조치는 적용하지 않았다.
- 근본 해결: 파일과 하위 디렉터리를 먼저 처리하고 최상위 디렉터리 소유권을 마지막에 넘긴다. container에는 심판 초기화·감독에 필요한 `CHOWN`, `DAC_READ_SEARCH`, `KILL`, `SETUID`, `SETGID`만 추가하고 player UID 전환 뒤 effective capability가 0인지 검증한다.
- 검증 결과: Python·Java·C·C++·JavaScript compile/run이 모두 50턴을 완료했고, player가 상대 source·`/app/referee.py`를 읽거나 PID 1에 signal 권한 검사를 통과하지 못함을 공격 테스트로 확인했다.

## TS-011 실제 PvP 통합 테스트의 첫 queue join 거부

- 상태: 해결
- 현상: 새 MySQL 사용자로 실행한 PvP 통합 테스트의 첫 `joinQueue`가 `User is already assigned to a match`로 종료됐다.
- 재현 조건: DB test row는 삭제됐지만 동일 auto-increment ID를 사용했던 개발 실행의 `user_session:{id}` 또는 `match_reservation:{id}`가 Redis에 남아 있는 환경에서 테스트한다.
- 원인: MySQL 행과 Redis 세션의 수명을 독립적으로 수동 조작한 테스트 환경에서 ID가 재사용됐다. 매칭 Lua는 실제 활성 배정과 구분할 수 없으므로 안전하게 queue 진입을 거부했다.
- 임시 조치: 검사 후 남은 테스트 key를 수동 확인했다.
- 근본 해결: 실제 인프라 테스트가 자신이 생성한 user ID의 queue/session/reservation key만 시작·종료 시 정리하도록 격리했다. 전체 Redis flush나 기존 DB 삭제는 사용하지 않는다.
- 검증 결과: 재실행에서 join/cancel, 두 사용자 match, 동시 submit 1회, 다중 탭 disconnect와 match 관련 key 정리가 모두 통과했다.

## TS-012 Prometheus endpoint가 노출 목록에서 누락

- 상태: 해결
- 현상: actuator가 health와 info만 등록해 `/actuator/prometheus` 요청이 404 경로로 처리되고 공통 예외 handler를 거쳐 500을 반환했다.
- 재현 조건: Prometheus registry 의존성을 추가한 초기 M2 설정으로 `ObservabilityIntegrationTest`를 실행한다.
- 원인: registry가 runtime classpath에 있어도 현재 실행 설정에서는 Prometheus export auto-configuration이 활성화되지 않아 endpoint bean이 만들어지지 않았다.
- 임시 조치: endpoint 테스트를 제외하거나 metric을 애플리케이션 API로 대신 노출하지 않았다.
- 근본 해결: `management.prometheus.metrics.export.enabled=true`를 공유 설정에 명시하고 health, info, prometheus만 management port에 노출했다.
- 검증 결과: 실제 MySQL·Redis·Docker image 환경에서 readiness 200/UP, Prometheus text metric, HTTP correlation header 계약이 함께 통과했다.

## TS-013 Testcontainers Hikari timeout 바인딩 실패

- 상태: 해결
- 현상: Testcontainers는 정상 시작하지만 Spring context가 `spring.datasource.hikari.connection-timeout`을 `long`으로 바인딩하지 못해 실제 통합 5건이 모두 시작 전에 실패했다.
- 재현 조건: `DynamicPropertyRegistry`에서 Hikari timeout을 `"2s"`, `"1s"` 문자열로 등록한다.
- 원인: Spring의 일반 `Duration` 설정과 달리 Hikari bean property는 millisecond `long`을 직접 요구한다.
- 임시 조치: timeout 설정 제거로 장애 테스트를 장시간 대기시키지 않았다.
- 근본 해결: dynamic property를 `2_000L`, `1_000L` millisecond 값으로 등록하고 Redis timeout만 duration 문자열을 유지했다.
- 검증 결과: Compose가 중지된 상태에서 실제 통합 5건이 통과했고 DB·Redis 단절과 복구가 제한 시간 안에 감지됐다.

## TS-014 Playwright 게스트 인증이 localhost 경계에서 거부

- 상태: 해결
- 현상: Playwright가 게스트 버튼을 눌러도 로그인 화면에 머물고 로그아웃 버튼이 나타나지 않았다.
- 재현 조건: frontend를 `http://127.0.0.1:3000`에서 열고 기본 `FRONTEND_URL=http://localhost:3000` backend에 상태 변경 요청을 보낸다.
- 원인: same-origin 검증은 host 문자열까지 정확히 비교하므로 `127.0.0.1` Origin은 `localhost` 허용값과 다르다.
- 임시 조치: 보안 검증을 끄거나 click을 강제하지 않았다.
- 근본 해결: Playwright 기본 URL을 실제 frontend 설정과 같은 `http://localhost:3000`으로 통일하고 배포별 값은 `PLAYWRIGHT_BASE_URL`로 명시하게 했다.
- 검증 결과: guest cookie가 발급되고 session 복원 뒤 로비의 사용자 상태와 로그아웃 버튼이 표시됐다.

## TS-015 개발 서버 오류 overlay가 브라우저 조작 차단

- 상태: 해결
- 현상: CRA 개발 서버의 runtime error iframe이 `GENERATE MAP` 버튼 위에서 pointer event를 가로채 Playwright가 timeout됐다. npm을 거친 web server child도 Windows에서 테스트 종료 뒤 남았다.
- 재현 조건: Playwright `webServer`로 `react-scripts start`를 실행하고 Monaco 화면에 진입한다.
- 원인: release 검증 대상이 아닌 개발 overlay가 화면 최상단을 덮었고 Windows child process tree 종료가 runner 수명과 분리됐다.
- 임시 조치: overlay를 dismiss하거나 `force: true` click으로 우회하지 않았다.
- 근본 해결: global setup이 production build를 생성하고 같은 Playwright runner process에서 최소 정적 서버를 시작하도록 했다. global setup이 반환한 teardown이 서버를 닫는다.
- 검증 결과: Chromium 사용자 흐름 1건이 통과했고 Playwright가 summary 출력 후 exit code 0으로 정상 종료됐다.

## TS-016 Ubuntu Release Gate의 map init이 null 응답을 반환

- 상태: 해결
- 현상: Ubuntu Release Gate에서 AI `/start`는 200을 반환했지만 `walls`가 null이었고, PvP는 `user_session`이 생성되지 않았다.
- 재현 조건: `--cap-drop ALL`과 제한 capability를 적용한 엔진 init이 GitHub runner 소유 bind mount의 `/app/data/map.json`을 직접 생성한다.
- 원인: container root 심판에 host runner 디렉터리 쓰기 권한이 없었다. 엔진은 예외를 exit code 0의 `{error}` JSON으로 바꿨고 백엔드는 필수 map 필드를 검증하지 않아 성공 응답처럼 처리했다.
- 임시 조치: `DAC_OVERRIDE` capability 추가나 host 디렉터리의 world-writable 변경은 적용하지 않았다.
- 근본 해결: init container는 bind mount 없이 map JSON만 stdout으로 반환한다. 백엔드가 오류와 `walls`·`coins`를 검증하고 host 권한으로 `map.json`을 저장한 뒤 lease를 만든다.
- 검증 결과: map host 저장·오류 JSON 단위 테스트, bind mount 쓰기 없는 Docker init, 엔진 전체 8건, 실제 AI/PvP 포함 Testcontainers 5건이 로컬에서 통과했다. 수정 branch의 [Release Gate #32026759619](https://github.com/Urasica/code_clash_arena/actions/runs/32026759619)에서도 실제 인프라와 Chromium 흐름을 포함한 전체 단계가 통과했다.

## TS-017 DB 단절 시 readiness가 socket 응답을 무기한 대기

- 상태: 해결
- 현상: Ubuntu Release Gate의 DB 장애 주입에서 `DataSourceHealthIndicator`가 59.94초 동안 응답하지 않아 테스트 전체 60초 제한에 도달했다.
- 재현 조건: Hikari가 이미 만든 MySQL 연결을 Toxiproxy로 차단한 뒤 readiness를 조회한다.
- 원인: Hikari `connection-timeout`은 pool에서 연결을 얻는 시간만 제한한다. 기존 JDBC 연결의 network read에는 별도 socket timeout이 없어 health query가 계속 대기했다.
- 임시 조치: JUnit 전체 timeout만 늘려 장애를 숨기지 않고 각 outage/recovery 단계의 15초 조건을 유지했다.
- 근본 해결: Hikari pool 획득·검증과 MySQL driver connect/socket timeout을 분리해 설정하고 Redis connect/command timeout도 공통 설정으로 노출했다. 통합 테스트는 더 짧은 driver timeout을 명시한다.
- 검증 결과: 실제 MySQL·Redis 연결 차단에서 readiness 503/DOWN과 복원 후 200/UP을 확인했으며 실제 인프라 5건이 로컬과 수정 branch의 [Release Gate #32026759619](https://github.com/Urasica/code_clash_arena/actions/runs/32026759619)에서 통과했다.

## TS-018 Google OAuth 취소가 예측 가능한 화면으로 복귀하지 않음

- 상태: 해결
- 현상: 사용자가 Google 인증을 취소하거나 callback claim 검증이 실패했을 때 프론트에서 원인을 구분해 안내할 계약이 없었다.
- 재현 조건: 실제 authorization 요청의 `state`를 유지한 채 callback에 `error=access_denied`를 반환하거나 필수 Google claim이 없는 사용자를 성공 handler에 전달한다.
- 원인: 성공 handler만 있었고 OAuth2 실패를 공개 가능한 안정적 코드로 분류하는 handler와 프론트 소비 규칙이 없었다.
- 임시 조치: Spring 기본 오류 화면을 운영 계약으로 간주하거나 예외 상세를 redirect query에 노출하지 않았다.
- 근본 해결: 취소·claim 오류·미검증 email·계정 충돌·기타 실패를 다섯 공개 코드로 제한하고, 실패 시 임시 session을 무효화한 뒤 프론트로 redirect한다. 프론트는 안내를 표시하고 `authError` query를 history에서 즉시 제거한다.
- 검증 결과: failure handler 단위 테스트와 프론트 취소 테스트가 통과했다. 실제 Google callback 취소에서도 `OAUTH_CANCELLED` 안내가 보이고 URL은 `http://localhost:3000/`으로 정리됐으며 로그에는 공개 코드와 예외 종류만 남았다.

## TS-019 V2 migration 추가 후 실제 인프라 테스트가 버전 불일치로 실패

- 상태: 해결
- 현상: OAuth provider identity unique migration이 정상 적용됐지만 `RealInfrastructureSmokeTest`와 schema 검사가 예상 migration 버전 불일치로 실패했다.
- 재현 조건: MySQL/H2에 V1 다음 V2를 적용한 뒤 최신 Flyway 버전을 숫자 `1`로 고정한 기존 assertion을 실행한다.
- 원인: schema 계약 테스트가 migration의 의미 대신 당시 최신 버전 값을 상수로 가정했다.
- 임시 조치: V2 migration을 제거하거나 테스트를 비활성화하지 않았다.
- 근본 해결: 예상 최신 버전을 V2로 갱신하고 MySQL·H2 모두 `users(provider, provider_id)` unique 제약의 실제 존재까지 검증한다.
- 검증 결과: backend 빠른 회귀 77건과 Testcontainers 실제 인프라·장애 주입 5건이 통과했으며 Flyway V1·V2 적용과 schema version 2를 확인했다.

## TS-020 두 탭의 logout·OAuth 요청이 겹치면 재로그인이 일반 실패로 복귀

- 상태: 운영 기준 추가
- 현상: 최초 Google 로그인 성공 뒤 재로그인 smoke 중 `OAUTH_FAILED` 안내가 한 차례 나타났다.
- 재현 조건: 같은 브라우저 session을 공유하는 사용자 탭과 자동 검증 탭에서 logout과 새 OAuth authorization/callback을 거의 동시에 진행한다.
- 원인: 한 탭의 logout 또는 callback 정리가 다른 진행 중인 OAuth 요청의 임시 HTTP session/state를 무효화할 수 있다. 같은 시각에 logout 2건과 OAuth 실패 2건이 서로 다른 correlation ID로 기록돼 겹친 흐름임을 확인했다.
- 임시 조치: 오류 query나 provider 예외 상세를 사용자에게 노출하지 않고 안정적인 `OAUTH_FAILED` 안내를 유지했다.
- 근본 해결: logout·OAuth smoke를 한 탭에서 직렬 실행하고, 운영에서도 다른 탭의 로그인 시도가 겹친 경우 단일 탭에서 재시도하도록 안내한다. 보안을 위해 logout과 callback 뒤의 임시 session 무효화는 완화하지 않는다.
- 검증 결과: 단일 흐름으로 같은 Google 계정에 재로그인하자 성공했고, DB의 Google 사용자 수와 내부 ID가 각각 1건과 17로 유지됐다.

## TS-021 CRA lockfile을 유지한 Vite 설치가 peer dependency 충돌

- 상태: 해결
- 현상: CRA 의존성을 제거하고 Vite/Vitest를 추가한 첫 `npm install`이 이전 `node_modules`와 lockfile의 Jest·Testing Library peer 관계를 계속 해석하다 실패했다.
- 재현 조건: `react-scripts`를 manifest에서 제거했지만 CRA가 만든 `node_modules`와 `package-lock.json`을 둔 상태로 새 build/test 도구를 설치한다.
- 원인: 도구 체인 자체를 교체하는데 이전 해석 결과를 입력으로 재사용해 서로 다른 test 생태계의 peer 제약이 한 트리에 남았다.
- 임시 조치: 생성물인 `frontend/node_modules`만 정확한 경로를 확인한 뒤 제거했다.
- 근본 해결: manifest를 Vite/Vitest direct dependency로 확정하고 기존 lockfile을 새로 생성했다. 이후 설치는 lockfile을 변경하지 않는 `npm ci`만 사용한다.
- 검증 결과: clean `npm ci`가 160 package를 재현했고 전체 audit 0건, 프론트 6건과 production build가 통과했다.

## TS-022 Vite 8이 `.js` JSX를 해석하지 않고 Vitest가 E2E를 수집

- 상태: 해결
- 현상: 첫 Vite build가 `src/index.js`의 JSX에서 실패했고 Vitest는 `frontend/e2e/ai-match.spec.js`까지 읽어 Playwright의 `test()`를 잘못된 runner에서 실행했다.
- 재현 조건: CRA에서 사용하던 `.js` JSX 파일과 전체 기본 test 탐색 범위를 그대로 둔 채 Vite 8/Vitest 4를 실행한다.
- 원인: Vite 8의 변환 경계는 JSX 확장자를 명시하는 현재 도구 규칙을 따르며, 단위 test와 E2E 파일이 같은 frontend tree에 있어 기본 탐색 범위가 겹쳤다.
- 임시 조치: deprecated esbuild loader override로 `.js` 전체를 JSX로 취급하려 했지만 Vite 8의 Oxc/Rolldown 경로에는 적용되지 않아 제거했다.
- 근본 해결: 실제 JSX를 가진 화면·entry·컴포넌트 test를 `.jsx`로 바꾸고 Vitest include를 `src/**/*.test.{js,jsx}`로 제한했다. E2E는 Playwright만 소유한다.
- 검증 결과: Vitest 2 suite/6 test와 Vite production build가 통과하고 E2E spec은 단위 test에서 수집되지 않는다.
