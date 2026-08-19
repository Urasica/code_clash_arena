# M2 — 관측 가능하고 회귀에 강한 운영

- 상태: IN_PROGRESS
- 선행: M1 DONE
- 시작일: 2026-08-17
- 작업 브랜치: `codex/m2-auth-01`

| ID | 상태 | 작업 | 완료 조건 |
| --- | --- | --- | --- |
| OPS-01 | DONE | 구조화 로그, correlation ID, queue/engine/DB metric, readiness·alert | match ID로 전 구간 추적하고 적체·timeout·cleanup·저장 실패 경보 확인 |
| TEST-01 | DONE | Testcontainers, Playwright, failure injection, Windows/Linux CI | PR 빠른 gate와 release 실제 인프라·브라우저·5언어 gate |
| AUTH-01 | DONE | 실제 Google OAuth claim·충돌·취소·logout 정책 | 운영 credential smoke와 예측 가능한 오류/계정 연결 |
| DATA-02 | READY | 제출 코드·replay 보존, 삭제, 암호화, 감사 | 자동 만료·삭제와 접근 감사, DB 성장 상한 |
| DEP-01 | READY | MySQL·Flyway·JDK·Node 지원 버전 정렬과 의존성 갱신 정책 | 지원 경고 없이 호환 행렬·lockfile·정기 갱신 gate 통과 |

## OPS-01 완료 기록

- 근거: 기존 text log는 요청·worker thread를 연결할 식별자가 없었고 queue 적체, engine timeout, 저장·workspace 정리 실패를 기계적으로 집계할 endpoint가 없었다.
- 구현: HTTP correlation ID 생성·echo, match ID MDC scope와 비동기 전파, Log4j2 JSON, queue/engine/persistence/cleanup metric, 분리된 management port, DB·Redis·engine image readiness, 6개 Prometheus alert를 추가했다.
- 검증:
  - 백엔드 빠른 회귀 62건 통과.
  - 실제 MySQL·Redis·Docker 통합 4건 통과. `ObservabilityIntegrationTest`가 readiness component, Prometheus metric, correlation header를 확인한다.
  - 실제 AI/PvP 로그에서 HTTP `mdc.correlationId`, REST/STOMP·match worker의 `mdc.matchId` 연결을 확인했다.
  - Prometheus `promtool check rules`로 6개 rule 통과.
  - backend package, Compose config, markdown link, `git diff --check` 통과.
