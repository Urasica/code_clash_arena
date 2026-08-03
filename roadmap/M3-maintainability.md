# M3 — 기능 단위 유지보수 구조

- 상태: READY
- 선행: M2 DONE

| ID | 작업 | 완료 조건 |
| --- | --- | --- |
| FE-01 | `GameArena`를 battle session hook/state machine과 editor/status/result/replay UI로 분리 | page는 조합만 담당하고 시각·기능 회귀 없음 |
| FE-02 | socket reconnect/backoff, duplicate subscription 방지, session expiry·오류 UX | 일시 단절 후 중복 제출 없이 복구 |
| BE-01 | `MatchExecutionResult`, outcome/reason enum, persistence mapper | AI/PvP/crash/draw/disconnect 결과 행렬이 UI·DB와 동일 |
| EXEC-01 | 자원 정책 설정, engine digest/policy version, 보안 corpus | 모든 match에서 engine/policy version 추적 가능 |

M3 완료 시 기능 규칙과 네트워크·영속 adapter의 변경 영향이 독립 테스트로 제한되어야 한다.
