# M1 — 배포 가능한 신뢰 경계

- 상태: IN_PROGRESS
- 목표: 실제 MySQL·Redis·Docker와 두 클라이언트를 묶었을 때 인증, 매칭, 제출, 실행, 저장, 종료가 안전하고 일관되게 동작한다.

## 작업 순서와 상태

| ID | 작업 | 상태 | 선행 |
| --- | --- | --- | --- |
| API-01 | 요청·응답 DTO와 단일 오류 계약 | DONE | 없음 |
| EXEC-02 | AI workspace ownership·TTL·고아 정리 | DONE | API-01 request contract |
| MATCH-01 | 비동기 실행과 명시적 상태 머신 | DONE | API-01 |
| MATCH-02 | 다중 인스턴스 매칭 원자성 | DONE | MATCH-01 state vocabulary |
| MATCH-03 | 다중 소켓 disconnect·cleanup | DONE | MATCH-01 |
| EXEC-03 | 플레이어·심판 격리와 공정성 | READY | 실행 계약 테스트 |
| SEC-01 | CSRF·rate limit·운영 비밀 정책 | READY | API-01 |
| DATA-01 | migration·제약조건·저장 일관성 | READY | 결과 DTO/state |
| REL-01 | 실 인프라 M1 E2E | READY | 위 작업 전체 |

## API-01 요청·응답 계약

- 시작 근거: REST/STOMP 요청과 engine 결과가 raw Map 중심이었고 controller별 오류 형식이 달랐다.
- 진행 커밋: `afabaca` (`feat: validate battle API requests`) 및 M1 API 계약 변경 집합.
- 완료 범위: REST/STOMP 요청 DTO, UUID·코드 크기·언어·난이도·gameType validation, wildcard CORS 제거, malformed/execution/interrupted/unauthorized `ApiError`, engine 성공 결과·notification·error DTO, STOMP validation error frame, 직렬화 계약 테스트.
- 완료 조건: 정상/검증/인증/timeout/내부 오류가 고정된 schema를 사용하고 원시 예외를 노출하지 않는다.

## EXEC-02 AI workspace 수명

- 근거: `/start` workspace와 user ownership 연결이 없고 run하지 않은 workspace가 남을 수 있다.
- 범위: owner/status/expiresAt, compile/run 권한, idle TTL, startup/scheduled janitor.
- 완료 범위: `ai_workspace:{matchId}` lease에 owner/status/expiresAt과 TTL을 저장하고 compile/run 소유권·중복 run을 검증한다. run 종료 시 lease와 폴더를 제거하며 startup/주기 janitor는 lease 없는 오래된 UUID 폴더만 정리한다.
- 완료 조건: 다른 사용자의 matchId 사용이 거부되고 중단·재시작 후 TTL을 넘은 workspace가 없다.

## MATCH-01~03 매치 상태와 동시성

- 근거: STOMP inbound 흐름이 Docker/DB를 동기 실행하고, pair 획득은 다중 scheduler에서 원자적이지 않으며, 탭 하나 disconnect가 기권을 만들 수 있다.
- 범위: `WAITING → READY → RUNNING → PERSISTING → COMPLETED|FAILED|DISCONNECTED`, bounded worker, Redis 원자 pair/transition, socket set와 reconnect grace, 멱등 cleanup.
- 완료 범위: Lua CAS 상태 전이, bounded 실행 pool, 원자 pair reservation·room 생성·복귀, 사용자/매치별 socket set, 마지막 소켓의 reconnect grace, 현재 매핑만 지우는 멱등 cleanup을 구현했다.
- 완료 조건: 느린 실행이 inbound thread를 막지 않고 매치당 engine/DB 최대 1회, 한 사용자의 중복 배정 0, 마지막 socket 종료만 기권을 확정한다.

## EXEC-03 player isolation

- 근거: p1, p2, referee가 같은 container와 기본 root UID/file namespace를 공유한다.
- 범위: player별 UID·runtime directory·process permission, root-only referee, read-only input, 공격 corpus.
- 완료 조건: player가 상대 source/referee를 읽거나 수정하고 상대/referee process에 signal을 보낼 수 없으며 5개 언어 계약이 유지된다.

## SEC-01 cookie 인증 보안

- 근거: HttpOnly cookie를 사용하지만 CSRF가 비활성화되어 있고 login/guest/compile/run rate limit과 운영 secret 검증이 없다.
- 범위: same-origin CSRF policy, origin 검사, endpoint rate limit, production cookie/secret fail-fast, guest cleanup 기준.
- 완료 조건: cross-site 상태 변경과 과다 요청이 거부되고 약한 운영 secret/cookie 설정으로 기동할 수 없다.

## DATA-01 schema와 저장

- 근거: `ddl-auto=update`, migration 부재, mapData 미저장, 중복 match UUID와 결과 정책 위험.
- 범위: Flyway baseline, FK/unique/index/nullability, `validate`, map/result metadata, idempotent save.
- 완료 조건: 빈 DB와 upgrade가 재현되고 동일 match UUID 중복 저장이 없으며 map·player·replay 관계가 완전하다.

## REL-01 M1 통합 검증

- 범위: 실제 MySQL·Redis·Docker에서 signup/login/me/logout, AI start/compile/run/replay, PvP join/cancel/동시 submit/disconnect, Redis/workspace cleanup.
- 완료 조건: 반복 실행에서 올바른 winner, engine/DB 1회, 잔존 key/workspace 0, 고정 오류 계약을 확인한다.

## M1 공통 완료 조건

- 모든 M1 항목이 `DONE`이고 완료 커밋이 기록되어 있다.
- backend/frontend/engine 자동 테스트와 production build가 통과한다.
- 실제 Redis/MySQL/Docker smoke가 통과한다.
- 사용자 코드가 상대나 referee 경계를 침범하지 못한다.
- migration과 운영 보안 설정이 문서만이 아니라 기동 시 검증된다.
