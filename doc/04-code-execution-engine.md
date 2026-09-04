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
| `EnginePolicyProperties` | CPU·memory·PID·tmpfs·출력·mode별 timeout의 검증된 설정 |
| `DockerEngineMetadataProvider` | image tag를 불변 digest로 해석하고 policy version과 실행 snapshot 생성 |
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

기본 이미지: `ENGINE_IMAGE=code-battle-engine:latest`. 실행 직전에 Docker image를 inspect해 repository digest 또는 image ID를 얻고, tag가 아니라 이 불변 참조로 container를 실행한다. `ENGINE_POLICY_VERSION`은 정책값을 변경할 때 함께 올리는 운영 식별자이며 run 결과와 disconnect 결과 모두 `game_match`에 digest와 함께 저장된다.

이미지는 Ubuntu 24.04를 기반으로 Python 3.12 계열, OpenJDK 21, GCC/G++ 13 계열을 설치하고 Node 24.18.0 binary를 명시적으로 가져온다. JavaScript와 Java runner의 지원 버전은 프론트/백엔드 CI와 같은 Node 24·Java 21 기준이며, 이미지 변경은 다섯 언어 compile/run 계약을 모두 통과해야 한다.

| 제한 | 값 |
| --- | --- |
| network | `none` |
| CPU | `ENGINE_CPUS`, 기본 `0.5` |
| memory | `ENGINE_MEMORY`, 기본 `512m` |
| PID | `ENGINE_PIDS_LIMIT`, 기본 `128` |
| root filesystem | read-only |
| temp | `/tmp`, `ENGINE_TEMP_SIZE`, 기본 64 MiB, noexec/nosuid |
| player runtime | `/run/players`, `ENGINE_PLAYERS_SIZE`, 기본 128 MiB, exec/nosuid/nodev |
| referee capabilities | drop all 후 CHOWN, DAC_READ_SEARCH, KILL, SETUID, SETGID만 추가 |
| player UID/capabilities | p1=10001, p2=10002, effective capabilities 0 |
| security option | `no-new-privileges` |
| stdout/stderr | stream당 `ENGINE_MAX_OUTPUT_BYTES`, 기본 8 MiB |
| backend init timeout | `ENGINE_INIT_TIMEOUT`, 기본 15초 |
| backend compile timeout | `ENGINE_COMPILE_TIMEOUT`, 기본 20초 |
| backend run timeout | `ENGINE_RUN_TIMEOUT`, 기본 40초 |
| engine compiler timeout | 10초 |
| player turn timeout | 0.5초 |

container는 `--rm`으로 실행한다. network 차단, read-only root, capability allowlist와 `no-new-privileges`는 설정으로 완화할 수 없는 고정 보안 경계다. 설정 binding은 CPU 0.1~4.0, PID 16~1024, memory/tmpfs 크기와 timeout 1초~5분 범위를 검증하며 범위를 벗어나면 기동을 거부한다. data와 players는 run/compile mode에 필요한 경우에만 같은 host match directory를 `/app/data`, `/app/players`에 mount한다. init은 bind mount 없이 map JSON만 stdout으로 반환하고, 백엔드가 필수 필드를 검증한 뒤 host workspace의 `map.json`을 저장한다. run/compile mount는 root 심판만 읽는다. 심판은 각 소스를 `/run/players/p1|p2`로 복사한 뒤 디렉터리와 파일을 해당 전용 UID에 넘기고 mode 700/600으로 잠근다. compile과 player process는 비어 있는 환경과 전용 HOME으로 UID 전환한 뒤 시작한다. `/app` 전체는 root만 읽을 수 있다.

Docker 통합 보안 corpus는 상대·심판 파일 읽기, read-only root 쓰기, effective capability, referee signal, 외부 network, container 환경 변수 상속 공격을 실제 player 코드로 실행하며 모두 차단되는지 확인한다.

## mode 계약

### init

`referee.py land_grab init` → `games.land_grab.init()` → stdout에 `{walls, coins}` JSON. `LandGrabService`가 `walls`·`coins`를 검증하고 host workspace에 `map.json`을 저장한다. 엔진 오류 JSON이나 필수 필드 누락은 lease 생성 전에 실패하며 작업공간을 정리한다.

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

- 심판과 플레이어는 같은 container PID namespace를 사용하므로 커널 수준의 완전한 container 분리는 아니며, UID·파일 mode·capability 경계로 상호 접근을 차단한다.
- Redis가 장시간 중단되면 새 AI workspace lease를 만들 수 없으므로 `/start`도 실패하고 생성한 폴더를 되돌린다.
- 실행 중 사용자 코드는 match workspace의 파일로 존재하고 종료·TTL 정리 시 삭제된다. DB로 영속화되는 code/replay는 저장 경계에서 암호화되지만 workspace 자체는 별도 저장 암호화를 사용하지 않는다.
- engine 자체는 JSON 프로세스 계약이지만 백엔드 경계에서 명시적 DTO와 직렬화 계약 테스트로 검증한다.

후속 ARM 실행 게이트와 배포 경계는 [M4](../roadmap/M4-modernization-scale.md)에서 관리한다. DB 민감 payload 정책은 [민감 데이터 수명 설계](09-sensitive-data-lifecycle.md)를 따른다.
