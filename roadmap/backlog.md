# 남은 작업 상세 백로그

상태 값은 `READY`, `BLOCKED`, `IN_PROGRESS`, `DONE`을 사용한다. 현재 항목은 모두 미구현이므로 `READY`이며, 우선순위 안에서는 표의 순서를 권장 실행 순서로 사용한다.

## P1 — 배포 신뢰성·보안·무결성

### REL-01 실 인프라 AI/PvP E2E

- 상태: READY
- 근거: 현재 자동 테스트는 H2와 mock Redis 중심이며 실제 두 브라우저, MySQL, Redis, Docker를 한 흐름으로 검증하지 않는다.
- 범위: signup/login/me/logout, AI start/compile/run/replay, PvP join/cancel/simultaneous submit/disconnect, Redis/temp 정리.
- 구현 방향: Testcontainers 기반 백엔드 통합 테스트와 Playwright 브라우저 시나리오를 분리하고, 최소 smoke는 CI에서 실행한다.
- 완료 조건: 동일 시나리오를 반복해 엔진·DB 저장 1회, 올바른 winner, Redis/temp 잔존 0을 확인한다.

### API-01 요청·응답 DTO와 단일 오류 계약

- 상태: READY
- 근거: `RunRequestDto`에 validation이 없고 STOMP payload·엔진 결과가 raw `Map`이다. `LandGrabMatchController`가 예외를 직접 잡아 문자열/Map 응답을 섞고 `@CrossOrigin(origins = "*")`로 중앙 CORS 정책과 충돌한다.
- 범위: AI REST 요청, STOMP join/submit, 엔진 결과, 인증 실패와 실행 오류.
- 구현 방향: 필수값·언어·난이도 enum DTO, `@Valid`, 엔진 결과 DTO, `GlobalExceptionHandler` 단일 `ApiError` 적용, controller wildcard CORS 제거.
- 완료 조건: OpenAPI 또는 계약 테스트에서 정상/검증/인증/timeout/내부 오류 응답 스키마가 고정되고 stack·원시 예외 문구가 노출되지 않는다.

### EXEC-02 AI 작업공간 ownership·만료·고아 정리

- 상태: READY
- 근거: AI `/start`는 UUID workspace를 만들지만 `/compile`·`/run`은 인증 사용자와 matchId의 소유 관계를 확인하지 않는다. run까지 가지 않은 start/compile 작업공간은 finally cleanup을 거치지 않는다.
- 범위: match owner binding, workspace 상태·생성 시각, idle TTL, startup/scheduled janitor, compile/run 권한.
- 구현 방향: Redis 또는 DB에 `{matchId, ownerId, status, expiresAt}`를 저장하고 모든 AI API에서 Principal ownership과 상태 전이를 검증한다. workspace root janitor는 안전한 UUID 하위 경로만 삭제한다.
- 완료 조건: 다른 사용자의 matchId 사용이 거부되고, 브라우저 종료·compile 중단·서버 재시작 뒤에도 TTL을 넘은 코드/map 디렉터리가 남지 않는다.

### EXEC-03 플레이어·심판 프로세스 간 격리와 공정성

- 상태: READY
- 근거: 현재 p1, p2, referee가 한 container와 같은 기본 root 사용자/파일 namespace에서 실행된다. host는 Docker 제한으로 보호하지만 악성 player가 상대 코드·map·심판 파일을 읽거나 같은 UID process에 간섭할 수 있다.
- 범위: player별 filesystem/process/user namespace, referee control plane, read-only 입력, action IPC, syscall 정책.
- 구현 방향: player별 독립 container 또는 동등한 sandbox를 사용하고 referee만 결과를 조정한다. 각 player는 자신의 source/runtime와 turn state channel만 볼 수 있어야 하며 non-root UID, seccomp/AppArmor 정책을 명시한다.
- 완료 조건: 공격 corpus가 상대 source/map/referee를 읽거나 수정할 수 없고 상대·심판 process에 signal을 보낼 수 없으며 기존 5개 언어 계약과 timeout 정책이 유지된다.

### MATCH-01 비동기 실행과 명시적 상태 머신

- 상태: READY
- 근거: 두 코드가 준비되면 STOMP inbound 처리 흐름에서 Docker 대전과 DB 저장을 동기 실행한다. 상태는 Redis 문자열 필드에 흩어져 있고 실패 복구·재시도 정책이 없다.
- 범위: `WAITING → READY → RUNNING → PERSISTING → COMPLETED|FAILED|DISCONNECTED` 전이, 작업 queue/executor, timeout, 멱등성 키.
- 구현 방향: 실행을 bounded worker로 넘기고 원자적 상태 전이를 중앙 서비스로 관리한다. 클라이언트에는 접수와 최종 결과를 별도 이벤트로 보낸다.
- 완료 조건: 느린 대전이 STOMP inbound thread를 막지 않고, 중복 submit/retry/disconnect에서 허용된 terminal state 하나만 남는다.

