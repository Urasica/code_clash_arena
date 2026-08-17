# 테스트·품질 설계

## 역할

테스트 영역은 프론트 표시 정책과 실제 브라우저 흐름, Spring context·인증·STOMP·매치 조정, 격리된 MySQL·Redis와 장애 상태, workspace·Docker 명령, Python 게임 규칙과 5개 언어 실행 계약을 계층별로 검증한다. GitHub Actions는 빠른 교차 OS 회귀와 실제 release 회귀를 분리한다.

## 현재 테스트 계층

| 계층 | 위치 | 현재 수 | 주요 보장 |
| --- | --- | --- | --- |
| 프론트 단위/컴포넌트 | `frontend/src/**/*.test.js` | 5 tests | 익명/세션 복원, token localStorage 부재, AI/draw/disconnect 결과 표시 정책 |
| 프론트 브라우저 | `frontend/e2e` | 1 test | production build, 게스트 인증, 로비·난이도, 맵 생성, Python compile/run, 결과 overlay |
| 백엔드 빠른 회귀 | `backend/code/src/test/java` | 62 pass | Flyway/H2 context, DTO·오류·보안 계약, JWT/cookie, Redis Lua 상태·매칭, workspace, 저장 aggregate, correlation context·metric·health |
| 실제 인프라 통합 | `backend/code/src/test/java/.../integration` | 5 tests | MySQL migration·Redis, 인증/AI 전체 흐름, 두 사용자 PvP 동시 제출·disconnect, readiness·Prometheus·correlation header, DB/Redis 장애·복구 |
| 엔진 규칙 | `engine/tests/test_land_grab.py`, `test_referee.py` | 4 tests | turn timeout, 마지막 점수, 맵 속성, C compiler 분기 |
| Docker 계약 | `engine/tests/test_runners_integration.py` | 3 tests | 5개 언어 compile/run과 player 간·referee 접근 공격 차단 |

엔진 전체 suite는 7개 test이며 Docker image가 없으면 계약 3개는 명시적으로 skip한다. 실제 인프라 백엔드 테스트 5개는 `cca.run.integration=true`일 때만 실행하며 MySQL·Redis·Toxiproxy는 Testcontainers가 격리된 임의 포트로 시작한다.

## 실행 명령

```powershell
# frontend
Set-Location frontend
npm.cmd test -- --watchAll=false
npm.cmd run build

# backend
Set-Location ../backend/code
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package

# 실제 MySQL·Redis·Docker 통합 및 장애 주입
.\mvnw.cmd "-Dcca.run.integration=true" "-Dtest=RealInfrastructureSmokeTest,ObservabilityIntegrationTest,DependencyFailureInjectionTest,FullStackAiFlowTest,FullStackPvpFlowTest" test

# engine
Set-Location ../..
python -m unittest discover -s engine/tests -v

# Docker 계약만 직접 실행
python engine/tests/test_runners_integration.py -v

# 문서/구성 기본 검사
docker compose config
git diff --check
```

macOS/Linux backend는 `./mvnw test`를 사용한다. engine Docker 계약 전에 `docker build -t code-battle-engine engine`이 필요하다.

Playwright는 백엔드와 로컬 인프라가 실행 중이고 engine image가 준비된 상태에서 실행한다. 프런트 production build와 임시 정적 서버는 테스트가 생성·정리한다.

