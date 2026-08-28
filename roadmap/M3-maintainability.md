# M3 — 기능 단위 유지보수 구조

- 상태: IN_PROGRESS
- 선행: M2 DONE
- 시작일: 2026-08-28
- 작업 브랜치: `codex/m3-fe-battle-session`

| ID | 상태 | 작업 | 완료 조건 |
| --- | --- | --- | --- |
| FE-01 | DONE | `GameArena`를 battle session hook/state machine과 editor/status/result/replay UI로 분리 | page는 조합만 담당하고 시각·기능 회귀 없음 |
| FE-02 | DONE | socket reconnect/backoff, duplicate subscription 방지, session expiry·오류 UX | 일시 단절 후 중복 제출 없이 복구 |
| BE-01 | READY | `MatchExecutionResult`, outcome/reason enum, persistence mapper | AI/PvP/crash/draw/disconnect 결과 행렬이 UI·DB와 동일 |
| EXEC-01 | READY | 자원 정책 설정, engine digest/policy version, 보안 corpus | 모든 match에서 engine/policy version 추적 가능 |

## FE-01·FE-02 완료 기록

- 구조: `GameArena`는 session hook과 status/editor/result/replay/mission/error 컴포넌트를 배치한다. 순수 reducer가 AI/PvP phase, timer, connection, notice 전이를 소유하고 hook이 REST·STOMP 수명주기를 조정한다.
- 복구: STOMP는 1초에서 시작해 최대 10초인 지수 backoff와 heartbeat를 사용한다. 재연결마다 match topic 구독을 하나로 교체하고 join만 반복한다.
- 중복 방지: 동기 제출 잠금과 `ready` phase gate를 함께 사용한다. PvP submit 뒤 단절·재연결되어도 코드를 다시 publish하지 않고 결과 구독만 복구한다.
- UX: 일시 단절, 일반 요청 오류, 자동 제출, REST/STOMP 세션 만료를 arena 내부 안내 panel로 표시한다. 세션 만료는 편집·제출을 중단하고 로비 복귀를 제공한다.
- 검증: Vitest 7 suite, 18 tests, Vite production build와 mock API 기반 Playwright Chromium 화면 흐름이 통과했다. reducer 전이, 기존 AI generate/compile/run/result, arena·matchmaking bounded backoff와 중복 구독 교체, reconnect 후 submit 1회, REST·broker·재연결 중 cookie 만료를 자동 검증한다.
- 현재 설계: [`../doc/01-frontend.md`](../doc/01-frontend.md)
- FE-01 완료 커밋: `4778b7b` (`refactor(m3): complete FE-01 battle session split`)
- FE-02 완료 커밋: `a46bee4` (`feat(m3): complete FE-02 socket recovery`)

M3 완료 시 기능 규칙과 네트워크·영속 adapter의 변경 영향이 독립 테스트로 제한되어야 한다.
