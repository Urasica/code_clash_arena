# M2 운영·회귀 품질 완료 결과

- 수행일: 2026-08-17~2026-08-19
- 브랜치: `codex/m2-dep-data`
- 목표: 상태와 원인을 추적할 수 있고, 실제 인프라 회귀·인증·의존성·민감 데이터 수명 정책이 자동 검증되는 운영 기준을 만든다.
- 판정: DONE

## 결과 요약

M2는 관측성, 실제 release 회귀, Google OAuth 계정 정책, 지원 도구체인, 민감 데이터 수명을 순서대로 완료했다. 빠른 PR 검증과 실제 MySQL·Redis·Docker·브라우저 release 검증을 분리하고, 제출 코드와 replay는 암호화·TTL·건수 상한·사용자 삭제 경계를 갖게 됐다.

| 항목 | M2 이전 | M2 완료 |
| --- | --- | --- |
| 장애 추적 | thread별 text log, 요청·match 연결 어려움 | correlation/match ID JSON log, queue/engine/DB/cleanup metric, readiness·alert |
| 실제 회귀 | 개발자 고정 포트와 로컬 상태에 의존 | Testcontainers 임의 포트, DB/Redis 장애·복구, Chromium 사용자 흐름 |
| CI | 단일 환경 중심 | Ubuntu/Windows PR Gate와 실제 인프라 Release Gate 분리 |
| Google OAuth | 선택적 성공 handler 중심 | `sub` identity, claim 검증, 충돌·취소·logout·재로그인 정책 |
| 프론트 도구 | CRA 5, 취약점 55건인 전이 트리 | Vite 8/Vitest 4, clean lockfile 160 package, audit 0건 |
| backend 지원 기준 | MySQL 8.4의 Flyway 지원 경고 | Spring Boot 3.5.16, Java 21, Flyway 11.20.3, JJWT 0.13 |
| 민감 payload | 평문·무기한, 삭제·감사 없음 | AES-256-GCM, code 7일/replay 30일, 최신 1,000 match, 사용자 삭제·감사 |
| DB 성장 | match/replay 무상한 | 민감 payload와 감사 로그에 TTL·건수·batch 상한 |

## 완료 작업

| 작업 | 핵심 결과 |
| --- | --- |
| OPS-01 | 구조화 로그, correlation 전파, metric, management port, readiness, Prometheus alert |
| TEST-01 | Testcontainers·Toxiproxy 장애 주입, Playwright, 교차 OS PR Gate, Release Gate |
| AUTH-01 | Google claim/identity/취소/충돌/logout 정책과 실제 공급자 smoke |
| DEP-01 | 지원 버전 정렬, Vite/Vitest 전환, audit 0 lockfile, Dependabot |
| DATA-02 | V3 schema, 암호화 envelope, legacy 전환, TTL/capacity janitor, 즉시 삭제, 접근 감사 |

## 최종 로컬 검증

| 게이트 | 결과 |
| --- | --- |
| 프론트 단위 | 2 suites, 6 tests PASS |
| 프론트 production build | Vite build PASS |
| 프론트 의존성 | clean `npm ci`, 전체 `npm audit` 0건 |
| 백엔드 빠른 회귀 | 96 discovered, 90 PASS, opt-in integration 6 SKIP |
| DATA 집중 회귀 | 암호화·서비스·janitor·schema·보안·저장 22 tests PASS |
| 백엔드 실제 인프라 | 6 tests PASS: MySQL·Redis·Toxiproxy·engine, 장애 복구, V3·민감 데이터 |
| 엔진 | 8 tests PASS, 5개 언어 compile/run·격리 공격 포함 |
| Flyway | MySQL 8.4 V1→V3 migrate/validate PASS, 지원 경고 없음 |
| 배포 산출물·구성 | Spring Boot executable JAR package, `docker compose config --quiet` PASS |

원격 PR/Release Gate는 사용자 운영 절차에 따라 별도로 확인한다. 이 작업에서는 요청대로 원격 실행 상태를 반복 조회하지 않았다.

## DATA-02 데이터 흐름 변화

```text
As-Is
MatchService → submitted code/replay 평문 → MySQL → 무기한 보존

To-Be
MatchService
  → UTF-8 크기 상한
  → AES-256-GCM(match/player AAD)
  → MySQL envelope
      ├─ 참가자 DELETE 즉시 제거
      ├─ TTL·최신 1,000 match batch 제거
      └─ 감사되는 내부 복호화 경계
```

match 결과·점수·언어·참가자 관계는 민감 payload와 분리해 유지한다. 사용자 삭제는 본인 code와 양쪽 행동이 섞인 공유 replay를 제거하며 다른 참가자의 code는 건드리지 않는다.

## 계획에서 조정한 사항

- DATA-02를 DEP-01 뒤에 같은 브랜치에서 진행하되 구현 커밋을 분리했다. 지원 runtime과 release gate 기준을 먼저 고정해 데이터 변경 검증 환경을 안정화하기 위해서다.
- DB migration은 column/table만 만들고 기존 평문 암호화는 애플리케이션 기동 batch가 담당한다. SQL에 키를 전달하거나 migration history에 비밀을 남기지 않기 위해서다.
- 이미 암호화된 payload의 자동 key 재암호화는 추가하지 않았다. active/previous key 읽기를 지원하고 이전 payload가 TTL로 사라질 때까지 old key를 유지하는 운영 절차를 명시했다.
- 사용자 요청에서 match 전체를 지우지 않고 민감 payload만 삭제한다. 결과 이력 보존과 상대 사용자 code 소유권을 분리하기 위해서다.

## 완료 커밋

| 범위 | 커밋 | 목적 |
| --- | --- | --- |
| DEP-01 구현 | `6fb3a2e` | 지원 도구체인과 lockfile 정렬 |
| DEP-01 문서 | `650ee4b` | 의존성 기준과 갱신 정책 기록 |
| DATA-02 구현 | `57d02e5` | 제출 코드·replay 보호와 수명 정책 |
| DATA-02 테스트 | `6172a1d` | UTF-8 저장 크기 상한 경계 고정 |

현재 역할별 설계는 [`../../doc/README.md`](../../doc/README.md), 이후 남은 작업은 [`../../roadmap/README.md`](../../roadmap/README.md)를 기준으로 한다.
