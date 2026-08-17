# M2 — 관측 가능하고 회귀에 강한 운영

- 상태: IN_PROGRESS
- 선행: M1 DONE
- 시작일: 2026-08-17
- 작업 브랜치: `codex/m2-operability`

| ID | 상태 | 작업 | 완료 조건 |
| --- | --- | --- | --- |
| OPS-01 | DONE | 구조화 로그, correlation ID, queue/engine/DB metric, readiness·alert | match ID로 전 구간 추적하고 적체·timeout·cleanup·저장 실패 경보 확인 |
| TEST-01 | READY | Testcontainers, Playwright, failure injection, Windows/Linux CI | PR 빠른 gate와 release 실제 인프라·브라우저·5언어 gate |
| AUTH-01 | READY | 실제 Google OAuth claim·충돌·취소·logout 정책 | 운영 credential smoke와 예측 가능한 오류/계정 연결 |
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

## 다음 작업

`TEST-01`을 시작한다. 빠른 PR gate와 실제 인프라·브라우저 release gate를 분리하고 Testcontainers, Playwright, failure injection, Windows/Linux CI를 단계적으로 추가한다.

M2 완료 시 장애를 재현하지 않고도 상태와 원인을 metric·log·trace에서 찾을 수 있어야 한다.
