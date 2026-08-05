# M1 배포 신뢰 경계 완료 결과

- 수행일: 2026-08-04
- 브랜치: `codex/stage-2-improvements`
- 목표: 실제 MySQL·Redis·Docker에서 인증, 매칭, 제출, 실행, 저장, 종료 경계를 일관되게 만든다.
- 판정: DONE

## 결과 요약

M1은 API 계약, AI workspace 수명, PvP 상태·동시성, player 격리, cookie 인증 보안, versioned schema, 실제 인프라 E2E를 순서대로 완료했다. 빠른 회귀와 실제 인프라 검증을 분리해 일반 개발에서는 H2/mock 기반 gate를 유지하고, release 검증에서는 Compose MySQL·Redis와 실제 engine image를 사용한다.

| 항목 | M1 이전 | M1 완료 |
| --- | --- | --- |
| API 결과 | controller·engine raw map 혼재 | REST/STOMP/engine DTO와 고정 오류 code |
| AI workspace | owner 없음, 미실행 폴더 잔존 가능 | owner/status/TTL lease와 janitor, 종료 시 제거 |
| PvP 실행 | inbound thread에서 동기 실행 | bounded worker와 명시적 상태 전이 |
| 매칭 | scheduler 간 pair 경쟁 가능 | Redis Lua pair reservation·room 생성·복귀 |
| disconnect | 탭 하나 종료가 기권 가능 | 사용자별 socket set과 reconnect grace |
| player 격리 | player/referee가 root 경계 공유 | UID 10001/10002, private tmpfs, 최소 capability |
| cookie 보안 | CSRF·rate limit·운영 설정 검증 없음 | same-origin, Redis rate limit, `prod` fail-fast |
| DB schema | `ddl-auto=update`, map 미저장 | Flyway V1, `validate`, map aggregate, UUID 멱등성 |
| 실제 인프라 회귀 | 없음 | MySQL·Redis smoke, AI HTTP E2E, 두 사용자 PvP E2E |

## 완료 커밋

| 범위 | 커밋 | 목적 |
| --- | --- | --- |
| 마일스톤 전환 | `25fe76c` | 남은 작업을 M1~M4 완료 조건으로 재구성 |
| API-01 | `aab28d6` | 안정적인 battle API 계약 |
| EXEC-02 | `7db366f` | AI workspace lease와 정리 |
| MATCH-01~03 | `6195a1b` | 비동기·원자 매칭·다중 소켓 상태 |
| EXEC-03 | `1f11141` | player 실행 identity 격리 |
| SEC-01 | `f1e6e38` | cookie 인증 신뢰 경계 강화 |
| DATA-01 | `34f6f5b` | Flyway schema와 결과 aggregate 일관성 |
| REL-01 | `5668cbe` | 실제 인프라 AI/PvP release 회귀 |

## 최종 검증

| 게이트 | 결과 |
| --- | --- |
| 프론트 테스트 | 2 suites, 5 tests PASS |
| 프론트 production build | PASS, gzip main bundle 100.79 kB |
| 백엔드 빠른 회귀 | 60 discovered, 57 PASS, opt-in 3 SKIP |
| 백엔드 실제 인프라 | 3 tests PASS |
| 백엔드 배포 JAR | `code-0.0.1-SNAPSHOT.jar` package PASS |
| 엔진 | 7 tests PASS: 규칙, 5개 언어 compile/run, 격리 공격 |
| Compose | MySQL 8.4, Redis 7.4 healthy |
| Flyway | 빈 MySQL V1 및 legacy schema baseline 0→V1 PASS |
| 종료 정리 | 검사 대상 match/user/socket/lease key와 workspace 잔존 0 |

실제 AI E2E는 signup → login → me → start → compile → run → map/player/replay 확인 → lease/workspace 정리 → logout을 수행했다. PvP E2E는 join/cancel, 두 사용자 pair, 동시 제출, engine/DB 정확히 1회, 두 탭 중 마지막 disconnect 이후 기권 저장과 Redis 정리를 수행했다.

## 계획에서 조정한 사항

- DB migration은 운영 MySQL과 빠른 H2 검증의 타입 차이를 피하기 위해 vendor별 V1로 나눴다.
- 기존 Hibernate schema는 Flyway baseline version 0에서 V1을 실행해 null map/code/language를 legacy 값으로 보정한다.
- 브라우저/STOMP wire E2E 대신 M1에서는 실제 service·Redis·MySQL·Docker를 묶은 두 사용자 테스트를 자동화했다. 실제 브라우저 reconnect와 subscription은 M2 `TEST-01`로 남겼다.
- Redis 장애 시 rate limit을 우회하지 않고 보호 endpoint를 `503`으로 닫는다.

## M2로 넘긴 작업

- Testcontainers/Playwright 기반 브라우저·STOMP release gate와 Redis/DB/Docker failure injection.
- 구조화 로그, correlation ID, queue/engine/DB metric, readiness와 alert.
- 실제 Google OAuth 공급자 smoke와 계정 충돌 정책.
- 제출 코드·replay 보존/삭제/감사 및 장기 DB 성장 상한.
- MySQL 8.4에서 출력되는 Flyway 지원 경고를 없애기 위한 지원 버전·의존성 정렬.

세부 목표와 완료 조건은 [`../../roadmap/M2-operability-quality.md`](../../roadmap/M2-operability-quality.md)를 기준으로 한다.
