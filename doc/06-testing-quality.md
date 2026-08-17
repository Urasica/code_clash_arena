# 테스트·품질 설계

## 역할

테스트 영역은 프론트 표시 정책, Spring context·인증·STOMP·매치 조정, workspace·Docker 명령, Python 게임 규칙과 5개 언어 실행 계약을 계층별로 검증한다.

## 현재 테스트 계층

| 계층 | 위치 | 현재 수 | 주요 보장 |
| --- | --- | --- | --- |
| 프론트 단위/컴포넌트 | `frontend/src/**/*.test.js` | 5 tests | 익명/세션 복원, token localStorage 부재, AI/draw/disconnect 결과 표시 정책 |
| 백엔드 빠른 회귀 | `backend/code/src/test/java` | 62 pass | Flyway/H2 context, DTO·오류·보안 계약, JWT/cookie, Redis Lua 상태·매칭, workspace, 저장 aggregate, correlation context·metric·health |
| 실제 인프라 통합 | `backend/code/src/test/java/.../integration` | 4 tests | MySQL migration·Redis, 인증/AI 전체 흐름, 두 사용자 PvP 동시 제출·disconnect, readiness·Prometheus·correlation header |
| 엔진 규칙 | `engine/tests/test_land_grab.py`, `test_referee.py` | 4 tests | turn timeout, 마지막 점수, 맵 속성, C compiler 분기 |
| Docker 계약 | `engine/tests/test_runners_integration.py` | 3 tests | 5개 언어 compile/run과 player 간·referee 접근 공격 차단 |

엔진 전체 suite는 7개 test이며 Docker image가 없으면 계약 3개는 명시적으로 skip한다. 실제 인프라 백엔드 테스트 4개는 `cca.run.integration=true`일 때만 실행한다.

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

# 실제 MySQL·Redis·Docker 통합
.\mvnw.cmd "-Dcca.run.integration=true" "-Dtest=RealInfrastructureSmokeTest,FullStackAiFlowTest,FullStackPvpFlowTest,ObservabilityIntegrationTest" test

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

## 백엔드 테스트 격리

`application-test.properties`는 H2 memory DB를 MySQL compatibility mode로 사용한다. Flyway H2 V1을 적용한 뒤 Hibernate `validate`를 실행하며 scheduling은 꺼서 matcher가 빠른 테스트에 개입하지 않는다. OAuth2는 test client registration을 사용한다.

빠른 service test는 Redis 연산을 mock한다. 실제 serialization·Lua·동시성은 opt-in 통합 테스트가 Compose의 Redis를 사용해 보완한다. 통합 테스트는 자신이 만든 DB 행과 Redis 키를 종료 시 삭제하며 기존 데이터 전체를 초기화하지 않는다.

## 실제 인프라 통합 범위

- `RealInfrastructureSmokeTest`: MySQL Flyway V1/`validate`, 네 domain table, Redis PING.
- `FullStackAiFlowTest`: HTTP signup/login/me, 동일-origin cookie 요청, Docker init/compile/run, map·2 players·replay 저장, lease/workspace 정리, logout cookie 만료.
- `FullStackPvpFlowTest`: 실제 Redis queue join/cancel/pair, 두 사용자의 동시 submit, engine/DB 정확히 1회, 두 탭 중 마지막 disconnect의 기권 저장, room/user/socket key 정리.
- `ObservabilityIntegrationTest`: 실제 MySQL·Redis·Docker image readiness, Prometheus metric 노출, HTTP correlation ID echo.
- 브라우저 UI와 STOMP wire protocol 자체는 아직 Playwright/Testcontainers release gate가 아니며 M2 `TEST-01` 범위다.

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
| Redis/STOMP/session | backend test + 실제 인프라 통합 4종 |
| log/metric/readiness/alert | backend test + `ObservabilityIntegrationTest` + Prometheus rule 검토 |
| runner/template/referee | engine 전체 + Docker 계약 |
| Land Grab 규칙 | engine 규칙 + Docker 계약 |
| schema/entity/persistence | backend test + `RealInfrastructureSmokeTest` + AI/PvP 통합 |
| compose/설정/문서 | compose config + diff check + 문서 링크 검사 |

## 현재 검증 공백

- 두 실제 브라우저의 STOMP CONNECT/SUBSCRIBE/SEND와 화면 reconnect·replay E2E.
- Google OAuth 실제 공급자 smoke.
- Redis/DB/Docker 장애 주입과 저장 전달 보장(outbox/retry). 현재 readiness와 실패 metric은 감지까지 보장하고 복구 동작은 보장하지 않는다.
- 부하, queue latency, container capacity, 장기 데이터 증가 측정.
- MySQL 8.4와 현재 Flyway 조합은 실제 검증을 통과했지만 Flyway가 공식 지원 경고를 출력하므로 지원 버전 정렬이 필요하다.

후속 작업은 M2의 `TEST-01`, `AUTH-01`, `DATA-02`, `DEP-01`과 이후 `SCALE-01`로 관리한다.

## 품질 기록 위치

- 변경 전 기준선: [`../docs/improvement/verification-baseline.md`](../docs/improvement/verification-baseline.md)
- 2단계 결과: [`../docs/improvement/phase-2-results.md`](../docs/improvement/phase-2-results.md)
- M1 결과: [`../docs/improvement/m1-results.md`](../docs/improvement/m1-results.md)
- 재현·원인·해결: [`../docs/improvement/troubleshooting.md`](../docs/improvement/troubleshooting.md)
- 미완료 항목: [`../roadmap/README.md`](../roadmap/README.md)
