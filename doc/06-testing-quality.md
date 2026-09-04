# 테스트·품질 설계

## 역할

테스트 영역은 프론트 표시 정책과 실제 브라우저 흐름, Spring context·인증·STOMP·매치 조정, 격리된 MySQL·Redis와 장애 상태, workspace·Docker 명령, Python 게임 규칙과 5개 언어 실행 계약을 계층별로 검증한다. GitHub Actions는 빠른 교차 OS 회귀와 x64/native ARM64 release 회귀를 분리한다.

## 현재 테스트 계층

| 계층 | 위치 | 현재 수 | 주요 보장 |
| --- | --- | --- | --- |
| 프론트 단위/컴포넌트 | `frontend/src/**/*.test.{js,jsx}` | 19 tests | 인증·OAuth, 결과 표시, battle reducer, AI 조합 흐름, arena·matchmaking bounded reconnect/구독 교체, 제출 1회, REST/STOMP·재연결 중 세션 만료 UX |
| 프론트 브라우저 | `frontend/e2e` | 3 tests | production build의 mock AI 화면 조합, 실제 backend guest·AI compile/run, 두 guest의 STOMP PvP 매칭·제출·결과 overlay |
| 백엔드 빠른 회귀 | `backend/code/src/test/java` | 104 pass, 6 opt-in skip | Flyway/H2 V1~V5, DTO·오류·보안 계약, JWT/cookie/logout, Google claim·계정·취소·충돌, Redis Lua 상태·매칭, workspace, 저장 aggregate, 민감 payload 암호화·삭제·batch 정리, production 데이터 설정, correlation context·metric·health |
| 실제 인프라 통합 | `backend/code/src/test/java/.../integration` | 6 tests | MySQL migration·Redis, 인증/AI 전체 흐름, 두 사용자 PvP 동시 제출·disconnect, readiness·Prometheus·correlation header, DB/Redis 장애·복구, 민감 payload 평문 비노출·legacy 전환·감사·삭제 |
| 엔진 규칙 | `engine/tests/test_land_grab.py`, `test_referee.py` | 4 tests | turn timeout, 마지막 점수, 맵 속성, C compiler 분기 |
| Docker 계약 | `engine/tests/test_runners_integration.py` | 4 tests | bind mount 쓰기 없는 init, 5개 언어 compile/run과 player 간·referee 접근 공격 차단 |
| 네트워크 구성 | `infra/oci/network/tests/network.tftest.hcl` | 11 mock runs | subnet·route·NSG·Bastion 정책, 잘못된 입력 거부. 실제 OCI 연결 검증은 아님 |
| 운영 데이터 경계 | `deploy/backend/data/tests` | 7 unit + 1 Docker scenario | secret/ACL·OCI 입력 fail-closed, MySQL 역할 분리, Redis 제한, `age` backup, 별도 restore, Redis 빈 재시작. 실제 OCI 왕복은 아님 |

엔진 전체 suite는 8개 test이며 일반 로컬 실행에서는 Docker image가 없으면 계약 4개를 명시적으로 skip한다. Release Gate는 `CCA_REQUIRE_DOCKER_TESTS=true`를 사용해 image·daemon 누락을 오류로 바꾸므로 8건을 실제 실행하지 않으면 통과하지 않는다. 실제 인프라 백엔드 테스트 6개는 `cca.run.integration=true`일 때만 실행하며 MySQL 8.4·Redis 7.4·multi-arch Toxiproxy 2.12.0은 Testcontainers가 격리된 임의 포트로 시작한다.

## 실행 명령