### MATCH-02 다중 인스턴스 매칭 원자성

- 상태: READY
- 근거: 스케줄러가 ZSET에서 사용자를 한 명씩 두 번 pop한다. 여러 백엔드 인스턴스가 동시에 실행될 때 pair 획득과 room 생성이 하나의 원자 연산이 아니다.
- 범위: FIFO pair pop, room 생성, 실패 rollback, scheduler ownership.
- 구현 방향: Redis Lua 또는 검증된 분산 lock/stream consumer로 pair 획득부터 room 생성까지 원자화한다.
- 완료 조건: 2개 이상의 scheduler와 동시 join 부하에서 한 사용자가 두 매치에 배정되지 않고 FIFO·rollback 속성이 유지된다.

### MATCH-03 다중 소켓 disconnect와 cleanup 안정성

- 상태: READY
- 근거: 하나의 사용자 세션이 여러 탭을 가질 때 한 소켓 disconnect가 즉시 기권으로 처리될 수 있다. 오류 경로에서 player ID가 없으면 정리 키 생성이 안전하지 않으며 disconnect 중 예외를 일부 무시한다.
- 범위: 사용자별 활성 소켓 집합, grace period, 재연결, null-safe cleanup, queue/match/socket 키 수명.
- 구현 방향: user-match별 connection count와 reconnect 유예를 두고 cleanup을 멱등 함수로 만든다.
- 완료 조건: 탭 하나 종료는 진행 중 매치를 끝내지 않고, 마지막 연결 종료 또는 유예 만료만 기권을 확정하며 모든 키 삭제 테스트가 통과한다.

### SEC-01 쿠키 인증 보안 정책 완성

- 상태: READY
- 근거: JWT는 HttpOnly 쿠키지만 CSRF가 전역 비활성화되어 있다. 로그인·실행 API rate limit, guest 계정 만료, 제출 코드 접근/보존 정책도 없다.
- 범위: CSRF 방어 방식, origin 검증, login/guest/compile/run 제한, secret rotation, production cookie, guest cleanup.
- 구현 방향: SameSite 배포 구조를 확정하고 CSRF token 또는 엄격한 origin 정책을 적용한다. Redis 기반 rate limit과 비밀 교체 절차를 추가한다.
- 완료 조건: cross-site 상태 변경 요청이 거부되고 brute-force/과다 실행 테스트가 제한되며 운영 secret·cookie 설정이 기동 시 검증된다.

### DATA-01 마이그레이션·제약조건·결과 저장 일관성

- 상태: READY
- 근거: 기본 `ddl-auto=update`에 의존하고 migration이 없다. `GameMatch.mapData`는 필드가 있지만 현재 저장 흐름에서 채우지 않으며 결과 판정이 AI/PvP에 중복되어 있다.
- 범위: Flyway/Liquibase, FK/unique/index/nullability, map/result metadata, 저장 실패 정책.
- 구현 방향: 현재 schema baseline migration을 만들고 `JPA_DDL_AUTO=validate`로 전환한다. match UUID 멱등 저장과 결과 정책을 타입으로 고정한다.
- 완료 조건: 빈 DB와 기존 DB upgrade가 재현되며 같은 match UUID가 중복 저장되지 않고 맵·결과·replay 관계가 완전하다.

### AUTH-01 실제 Google OAuth와 계정 수명주기

- 상태: READY
- 근거: 선택적 등록과 성공 handler는 있으나 실제 공급자 credential smoke가 없고 `email`/`sub` 누락, 기존 로컬 계정과의 연결 정책이 정의되지 않았다.
- 범위: provider claim validation, account linking/collision, callback 오류, logout/revocation.
- 구현 방향: 공급자별 profile adapter와 명시적 연결 정책을 만들고 운영 비밀 환경에서 smoke를 실행한다.
- 완료 조건: 성공·사용자 취소·claim 누락·중복 계정 시나리오가 예측 가능한 오류/연결 결과를 낸다.

### OPS-01 관측 가능성·장애 대응

- 상태: READY
- 근거: 로그 형식과 emoji/System.out 사용이 혼재하고 매치 correlation ID, metric, readiness가 부족하다.
- 범위: request/match/session correlation, queue·match·engine·DB metric, health/readiness, alert, 로그 보존.
- 구현 방향: 구조화 로그와 Micrometer를 적용하고 MySQL/Redis/Docker/engine image readiness를 구분한다.
- 완료 조건: 매치 실패 하나를 ID로 전 구간 추적할 수 있고 timeout 증가, queue 적체, cleanup 실패, DB 저장 실패에 경보가 발생한다.

