# 영속화·운영 설계

## 역할

이 영역은 사용자·매치·플레이어·리플레이를 MySQL에 저장하고, Redis와 Docker를 포함한 로컬 실행 환경 및 애플리케이션 설정을 제공한다.

## MySQL 도메인 모델

```mermaid
erDiagram
    USER ||--o{ MATCH_PLAYER : participates
    GAME_MATCH ||--o{ MATCH_PLAYER : has
    GAME_MATCH ||--o| MATCH_REPLAY : has

    USER {
        bigint id PK
        string username UK
        string password
        string nickname
        string role
        string provider
        string providerId
        datetime createdAt
    }
    GAME_MATCH {
        bigint id PK
        string matchUuid UK
        string gameType
        string mode
        text mapData
        datetime playedAt
    }
    MATCH_PLAYER {
        bigint id PK
        bigint game_match_id FK
        bigint user_id FK_nullable
        string playerIndex
        string result
        integer score
        text submittedCode
        string language
    }
    MATCH_REPLAY {
        bigint id PK
        bigint game_match_id FK
        longtext fullLog
    }
```

### User

- local username은 사용자가 입력한 ID다.
- Google username은 `google_{sub}`다.
- guest username은 `guest_{uuid}`다.
- role은 `GUEST`, `USER`, `ADMIN` enum 문자열이다.
- local password만 BCrypt hash를 가지며 Google/guest는 null일 수 있다.
- 외부 계정은 `(provider, providerId)` unique identity로 조회한다. local/guest처럼 두 값 중 하나가 null인 행은 이 identity에 참여하지 않는다.
- Google email은 변경될 수 있으므로 저장 계정의 identity나 기존 local 계정 자동 연결 기준으로 사용하지 않는다.

### GameMatch

- `matchUuid`는 외부에 노출되는 UUID이며 unique다.
- `gameType`은 현재 `LAND_GRAB`.
- `mode`는 `AI` 또는 `PVP`.
- player와 replay는 cascade 저장한다.
- `playedAt`은 persist 시 생성된다.
- AI는 workspace의 `map.json`, PvP는 room의 `mapData` snapshot을 저장한다.
- `matchUuid` unique 제약과 저장 전 확인으로 같은 결과의 반복 저장을 멱등 처리한다. 동시 요청 경쟁도 DB unique 제약으로 한 건만 유지한다.

### MatchPlayer

- p1/p2별 user, result, score, submitted code, language를 저장한다.
- AI 대전의 p2는 `user=null`, `language=python`, code 필드에 `AI-{DIFFICULTY}` 표식을 저장한다.
- result 문자열은 `WIN`, `LOSE`, `DRAW`다.

### MatchReplay

engine `logs` 전체 배열을 JSON 직렬화해 LONGTEXT 한 행으로 저장한다. AI/PvP 정상 대전에서 생성하며 disconnect처럼 logs가 없는 경우 PvP replay는 생성하지 않는다.

## 저장 흐름

### AI

`LandGrabMatchController.run`이 엔진 성공 결과와 삭제 전 workspace의 map snapshot을 받은 뒤 인증 사용자에 대해 `MatchService.saveMatchResult`를 호출한다.

1. system `error`가 있으면 저장하지 않는다.
2. user를 조회한다.
3. GameMatch에 map snapshot을 넣고 replay를 만든다.
4. p1 사용자의 winner/crash 결과를 계산한다.
5. p2 AI 결과를 계산한다.
6. UUID 중복 여부를 확인한 뒤 aggregate를 한 transaction으로 저장한다.

DB 저장 실패는 controller 로그에 남지만 engine 결과 HTTP 응답은 계속 전달한다.

### PvP

`GameSessionService`가 engine 결과를 받은 뒤 `savePvPMatchResult`를 호출한다.

1. p1/p2 user 조회.
2. score/winner/reason/error 파싱.
3. Redis room의 map snapshot으로 GameMatch와 선택적 replay 생성.
4. 각 player 결과와 제출 코드를 생성.
5. cascade save.
6. 저장 성공 여부와 무관하게 client result 발행을 시도.

disconnect는 winner/reason과 0:0 기본 score로 같은 PvP 저장 경로를 사용한다.

## 애플리케이션 설정

`backend/code/src/main/resources/application.yml`이 공유 기본값과 환경 변수 binding을 제공한다.

