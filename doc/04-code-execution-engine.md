# 코드 실행·게임 엔진 설계

## 역할

이 영역은 언어별 사용자 전략을 runner에 합성하고, 안전한 UUID 작업공간을 만들며, 제한된 Docker container에서 compile/init/run을 수행하고 Land Grab 결과 JSON을 반환한다.

## 컴포넌트 경계

| 컴포넌트 | 책임 |
| --- | --- |
| `LandGrabService` | AI/PvP 실행 순서, runner/AI 파일 준비, JSON 변환 |
| `CodeTemplateManager` | classpath의 언어별 runner와 난이도별 AI 코드 로드 |
| `MatchWorkspaceManager` | UUID 검증, workspace root 경계, 재귀 정리 |
| `WorkspaceLeaseService` | AI workspace owner·상태·만료시각과 Redis TTL 관리 |
| `WorkspaceJanitor` | 시작 시점과 주기 실행으로 lease 없는 고아 workspace 정리 |
| `DockerMatchExecutor` | docker command, process stream, 전체 timeout, 출력 크기 상한 |
| `engine/referee.py` | game module 선택, 언어 감지·컴파일, init/compile/run mode |
| `engine/games/land_grab.py` | 맵 생성, player protocol, 턴 루프, 점수·winner·replay |

## 작업공간 계약

기본 root는 백엔드 실행 디렉터리의 `temp`이며 `ENGINE_WORKSPACE`로 바꿀 수 있다. match ID는 UUID parse와 root 하위 경로 검사를 모두 통과해야 한다.

```text
{workspace}/{matchId}/
├─ map.json
├─ p1/
│  ├─ p1.py | p1.c | p1.cpp | p1.js
│  └─ Main.java
└─ p2/
   ├─ p2.py | p2.c | p2.cpp | p2.js
   └─ Main.java
```

AI run은 p1 사용자 코드와 p2 Python AI를 쓴다. PvP run은 두 runner와 scheduler가 전달한 map JSON을 쓴다. AI `/start`는 `ai_workspace:{matchId}` Redis hash에 인증 사용자 owner, `READY` 상태, `expiresAt`을 기록한다. compile은 owner 확인 후 `COMPILED`, run은 `RUNNING`으로 갱신하며 idle TTL을 연장한다. 다른 사용자의 접근, 만료된 lease, 중복 run은 거부한다. AI/PvP run과 transient map은 finally에서 작업공간을 삭제하고 AI run은 lease도 함께 해제한다.

기본 idle TTL은 30분이며 시작 시점과 기본 5분 주기의 janitor가 오래된 UUID 폴더를 검사한다. 대응하는 Redis lease가 없는 폴더만 삭제하고 UUID가 아닌 디렉터리는 건드리지 않는다.

## 언어별 runner

| UI 언어 | template | workspace source | container command |
| --- | --- | --- | --- |
| Python | `python_runner.py` | `p1.py`/`p2.py` | `python3 source` |
| Java | `java_runner.txt` | `Main.java` | `javac`, `java -cp dir Main` |
| C | `c_runner.c` | `p1.c`/`p2.c` | `gcc -O2`, native binary |
| C++ | `cpp_runner.cpp` | `p1.cpp`/`p2.cpp` | `g++`, native binary |
| JavaScript | `js_runner.js` | `p1.js`/`p2.js` | `node source` |

`%USER_CODE%` placeholder에 사용자의 strategy 코드를 삽입한다. runner는 stdin으로 한 줄 JSON state를 읽고 stdout으로 action 문자열 한 줄을 flush한다.

## Docker 실행 정책

기본 이미지: `ENGINE_IMAGE=code-battle-engine`.

| 제한 | 값 |
| --- | --- |
| network | `none` |
| CPU | `0.5` |
| memory | `512m` |
| PID | `128` |
| root filesystem | read-only |
| temp | `/tmp`, 64 MiB, noexec/nosuid |
| capabilities | 모두 drop |
| security option | `no-new-privileges` |
| stdout/stderr | stream당 최대 8 MiB |
| backend init timeout | 15초 |
| backend compile timeout | 20초 |
| backend run timeout | 40초 |
| engine compiler timeout | 10초 |
| player turn timeout | 0.5초 |

container는 `--rm`으로 실행한다. data와 players는 mode에 필요한 경우에만 같은 host match directory를 `/app/data`, `/app/players`에 mount한다.

## mode 계약

### init

`referee.py land_grab init` → `games.land_grab.init(map.json)` → map file과 stdout에 `{walls, coins}` JSON.

### compile

`referee.py land_grab compile` → p1 file 감지·컴파일 → `{status:"success"}` 또는 `{status:"error", error}`. compile 오류도 process exit 0의 구조화 결과로 반환한다.

### run

1. p1/p2를 각각 준비한다.
2. 준비 실패 시 상대 winner와 turn 0 replay를 반환한다.
3. 두 player process를 시작한다.
4. 매 turn 동일 state를 각 관점의 `my_pos`, `enemy_pos`와 함께 전송한다.
5. action을 0.5초 안에 읽고 허용되지 않은 action은 `STAY`로 정규화한다.
6. 이동, 영역, 코인, respawn을 적용한 뒤 점수를 계산하고 snapshot을 저장한다.
7. player process를 종료하고 crash 우선, 점수 차순으로 winner를 결정한다.

## Land Grab 규칙

| 규칙 | 값 |
| --- | --- |
| board | 15×15 |
| 최대 turn | 50 |
| 시작 위치 | p1 `[0,0]`, p2 `[14,14]` |
| wall 비율 | 20% |
| 초기 coin | 5개 |
| coin 점수 | 5점 |
| 영역 점수 | 현재 소유 cell당 1점 |
| 유효 action | `MOVE_UP/DOWN/LEFT/RIGHT`, `STAY` |

벽은 `random.sample`로 고유하게 생성한다. 두 시작점이 연결된 map만 사용하며 coin은 p1 시작점에서 도달 가능한 cell에만 배치한다.

## 결과 JSON

```text
winner: p1 | p2 | draw
final_scores: {p1, p2}
total_turns: 0..50
logs: [turn snapshots]
p1_error: null | message
p2_error: null | message
```

turn snapshot은 action, position, alive, coins, walls, board, scores, board_size를 포함한다.

## 현재 제약

- 자원·timeout 값 일부가 코드 상수이고 match 결과에 engine image digest/policy version이 없다.
- p1, p2, referee가 같은 container와 기본 root UID·파일 namespace를 공유한다. host 자원 격리는 적용되지만 플레이어 간 코드 기밀성·process 격리는 보장하지 않는다.
- Redis가 장시간 중단되면 새 AI workspace lease를 만들 수 없으므로 `/start`도 실패하고 생성한 폴더를 되돌린다.
- 사용자 코드와 replay의 보존/감사 정책이 실행 계층과 연결되어 있지 않다.
- container 생성 비용과 동시 실행 capacity가 측정되지 않았다.
- engine 자체는 JSON 프로세스 계약이지만 백엔드 경계에서 명시적 DTO와 직렬화 계약 테스트로 검증한다.

후속 작업은 `EXEC-01`, `EXEC-02`, `EXEC-03`, `BE-01`, `DATA-02`, `SCALE-01`로 관리한다.