## P2 — 테스트 가능성·유지보수성

### TEST-01 다층 통합 테스트와 CI 게이트

- 상태: READY
- 근거: 현재 단위·Docker 계약 테스트는 있으나 Redis concurrency, MVC 계약, 영속 결과 행렬, 브라우저 시각 흐름이 비어 있다.
- 범위: Spring MVC/STOMP/Testcontainers, Playwright, failure injection, Windows/Linux matrix.
- 완료 조건: PR마다 빠른 단위 테스트가 실행되고 야간/릴리스 게이트에서 실제 인프라·브라우저·5언어 계약이 통과한다.

### FE-01 GameArena 상태·화면 책임 분리

- 상태: READY
- 근거: `GameArena.js`가 약 466줄이며 타이머, AI/PvP 상태, 소켓, editor, 결과 overlay를 함께 가진다.
- 범위: `useBattleSession`, editor panel, PvP status, result overlay, replay panel.
- 선행 조건: REL-01 또는 Playwright 핵심 시나리오.
- 완료 조건: 페이지는 조합만 담당하고 상태 전이는 reducer/state machine 테스트로 보호되며 시각·기능 회귀가 없다.

### FE-02 소켓 복구와 사용자 오류 경험

- 상태: READY
- 근거: 연결 실패·재연결·구독 오류의 공통 정책이 없고 주요 오류가 `alert`와 console에 의존한다.
- 범위: reconnect backoff, duplicate subscription 방지, session expiry, toast/error boundary, cancel retry.
- 완료 조건: 일시적 네트워크 단절 후 중복 제출 없이 복구되며 사용자가 재시도 가능 여부와 최종 상태를 확인할 수 있다.

### BE-01 엔진 결과 타입과 단일 승패 정책

- 상태: READY
- 근거: AI와 PvP 저장 경로가 winner/crash/draw/disconnect를 별도로 판정하고 raw score Map을 casting한다.
- 범위: `MatchExecutionResult`, `PlayerOutcome`, reason enum, persistence mapper.
- 완료 조건: 정상·양측 crash·단측 crash·draw·disconnect 결과 행렬이 엔진 응답, UI, DB에 같은 값을 만든다.

### EXEC-01 실행 정책과 엔진 버전 관리

- 상태: READY
- 근거: 일부 timeout·자원 값이 코드 상수이며 결과에 엔진 이미지 digest와 정책 버전이 남지 않는다.
- 범위: compile/run/turn/output limit 설정 검증, immutable image digest, 실행 metadata, 보안 회귀 corpus.
- 완료 조건: 정책 변경이 설정과 테스트로 추적되고 모든 match record에서 사용한 engine/policy version을 확인할 수 있다.

### DATA-02 제출 코드·리플레이 보존 정책

- 상태: READY
- 근거: 전체 제출 코드와 LONGTEXT replay를 기간 제한 없이 저장한다.
- 범위: 보존 기간, 삭제 요청, 암호화, 관리자 접근, 용량 상한과 archive.
- 완료 조건: 정책에 따라 자동 만료·삭제가 검증되고 민감 코드 접근이 감사 로그에 남으며 DB 성장 상한을 산정할 수 있다.

## P3 — 현대화·성능

### DEV-01 프론트 빌드 도구 현대화

- 상태: READY
- 근거: CRA 5와 오래된 전이 의존성에서 폐기·Browserslist 경고가 발생한다.
- 선행 조건: Playwright smoke와 production build 비교.
- 완료 조건: 유지보수되는 도구로 전환하고 환경 변수·bundle·테스트 동등성을 확인하며 알려진 high severity 의존성 문제를 정리한다.

### OPS-02 CI/CD와 공급망 검증

- 상태: READY
- 범위: lockfile build, engine image scan/SBOM/signature, migration dry-run, branch protection, staged deploy/rollback.
- 완료 조건: 동일 commit에서 앱 artifact와 immutable engine image가 생성되고 취약점·서명·migration 게이트를 통과해야 배포된다.

### SCALE-01 부하·용량 측정과 비용 최적화

- 상태: READY
- 근거: Docker container를 init/compile/run마다 생성하지만 동시 매치 수와 daemon 포화 한계가 측정되지 않았다.
- 범위: queue latency, container startup, CPU/memory/disk, replay size, worker concurrency.
- 완료 조건: 목표 동시 사용자 기준의 p95/p99와 포화 지점을 기록하고 bounded queue·capacity alert를 설정한다. 격리 수준을 낮추는 최적화는 금지한다.