| 영역 | 환경 변수 | 로컬 기본값 |
| --- | --- | --- |
| MySQL | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | localhost `code_arena`, `cca/cca_dev` |
| MySQL timeout | `DATABASE_CONNECTION_TIMEOUT_MS`, `DATABASE_VALIDATION_TIMEOUT_MS`, `DATABASE_CONNECT_TIMEOUT_MS`, `DATABASE_SOCKET_TIMEOUT_MS` | `5000`, `2000`, `5000`, `5000` ms |
| JPA | 고정 설정 | `ddl-auto=validate` |
| Redis | `REDIS_HOST`, `REDIS_PORT` | `localhost:6379` |
| Redis timeout | `REDIS_CONNECT_TIMEOUT`, `REDIS_COMMAND_TIMEOUT` | `3s`, `3s` |
| frontend origin | `FRONTEND_URL` | `http://localhost:3000` |
| engine | `ENGINE_IMAGE`, `ENGINE_WORKSPACE` | `code-battle-engine`, `temp` |
| management | `MANAGEMENT_PORT`, `ENGINE_READINESS_TIMEOUT` | `8081`, `3s` |
| JWT/cookie | `JWT_SECRET`, `JWT_EXPIRATION`, `COOKIE_SECURE`, `COOKIE_SAME_SITE` | 개발값, 7d, false, Lax |

`.env.example`은 Compose와 운영 설정 이름을 함께 보여준다. Spring Boot는 루트 `.env`를 자동으로 읽지 않으므로 backend 값은 shell 또는 IDE에도 export해야 한다.

readiness에 포함되는 DB·Redis 검사가 네트워크 단절 상태에서 무기한 대기하지 않도록 pool 획득·검증과 driver connect/socket, Redis connect/command timeout을 각각 둔다. 운영 환경에서는 정상 쿼리와 네트워크의 p99보다 충분히 크면서 probe 허용 시간보다 작은 값으로 함께 조정한다.

## DB migration과 업그레이드

- MySQL migration은 `db/migration/mysql`, 테스트용 H2 migration은 `db/migration/h2`에 분리한다.
- MySQL 8.4와 Flyway 11.20.3 조합을 지원 기준으로 고정하며 실제 MySQL smoke에서 migrate와 validate를 모두 실행한다.
- 빈 MySQL에는 Flyway V1이 네 domain table, FK, unique, 조회 index를 만든다.
- 기존 Hibernate 관리 schema는 `baseline-version=0`으로 등록한 뒤 같은 V1을 실행한다. V1은 기존 table을 보존하면서 누락된 index와 unique 제약을 추가하고 null map/code/language를 명시적인 legacy 값으로 보정한다.
- V2는 `users(provider, provider_id)`에 `uk_users_provider_identity` unique 제약을 추가해 동일 Google `sub`의 중복 계정 생성을 막는다. H2 테스트 migration도 같은 계약을 적용한다.
- V2 배포 전 `provider_id IS NOT NULL`인 기존 행을 `(provider, provider_id)`로 집계해 중복이 없는지 확인한다. 중복이 있으면 계정 소유 관계를 먼저 수동 정리하고 migration을 실행하며 임의 병합하지 않는다.
- 기존 map을 복원할 수 없는 행은 `{"legacy":true}`로 표시한다. 신규 AI/PvP 결과에는 실제 초기 map JSON이 필수다.
- migration 후 Hibernate `validate`가 entity와 물리 schema의 타입·필수 table/column 일치를 확인하며 불일치 시 기동을 중단한다.
- 배포 전 DB backup을 만들고 애플리케이션과 동일 계정으로 migration 권한을 확인해야 한다. 이미 적용된 migration 파일은 수정하지 않고 다음 버전 파일을 추가한다.

## 로컬 인프라

`compose.yaml`은 애플리케이션이 아닌 MySQL 8.4와 Redis 7.4만 실행한다.

| service | port | volume | healthcheck |
| --- | --- | --- | --- |
| mysql | 3306 | `mysql-data` | `mysqladmin ping` |
| redis | 6379 | `redis-data` | `redis-cli ping` |

백엔드는 host Docker CLI로 별도 engine container를 실행하므로 Docker socket을 backend container에 mount하는 구성을 기본으로 제공하지 않는다.

## 데이터 수명

| 데이터 | 현재 수명 |
| --- | --- |
| JWT cookie | 기본 7일 |
| Redis match room/user/socket mapping | 30분 또는 정상 cleanup |
| Redis websocket session | 2시간 또는 disconnect cleanup |
| match workspace | AI/PvP run과 transient map 후 삭제 |
| user/match/player/replay/submitted code | 자동 만료 없음 |

## 현재 제약

- DB 저장 실패 후 재시도/outbox가 없고 client 결과와 영속 상태가 달라질 수 있다. UUID 멱등성은 중복을 막지만 전달 보장은 하지 않으며, 현재는 저장 outcome과 duration metric으로 실패를 탐지한다.
- 제출 코드, guest user, replay의 보존·삭제 정책이 없다.

DATA-01의 migration, map 저장, aggregate 제약, UUID 멱등성은 자동 테스트와 실제 MySQL smoke로 검증한다. 관측 경계는 [관측성·장애 대응 설계](07-observability.md), 전달 보장과 보존 정책은 후속 마일스톤에서 다룬다.