- 현재 설계: [`../doc/07-observability.md`](../doc/07-observability.md)
- 트러블슈팅: [`../docs/improvement/troubleshooting.md#ts-012-prometheus-endpoint가-노출-목록에서-누락`](../docs/improvement/troubleshooting.md#ts-012-prometheus-endpoint가-노출-목록에서-누락)
- 완료 커밋: `a325f0a` (`feat(ops): add match observability baseline`)

## TEST-01 완료 기록

- 근거: 실제 인프라 테스트가 개발자의 고정 `localhost:3306/6379` 상태에 의존했고 DB·Redis 단절 시 readiness 복구를 자동 검증하지 않았다. 브라우저 사용자 경로와 Windows/Linux 차이를 막는 자동 gate도 없었다.
- 구현:
  - Testcontainers singleton 환경에 MySQL 8.4, Redis 7.4, Toxiproxy를 구성하고 기존 실제 통합 4종을 임의 포트 환경으로 이전했다.
  - DB·Redis 연결 차단 시 readiness 503/DOWN과 복원 후 200/UP을 검증하는 장애 주입 테스트를 추가했다.
  - Playwright Chromium으로 production frontend의 게스트 로그인 → 로비 → 맵 생성 → Python compile/run → 결과 화면 경로를 추가했다.
  - Ubuntu/Windows `PR Gate`와 Ubuntu `Release Gate` workflow를 추가했다. release는 실제 인프라 5종, 5언어 engine 8건, backend package, Chromium 흐름을 실행하고 실패 증거를 보존한다.
- 로컬 검증:
  - backend 빠른 회귀 64건 통과. opt-in이 아니면 Testcontainers가 시작되지 않는다.
  - Compose가 중지된 상태에서 Testcontainers 실제 통합·장애 주입 5건 통과.
  - frontend 단위 5건, production build, 실제 backend와 Playwright Chromium 1건 통과.
  - engine stdout-only init·규칙·5언어 compile/run·격리 공격 8건 통과.
  - workflow YAML parse와 `git diff --check` 통과.
- 구현 커밋:
  - `93702ab` (`test(backend): isolate release infrastructure tests`)
  - `d12b063` (`test(frontend): add browser release flow`)
  - `1844c7e` (`ci: add cross-platform and release gates`)
  - `8a093f7` (`fix(engine): persist initialized maps on host`)
  - `6562c7f` (`fix(ops): bound dependency health checks`)
- 최초 원격 검증(2026-08-17):
  - [PR #1](https://github.com/Urasica/code_clash_arena/pull/1)을 `main`에 병합했다.
  - [Ubuntu PR Gate](https://github.com/Urasica/code_clash_arena/actions/runs/32024001696/job/95369422117)와 [Windows PR Gate](https://github.com/Urasica/code_clash_arena/actions/runs/32024001696/job/95369422136)는 성공했다.
  - [Release Gate #32024436887](https://github.com/Urasica/code_clash_arena/actions/runs/32024436887)는 실제 인프라 단계에서 실패했다. Ubuntu bind mount에 대한 engine init 쓰기와 DB health socket 무제한 대기가 원인이었다.
- 수정 검증:
  - init을 stdout-only로 바꾸고 host 저장·필수 필드 검증을 추가했다. MySQL·Redis network timeout을 명시했으며 로컬 backend 64건, engine 8건, 실제 인프라 5건이 통과했다.
  - 수정 [PR #2](https://github.com/Urasica/code_clash_arena/pull/2)의 [Ubuntu PR Gate](https://github.com/Urasica/code_clash_arena/actions/runs/32026550525/job/95377043702)와 [Windows PR Gate](https://github.com/Urasica/code_clash_arena/actions/runs/32026550525/job/95377043672)가 성공했다.
  - 병합 전 수정 branch ref로 실행한 [Release Gate #32026759619](https://github.com/Urasica/code_clash_arena/actions/runs/32026759619)가 실제 인프라·장애 복구, Chromium, 5개 언어를 포함한 전체 단계를 3분 14초에 통과했다.
- 현재 설계: [`../doc/06-testing-quality.md`](../doc/06-testing-quality.md)
- 트러블슈팅: [`../docs/improvement/troubleshooting.md#ts-013-testcontainers-hikari-timeout-바인딩-실패`](../docs/improvement/troubleshooting.md#ts-013-testcontainers-hikari-timeout-바인딩-실패)

## AUTH-01 완료 기록

- 실제 Client ID, Client Secret, JWT secret을 저장할 로컬 `.env`를 만들고 Git 추적에서 제외했다. smoke에서는 저장하지 않은 임시 무작위 JWT secret을 프로세스에 주입했으며 `prod` profile은 자리표시자나 32자 미만 secret을 계속 거부한다.
- 전체 공개 템플릿은 루트 `.env.example`, OAuth 빠른 참조 템플릿은 `.envExample`로 제공한다. 두 템플릿에는 실제 credential을 기록하지 않는다.
- Spring Boot 단독 실행은 루트 `.env`를 자동으로 읽지 않으므로 OAuth smoke 전에 값을 셸 환경 또는 IDE 실행 설정으로 불러와야 한다.
- claim 정책: Google `sub`만 외부 identity로 사용하고 필수 `sub`·email·`email_verified=true`를 검증한다. email 기반 local 계정 자동 연결은 하지 않는다.
- 계정 정책: `(provider, providerId)` 조회와 V2 DB unique 제약을 추가하고 `google_`·`guest_` local ID를 예약했다. claim 오류와 계정 충돌은 안정적인 공개 코드로 분류한다.
- 취소·logout 정책: OAuth 취소/실패를 프론트 안내로 redirect하고 노출된 오류 query를 즉시 제거한다. 성공·실패 시 임시 OAuth session을 무효화하며 logout은 JWT cookie와 존재하는 session을 제거한다.
- 자동 검증: backend 빠른 회귀 77건, frontend 6건과 production build, Testcontainers 실제 MySQL·Redis·장애 주입 5건이 통과했다. MySQL V2 migration과 provider identity unique 제약도 실제 DB에서 확인했다.
- 실제 공급자 smoke: 구성된 Google 앱의 authorization endpoint, callback URI, `openid profile email`, state/nonce를 확인했다. 실제 취소 callback은 `OAUTH_CANCELLED` 안내 후 깨끗한 프론트 URL로 복귀했고, guest logout 후 reload에서도 익명 상태를 확인했다.
- 실제 성공·재로그인 smoke: 사용자가 Google 인증/동의를 완료한 뒤 프론트가 `/api/auth/me`로 로그인 상태를 복원했고 DB에는 `provider=GOOGLE` 사용자가 1건 생성됐다. logout 후 같은 Google 계정으로 다시 로그인해도 사용자 수와 내부 ID가 각각 1건과 17로 유지되어 기존 identity를 재사용했다.
- 운영 기준: 실제 운영 JWT secret은 배포 secret store에서 주입한다. 여러 탭에서 logout과 OAuth callback이 겹치면 이전 임시 session/state가 무효화되어 `OAUTH_FAILED`가 될 수 있으므로 단일 흐름으로 재시도한다.
- 완료 커밋: `bdc66d5` (`feat(auth): harden Google OAuth account flow`)

## 다음 작업

AUTH-01 변경을 commit/push하고 PR Gate와 branch ref Release Gate를 통과시킨다. 이후 `DATA-02` 또는 `DEP-01`을 시작한다.

`DEP-01` 입력 기준으로 현재 MySQL 8.4 실행 시 Flyway 공식 지원 경고가 남고, `npm install` 기준 lockfile audit은 55건(낮음 11, 보통 15, 높음 27, 심각 2)을 보고한다. 자동 수정은 동작 변경 가능성이 있어 TEST-01에서 적용하지 않으며 지원 버전 정렬과 함께 별도 검증한다.

M2 완료 시 장애를 재현하지 않고도 상태와 원인을 metric·log·trace에서 찾을 수 있어야 한다.
