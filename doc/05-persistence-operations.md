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

### GameMatch

- `matchUuid`는 외부에 노출되는 UUID이며 unique다.
- `gameType`은 현재 `LAND_GRAB`.
- `mode`는 `AI` 또는 `PVP`.
- player와 replay는 cascade 저장한다.
- `playedAt`은 persist 시 생성된다.
- `mapData` 필드는 존재하지만 현재 `MatchService` 저장 경로에서는 채우지 않는다.

### MatchPlayer

- p1/p2별 user, result, score, submitted code, language를 저장한다.
- AI 대전의 p2는 `user=null`, `language=python`, code 필드에 `AI-{DIFFICULTY}` 표식을 저장한다.
- result 문자열은 `WIN`, `LOSE`, `DRAW`다.

### MatchReplay

engine `logs` 전체 배열을 JSON 직렬화해 LONGTEXT 한 행으로 저장한다. AI/PvP 정상 대전에서 생성하며 disconnect처럼 logs가 없는 경우 PvP replay는 생성하지 않는다.

## 저장 흐름

### AI

`LandGrabMatchController.run`이 엔진 성공 결과를 받은 뒤 인증 사용자에 대해 `MatchService.saveMatchResult`를 호출한다.

1. system `error`가 있으면 저장하지 않는다.
2. user를 조회한다.
3. GameMatch와 replay를 만든다.
4. p1 사용자의 winner/crash 결과를 계산한다.
5. p2 AI 결과를 계산한다.
6. cascade save한다.

DB 저장 실패는 controller 로그에 남지만 engine 결과 HTTP 응답은 계속 전달한다.

### PvP

`GameSessionService`가 engine 결과를 받은 뒤 `savePvPMatchResult`를 호출한다.

1. p1/p2 user 조회.
2. score/winner/reason/error 파싱.
3. GameMatch와 선택적 replay 생성.
4. 각 player 결과와 제출 코드를 생성.
5. cascade save.
6. 저장 성공 여부와 무관하게 client result 발행을 시도.

disconnect는 winner/reason과 0:0 기본 score로 같은 PvP 저장 경로를 사용한다.

## 애플리케이션 설정

`backend/code/src/main/resources/application.yml`이 공유 기본값과 환경 변수 binding을 제공한다.

| 영역 | 환경 변수 | 로컬 기본값 |
| --- | --- | --- |
| MySQL | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | localhost `code_arena`, `cca/cca_dev` |
| JPA | `JPA_DDL_AUTO` | `update` |
| Redis | `REDIS_HOST`, `REDIS_PORT` | `localhost:6379` |
| frontend origin | `FRONTEND_URL` | `http://localhost:3000` |
| engine | `ENGINE_IMAGE`, `ENGINE_WORKSPACE` | `code-battle-engine`, `temp` |
| JWT/cookie | `JWT_SECRET`, `JWT_EXPIRATION`, `COOKIE_SECURE`, `COOKIE_SAME_SITE` | 개발값, 7d, false, Lax |

`.env.example`은 Compose와 운영 설정 이름을 함께 보여준다. Spring Boot는 루트 `.env`를 자동으로 읽지 않으므로 backend 값은 shell 또는 IDE에도 export해야 한다.

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

- versioned DB migration이 없고 `ddl-auto=update`가 기본이다.
- match map metadata가 DB에 저장되지 않는다.
- DB 저장 실패 후 재시도/outbox가 없고 client 결과와 영속 상태가 달라질 수 있다.
- 제출 코드, guest user, replay의 보존·삭제 정책이 없다.
- 구조화 metric/readiness/correlation ID가 부족하다.

후속 작업은 `DATA-01`, `DATA-02`, `OPS-01`, `OPS-02`로 관리한다.
