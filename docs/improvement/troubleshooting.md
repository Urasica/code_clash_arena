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