```powershell
# repository root
docker compose up -d --wait
docker build -t code-battle-engine engine

# 별도 터미널: backend/code
.\mvnw.cmd spring-boot:run

# 별도 터미널: frontend
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

## 백엔드 테스트 격리

`application-test.properties`는 H2 memory DB를 MySQL compatibility mode로 사용한다. Flyway H2 V1을 적용한 뒤 Hibernate `validate`를 실행하며 scheduling은 꺼서 matcher가 빠른 테스트에 개입하지 않는다. OAuth2는 test client registration을 사용한다.

빠른 service test는 Redis 연산을 mock한다. 실제 serialization·Lua·동시성은 opt-in 통합 테스트가 Testcontainers Redis를 사용해 보완한다. 통합 JVM은 MySQL 8.4, Redis 7.4, Toxiproxy를 한 번 시작하고 모든 애플리케이션 연결을 proxy로 통과시킨다. local Compose 데이터와 고정 포트를 사용하지 않으며 JVM 종료 시 Ryuk가 container와 network를 정리한다.

## 실제 인프라 통합 범위

- `RealInfrastructureSmokeTest`: MySQL Flyway V1/`validate`, 네 domain table, Redis PING.
- `FullStackAiFlowTest`: HTTP signup/login/me, 동일-origin cookie 요청, Docker init/compile/run, map·2 players·replay 저장, lease/workspace 정리, logout cookie 만료.
- `FullStackPvpFlowTest`: 실제 Redis queue join/cancel/pair, 두 사용자의 동시 submit, engine/DB 정확히 1회, 두 탭 중 마지막 disconnect의 기권 저장, room/user/socket key 정리.
- `ObservabilityIntegrationTest`: 실제 MySQL·Redis·Docker image readiness, Prometheus metric 노출, HTTP correlation ID echo.
- `DependencyFailureInjectionTest`: DB와 Redis 연결을 각각 차단해 readiness 503/DOWN을 확인하고 연결 복원 뒤 200/UP 회복을 확인한다.
- `ai-match.spec.js`: production build에서 게스트 로그인부터 AI 결과 overlay까지 사용자 경로를 검증한다.

## CI 게이트

| workflow | 실행 조건 | 환경 | 범위 |
| --- | --- | --- | --- |
| `PR Gate` | pull request, main push | Ubuntu, Windows | backend 62건, frontend 5건·build, engine 규칙 4건 |
| `Release Gate` | 수동 실행, `v*` tag push | Ubuntu | engine image·7건/5언어, Testcontainers 실제 통합 5건, package, Compose backend, Chromium E2E |

Release 실패 시 backend log, Surefire report, Playwright report·trace·screenshot·video를 artifact로 보존한다. 두 workflow는 repository read 권한만 사용하며 배포나 외부 시스템 변경은 수행하지 않는다.

## Docker 계약 조건

integration test는 다음 조건을 만족할 때만 실행한다.

1. `docker` executable이 PATH에 있다.
2. `code-battle-engine` image inspect가 성공한다.

Python, Java, C, C++, JavaScript의 backend runner template에 최소 strategy를 치환하고 실제 container에서 compile한다. run test는 각 언어가 player process로 참여해 50-turn 결과 JSON을 반환하는지 검증한다.

## 변경별 필수 게이트

| 변경 영역 | 최소 실행 |
| --- | --- |
| frontend page/API/result | frontend tests + build |
| auth/security/controller | backend 전체 test |
| Redis/STOMP/session | backend test + 실제 인프라 통합 5종 |
| log/metric/readiness/alert | backend test + `ObservabilityIntegrationTest` + Prometheus rule 검토 |
| runner/template/referee | engine 전체 + Docker 계약 |
| Land Grab 규칙 | engine 규칙 + Docker 계약 |
| schema/entity/persistence | backend test + `RealInfrastructureSmokeTest` + AI/PvP 통합 + 장애 주입 |
| 인증·로비·AI 화면 흐름 | frontend test + build + Playwright Chromium |
| compose/설정/문서 | compose config + diff check + 문서 링크 검사 |

## 현재 검증 공백

- 두 실제 브라우저의 STOMP CONNECT/SUBSCRIBE/SEND와 화면 reconnect·replay E2E. 현재 Playwright는 AI HTTP 흐름만 보장한다.
- Google OAuth 실제 공급자 smoke.
- Docker daemon/engine timeout 장애 주입과 저장 전달 보장(outbox/retry). DB/Redis readiness의 장애 감지·복구는 보장하지만 실패한 업무 요청의 재시도는 보장하지 않는다.
- 부하, queue latency, container capacity, 장기 데이터 증가 측정.
- MySQL 8.4와 현재 Flyway 조합은 실제 검증을 통과했지만 Flyway가 공식 지원 경고를 출력하므로 지원 버전 정렬이 필요하다.

원격 Windows/Linux와 release workflow의 최초 성공 실행은 M2 `TEST-01`의 남은 검증이다. 이후 작업은 M2의 `AUTH-01`, `DATA-02`, `DEP-01`과 `SCALE-01`로 관리한다.

## 품질 기록 위치

- 변경 전 기준선: [`../docs/improvement/verification-baseline.md`](../docs/improvement/verification-baseline.md)
- 2단계 결과: [`../docs/improvement/phase-2-results.md`](../docs/improvement/phase-2-results.md)
- M1 결과: [`../docs/improvement/m1-results.md`](../docs/improvement/m1-results.md)
- 재현·원인·해결: [`../docs/improvement/troubleshooting.md`](../docs/improvement/troubleshooting.md)
- 미완료 항목: [`../roadmap/README.md`](../roadmap/README.md)