```powershell
# frontend
Set-Location frontend
npm.cmd test
npm.cmd run build
npm.cmd run audit

# backend
Set-Location ../backend/code
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package

# 실제 MySQL·Redis·Docker 통합 및 장애 주입
.\mvnw.cmd "-Dcca.run.integration=true" "-Dtest=RealInfrastructureSmokeTest,ObservabilityIntegrationTest,DependencyFailureInjectionTest,FullStackAiFlowTest,FullStackPvpFlowTest,SensitiveDataIntegrationTest" test

# DATA-03 단위 검사와 일회용 암호화 backup/restore (age와 Docker 필요)
Set-Location ../..
python -m unittest discover -s deploy/backend/data/tests -p 'test_*.py' -v
python deploy/backend/data/tests/integration_data_stack.py

# engine
Set-Location ../..
python -m unittest discover -s engine/tests -v

# Docker 계약만 직접 실행
python engine/tests/test_runners_integration.py -v

# Release Gate와 같은 strict 실행: Docker 계약 누락·skip은 실패
$env:CCA_REQUIRE_DOCKER_TESTS = 'true'
python -m unittest discover -s engine/tests -v

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

`application-test.properties`는 H2 memory DB를 MySQL compatibility mode로 사용한다. Flyway H2 V1·V2·V3를 적용한 뒤 Hibernate `validate`를 실행하며 scheduling은 꺼서 matcher와 주기적 민감 데이터 정리가 빠른 테스트에 개입하지 않는다. OAuth2는 test client registration을 사용한다.

빠른 service test는 Redis 연산을 mock한다. 실제 serialization·Lua·동시성은 opt-in 통합 테스트가 Testcontainers Redis를 사용해 보완한다. 통합 JVM은 MySQL 8.4, Redis 7.4, Toxiproxy를 한 번 시작하고 모든 애플리케이션 연결을 proxy로 통과시킨다. local Compose 데이터와 고정 포트를 사용하지 않으며 JVM 종료 시 Ryuk가 container와 network를 정리한다.

## 실제 인프라 통합 범위

- `RealInfrastructureSmokeTest`: MySQL Flyway V1·V2·V3/`validate`, 다섯 domain/audit table, OAuth provider identity unique 제약, 민감 데이터 감사 index, Redis PING.
- `FullStackAiFlowTest`: HTTP signup/login/me, 동일-origin cookie 요청, Docker init/compile/run, map·2 players·replay 저장, lease/workspace 정리, logout cookie 만료.
- `FullStackPvpFlowTest`: 실제 Redis queue join/cancel/pair, 두 사용자의 동시 submit, engine/DB 정확히 1회, 두 탭 중 마지막 disconnect의 기권 저장, room/user/socket key 정리.
- `ObservabilityIntegrationTest`: 실제 MySQL·Redis·Docker image readiness, Prometheus metric 노출, HTTP correlation ID echo.
- `DependencyFailureInjectionTest`: DB와 Redis 연결을 각각 차단해 readiness 503/DOWN을 확인하고 연결 복원 뒤 200/UP 회복을 확인한다.
- `SensitiveDataIntegrationTest`: MySQL 원문 비노출, 기존 평문 batch 암호화, 감사되는 복호화, 참가자 요청 삭제를 확인한다.
- `arena-ui.spec.js`: mock API로 production build의 로그인 복원, 로비, editor/status/replay 조합과 결과 overlay를 빠르게 검증한다.
- `ai-match.spec.js`: 실제 backend에서 게스트 로그인부터 AI 결과 overlay까지 사용자 경로를 검증한다.

## CI 게이트

NET-01에서 기존 Ubuntu/Windows `Fast regression` job에 Terraform `fmt/init/validate/test`를 추가했다. DATA-03에서는 두 OS에 secret·ACL·입력 단위 검사를 추가하고 Release Gate에 `age`와 일회용 MySQL·Redis backup/restore 검사를 연결했다. ARM-01은 Release Gate를 `ubuntu-latest` x64와 `ubuntu-24.04-arm` native ARM64 매트릭스로 실행하며 runner·Docker·engine image architecture를 먼저 대조한다. OCI provider와 upload 단위 검사는 mock이며 cloud secret이나 실제 apply/upload를 사용하지 않는다. 로컬 명령과 실제 검증 경계는 [OCI 구성 안내](../infra/oci/README.md)와 [운영 데이터 안내](../deploy/backend/data/README.md)에 있다. native ARM 원격 job은 아직 실행하지 않았다.

| workflow | 실행 조건 | 환경 | 범위 |
| --- | --- | --- | --- |
| `PR Gate` | ready pull request의 생성·갱신·재오픈 | Ubuntu, Windows | Java 21·Node 24, backend 104 pass/통합 6 skip, DATA 단위 7건, frontend 19건·Vite build·전체 의존성 audit, engine 규칙 4건, Terraform mock 11건 |
| `Release Gate` | 수동 실행, `v*` tag push | Ubuntu x64 + native ARM64 | 각 architecture에서 Java 21·Node 24, DATA 단위 7건·일회용 복원 1건, frontend unit/build·audit, engine image·8건/5언어, Testcontainers 실제 통합 6건, package, Compose backend, Chromium AI·STOMP PvP E2E 3건 |

Draft PR은 무거운 regression job을 실행하지 않는다. Ready PR의 최신 커밋만 검사하며 추가 push가 오면 같은 PR의 이전 실행을 취소한다. `main` 병합 후에는 PR Gate를 반복하지 않고, repository ruleset이 Ubuntu·Windows 두 PR check와 최신 base 반영을 병합 전에 강제한다. Release Gate는 실제 인프라·engine·DB·인증 경계 변경에서 PR branch를 대상으로 병합 전에 수동 실행한다.

Release는 architecture별 runner·Docker 정보, frontend와 backend SHA-256, engine image inspect, backend 통합·Playwright 결과를 evidence artifact로 보존한다. 필수 통합 class 누락·skip, 브라우저 3건 미만·skip·flaky, image architecture 불일치는 실패다. 두 workflow는 repository read 권한만 사용하며 배포나 외부 시스템 변경은 수행하지 않는다.

## Docker 계약 조건

integration test는 다음 조건을 만족할 때만 실행한다.

1. `docker` executable이 PATH에 있다.
2. `code-battle-engine` image inspect가 성공한다.

init test는 runner 소유 bind mount에 쓰지 않고 유효한 map JSON을 stdout으로 반환하는지 확인한다. Python, Java, C, C++, JavaScript의 backend runner template에 최소 strategy를 치환하고 실제 container에서 compile한다. run test는 각 언어가 player process로 참여해 50-turn 결과 JSON을 반환하는지 검증한다.

## 변경별 필수 게이트

| 변경 영역 | 최소 실행 |
| --- | --- |
| frontend page/API/result | frontend tests + build |
| auth/security/controller | backend 전체 test |
| Redis/STOMP/session | backend test + 실제 인프라 통합 6종 |
| log/metric/readiness/alert | backend test + `ObservabilityIntegrationTest` + Prometheus rule 검토 |
| runner/template/referee | engine 전체 + Docker 계약 |
| Land Grab 규칙 | engine 규칙 + Docker 계약 |
| schema/entity/persistence | backend test + `RealInfrastructureSmokeTest` + AI/PvP 통합 + 장애 주입 |
| 제출 코드/replay/암호화 키 | backend test + `SensitiveDataIntegrationTest` + 운영 키·이전 키 설정 검토 |
| 인증·로비·AI·PvP 화면 흐름 | frontend test + build + Playwright Chromium AI HTTP 및 STOMP PvP |
| compose/설정/문서 | compose config + diff check + 문서 링크 검사 |
| OCI 네트워크 구성 | Terraform fmt/validate/mock tests + diff/link 검사. 실제 경계 완료에는 별도 승인 환경의 허용/차단 증거 필요 |
| 운영 DB·Redis/backup | DATA 단위 검사 + 일회용 Docker backup/restore + 백엔드 실제 인프라 통합 6종. 완료에는 실제 Private VM 접근 대조군과 호스트 밖 복원 증거 필요 |
| ARM 배포 artifact | native ARM runner architecture + frontend/build + backend jar + engine image architecture/hash + strict engine/infrastructure/browser contracts |

## 현재 검증 공백

- 실제 브라우저의 STOMP reconnect·세션 만료·disconnect 후 화면 복구와 replay E2E. 두 guest의 CONNECT/SUBSCRIBE/SEND와 PvP 결과는 보장하지만 transport 장애 중 재연결은 단위·백엔드 통합 계약으로 남아 있다.
- 실제 Google 공급자 smoke는 수동으로 최초 성공·세션 복원·logout·동일 계정 재사용까지 확인했지만 CI에서는 실제 credential과 사용자 인증을 사용하지 않는다. 자동 gate는 claim·handler·계정 identity 계약까지만 보장한다.
- Docker daemon/engine timeout 장애 주입과 저장 전달 보장(outbox/retry). DB/Redis readiness의 장애 감지·복구는 보장하지만 실패한 업무 요청의 재시도는 보장하지 않는다.
- DATA-03은 로컬 암호화 backup/restore를 보장하지만 실제 Tokyo Object Storage 업로드·다운로드와 Edge/인터넷에서의 DB·Redis 차단은 ARM-01 이후 검증한다.

원격 Windows/Linux PR Gate와 기존 x64 release workflow는 M2 `TEST-01`에서 성공했고 실제 Google 공급자 smoke는 `AUTH-01`에서 완료했다. native ARM release workflow의 실제 원격 성공은 아직 확인하지 않았다. 지원 버전·lockfile·정기 갱신 정책은 `DEP-01`, 민감 데이터 보존·삭제·암호화·감사는 `DATA-02`에서 확정했다. M3 구조 작업 이후의 네트워크·ARM·독립 배포 검증은 [M4](../roadmap/M4-modernization-scale.md)에서 관리한다.

## 품질 기록 위치

- 변경 전 기준선: [`../docs/improvement/verification-baseline.md`](../docs/improvement/verification-baseline.md)
- 2단계 결과: [`../docs/improvement/phase-2-results.md`](../docs/improvement/phase-2-results.md)
- M1 결과: [`../docs/improvement/m1-results.md`](../docs/improvement/m1-results.md)
- 재현·원인·해결: [`../docs/improvement/troubleshooting.md`](../docs/improvement/troubleshooting.md)
- 미완료 항목: [`../roadmap/README.md`](../roadmap/README.md)
