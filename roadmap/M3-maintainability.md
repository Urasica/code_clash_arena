# M3 — 기능 단위 유지보수 구조

- 상태: DONE
- 선행: M2 DONE
- 시작일: 2026-08-28
- 완료일: 2026-08-29
- 작업 브랜치: `codex/m3-be-exec`

| ID | 상태 | 작업 | 완료 조건 |
| --- | --- | --- | --- |
| FE-01 | DONE | `GameArena`를 battle session hook/state machine과 editor/status/result/replay UI로 분리 | page는 조합만 담당하고 시각·기능 회귀 없음 |
| FE-02 | DONE | socket reconnect/backoff, duplicate subscription 방지, session expiry·오류 UX | 일시 단절 후 중복 제출 없이 복구 |
| BE-01 | DONE | `MatchExecutionResult`, outcome/reason enum, persistence mapper | AI/PvP/crash/draw/disconnect 결과 행렬이 UI·DB와 동일 |
| EXEC-01 | DONE | 자원 정책 설정, engine digest/policy version, 보안 corpus | 모든 match에서 engine/policy version 추적 가능 |

## FE-01·FE-02 완료 기록

- 구조: `GameArena`는 session hook과 status/editor/result/replay/mission/error 컴포넌트를 배치한다. 순수 reducer가 AI/PvP phase, timer, connection, notice 전이를 소유하고 hook이 REST·STOMP 수명주기를 조정한다.
- 복구: STOMP는 1초에서 시작해 최대 10초인 지수 backoff와 heartbeat를 사용한다. 재연결마다 match topic 구독을 하나로 교체하고 join만 반복한다.
- 중복 방지: 동기 제출 잠금과 `ready` phase gate를 함께 사용한다. PvP submit 뒤 단절·재연결되어도 코드를 다시 publish하지 않고 결과 구독만 복구한다.
- UX: 일시 단절, 일반 요청 오류, 자동 제출, REST/STOMP 세션 만료를 arena 내부 안내 panel로 표시한다. 세션 만료는 편집·제출을 중단하고 로비 복귀를 제공한다.
- 검증: Vitest 7 suite, 18 tests, Vite production build와 mock API 기반 Playwright Chromium 화면 흐름이 통과했다. reducer 전이, 기존 AI generate/compile/run/result, arena·matchmaking bounded backoff와 중복 구독 교체, reconnect 후 submit 1회, REST·broker·재연결 중 cookie 만료를 자동 검증한다.
- 현재 설계: [`../doc/01-frontend.md`](../doc/01-frontend.md)
- FE-01 완료 커밋: `4778b7b` (`refactor(m3): complete FE-01 battle session split`)
- FE-02 완료 커밋: `a46bee4` (`feat(m3): complete FE-02 socket recovery`)

## BE-01 완료 기록

- 모델: engine wire DTO를 `MatchExecutionResult`로 변환하고 winner, `MatchResultReason`, player별 `MatchOutcome`을 enum으로 고정했다. system error는 played match와 구분해 저장하지 않는다.
- 행렬: score 승패, score draw, 단일 player crash, 양쪽 crash, opponent disconnect를 한 판정 모델에서 계산한다. DB의 `game_match.result_reason`과 `match_player.result`, REST/STOMP reason이 같은 canonical 값을 사용한다.
- 영속화: `MatchPersistenceMapper`가 AI/PvP 공통 aggregate, player outcome·score, 암호화 code/replay를 생성하고 `MatchService`는 조회·멱등 저장만 조정한다. Flyway V4가 기존 행을 `LEGACY`로 구분한다.
- UI: disconnect도 recorded winner를 기준으로 양쪽 승패를 표시하며 crash reason을 DB와 같은 canonical 값으로 표시한다.
- 검증: BE 선택 테스트 19건과 백엔드 전체 102건 중 96 pass/통합 6 skip, 프론트 결과 행렬 4건이 통과했다.
- 현재 설계: [`../doc/05-persistence-operations.md`](../doc/05-persistence-operations.md)
- 구현 커밋: `5499c33` (`refactor(m3): complete BE-01 result model`)
- UI 행렬 보완 커밋: `78ceeee` (`fix(m3): align BE-01 UI outcome matrix`)

## EXEC-01 완료 기록

- 정책: `EnginePolicyProperties`가 CPU, memory, PID, tmpfs, 출력 크기, init/compile/run timeout과 `ENGINE_POLICY_VERSION`을 한 설정 경계에서 binding·범위 검증한다. network none, read-only root, capability allowlist와 no-new-privileges는 완화할 수 없는 고정 경계다.
- 불변 실행: Docker inspect로 repository digest 또는 image ID를 해석하고 tag 대신 digest로 container를 실행한다. 실행 snapshot의 digest와 policy version을 결과에 결합한다.
- 추적: Flyway V5와 `GameMatch`에 `engine_digest`, `engine_policy_version`을 추가했다. AI/PvP 정상 실행은 실행 snapshot을, disconnect는 현재 image snapshot을 사용해 모든 신규 persisted match가 두 값을 갖는다.
- 보안 corpus: 상대·심판 파일 읽기, read-only root 쓰기, capability, referee signal, outbound network, 환경 변수 상속 공격을 실제 player 코드로 실행한다.
- 검증:
  - 백엔드 전체 108건 중 102 pass/실제 인프라 6 skip, backend package 통과.
  - Testcontainers MySQL 8.4·Redis·Toxiproxy와 실제 engine 통합 6건 통과. Flyway V5 migrate/validate, AI/PvP/disconnect digest·policy 저장을 확인했다.
  - Docker engine 8건 통과. 다섯 언어 compile/run과 4종 보안 corpus를 확인했다.
  - 프론트 7 suite·19건과 Vite production build 통과.
- 현재 설계: [`../doc/04-code-execution-engine.md`](../doc/04-code-execution-engine.md), [`../doc/05-persistence-operations.md`](../doc/05-persistence-operations.md)
- 완료 커밋: `fd5ec1e` (`feat(m3): complete EXEC-01 engine policy tracking`)

## 완료 판정

FE-01, FE-02, BE-01, EXEC-01의 구현·독립 테스트·실제 Docker/인프라 검증·현재 설계 문서를 모두 완료해 M3를 종료한다. 다음 작업은 M4의 immutable artifact 공급망과 용량 측정이다.
