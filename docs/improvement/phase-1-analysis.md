# Code Clash Arena 개선 1단계 분석

- 기준일: 2026-08-03
- 기준 커밋: `61eec5c` (`main`)
- 범위: 현행 분석, 검증 기준선, 문제 선정, 목표 구조 설계
- 제외: 운영 코드 수정, 기능 변경, 대규모 구조 이동, 배포

## 1. 두 단계 진행 원칙

| 단계 | 범위 | 산출물 |
| --- | --- | --- |
| 1단계(현재) | 실행 환경과 구조 분석, 실제 기준선 실행, 문제와 우선순위 선정, As-Is/To-Be 설계 | 이 문서, `verification-baseline.md`, `troubleshooting.md` |
| 2단계(후속) | 테스트 기반 마련, 기능 오류 수정, 모듈별 리팩터링, 반복 검증, 최종 운영 문서와 전후 비교 | 변경 코드와 테스트, 갱신된 README, 최종 구조/API/운영/비교 문서 |

1단계에서는 운영 동작을 바꾸지 않는다. 2단계에서는 먼저 재현 가능한 검증 환경을 만든 뒤, 기능 수정과 구조 변경을 서로 다른 커밋으로 진행한다.

## 2. 요약 판단

현재 저장소는 React 프론트엔드, Spring Boot 백엔드, Python 게임 엔진의 큰 경계는 명확하다. AI 대전, PvP 매칭, 다중 언어 실행, 리플레이 저장이라는 제품 흐름도 코드에 구현되어 있다.

다만 현재 상태는 새 개발 환경에서 전체 기능을 재현할 수 있는 기준선이 아니다. 특히 다음 항목이 먼저 해결되어야 한다.

1. 프론트엔드가 선언하지 않은 상위 폴더의 `axios`에 의존한다.
2. 백엔드 테스트 프로필과 공유 가능한 설정 예제가 없어 기본 컨텍스트 테스트가 실패한다.
3. AI 대전에서 백엔드가 생성하는 선수 코드 경로와 엔진이 읽는 경로가 달라 컴파일·실행 계약이 깨져 있다.
4. 사용자 코드 실행에 턴별 응답 제한, 컨테이너 자원 제한, 네트워크 차단이 없어 무한 대기와 자원 고갈 위험이 있다.
5. STOMP 연결 인증 실패를 거부하지 않고 메시지 본문의 `userId`를 신뢰해 다른 사용자로 가장할 수 있다.
6. 마지막 턴 행동이 최종 점수에 반영되지 않아 승패가 잘못될 수 있다.

따라서 2단계의 첫 목표는 폴더 이동이 아니라 **재현 가능한 빌드와 테스트**, **실행 엔진 계약 복구**, **신뢰 경계 보완**이어야 한다.

## 3. 개발 환경과 실행 방법

### 3.1 저장소가 요구하는 환경

| 영역 | 요구 사항 | 실제 코드 근거 |
| --- | --- | --- |
| 프론트엔드 | Node.js 18+, npm | React 18.3.1, CRA 5.0.1, STOMP/SockJS, Monaco |
| 백엔드 | Java 17+, Maven Wrapper | Spring Boot 3.5.8, Java release 17 |
| 데이터 | MySQL, Redis | Spring Data JPA, MySQL Connector, Spring Data Redis |
| 실행 엔진 | Docker daemon과 엔진 이미지 | `LandGrabService`가 `docker run` 실행 |
| 외부 인증 | Google OAuth2 등록 정보 | OAuth2 로그인 성공 핸들러 |

조사 머신에는 Node.js 22.23.1, Java 21.0.4, Docker CLI 27.4.0이 있었다. Docker daemon에는 `code-battle-engine`, `code-execution-engine` 이미지가 모두 없었다.

### 3.2 현재 README 실행 절차의 차이

| 문서 내용 | 실제 상태 | 필요한 조치 |
| --- | --- | --- |
| `./mvn clean package`, `./mvn spring-boot:run` | 저장소에는 시스템 `mvn`이 아니라 `mvnw`/`mvnw.cmd`가 있음 | OS별 Wrapper 명령으로 수정 |
| `docker build -t code-execution-engine .` | 백엔드는 `code-battle-engine`을 실행 | 하나의 설정값과 이름으로 통일 |
| Docker Compose 필요 | Compose 파일이 없음 | MySQL·Redis 개발용 Compose 추가 |
| `DATABASE_URL` 등의 예시 | 해당 환경 변수를 Spring 속성에 연결하는 공유 설정 파일이 없음 | `application-example.yml` 또는 명시적 환경 변수 바인딩 추가 |
| OAuth2 환경 변수 예시 | 실제 Spring OAuth2 속성 이름과 매핑이 없음 | 등록 경로와 선택적 로컬 비활성화 방법 문서화 |

현재 Windows Maven Wrapper는 PowerShell에서 `Target[0]`을 참조하다 실패한다. Git Bash의 `./mvnw test`는 동작하여 Java 소스 32개를 컴파일했지만, 데이터소스 URL 또는 테스트용 내장 DB가 없어 컨텍스트 테스트에서 실패했다.

## 4. 현행 구조와 책임

```text
code_clash_arena/
├─ frontend/                       React 단일 페이지 앱
│  └─ src/
│     ├─ App.js                    화면 전환, 인증 복원, 전역 상태
│     ├─ LoginPage.js              로컬·게스트·Google 로그인 UI와 API
│     ├─ Lobby.js                  게임 선택 UI와 PvP 매칭 소켓
│     ├─ GameArena.js              AI/PvP 세션, 타이머, API, 소켓, 편집기, 결과 UI
│     ├─ ReplayViewer.js           Canvas 리플레이 렌더링
│     └─ CodeTemplates.js          언어별 사용자 코드 템플릿
├─ backend/code/                   Spring Boot 서버
│  └─ src/main/java/com/battle/code/
│     ├─ controller/               REST와 STOMP 진입점
│     ├─ service/                  인증, 매칭, 실행, 세션, 결과 저장
│     ├─ scheduler/                Redis 대기열 폴링과 매치 생성
│     ├─ security/, config/        JWT, OAuth2, HTTP/STOMP, Redis 설정
│     ├─ domain/, repository/      사용자·매치·선수·리플레이 영속화
│     └─ dto/, exception/          요청/응답 일부와 전역 예외 처리
└─ engine/                         컨테이너 내부 게임 실행기
   ├─ referee.py                   언어별 선수 프로세스 준비와 모드 분기
   └─ games/land_grab.py           맵 생성, 턴 루프, 점수, 로그
```

큰 시스템 경계는 적절하지만 내부 책임이 집중되어 있다. `GameArena.js`는 500줄 이상으로 네트워크, 상태 머신, 타이머, 편집기, 결과 표시를 함께 처리한다. 백엔드의 `LandGrabService`도 파일 시스템, 템플릿 합성, Docker 프로세스 실행, JSON 역직렬화를 동시에 담당한다.

## 5. 주요 기능과 데이터 흐름

### 5.1 인증

```text
브라우저
  → /api/auth/signup|login|guest 또는 Google OAuth2
  → AuthService / UserRepository
  → MySQL 사용자 저장·검증
  → JWT 생성
  → HttpOnly 쿠키 + 응답 본문/리다이렉트 쿼리
  → REST는 쿠키, STOMP는 localStorage 토큰 사용
```

JWT 키가 서버 시작마다 새로 생성되므로 재시작 후 기존 쿠키는 무효가 된다. 토큰이 응답 본문과 OAuth2 리다이렉트 URL에도 포함되어 HttpOnly 쿠키의 보호 효과가 약해진다.

### 5.2 AI 대전

```text
GameArena
  → POST /api/match/land-grab/start
  → 백엔드 temp/{matchId} 생성
  → Docker referee init → map.json과 맵 JSON
  → POST /compile → 템플릿에 사용자 코드 삽입 → Docker compile
  → POST /run → 사용자 코드 + AI 코드 → Docker referee run
  → 게임 JSON → MatchService → MySQL
  → ReplayViewer
```

현재 백엔드의 AI 경로는 `temp/{matchId}/p1.py`처럼 평평하게 파일을 쓰지만 엔진은 `/app/players/p1/p1.py`처럼 선수별 하위 폴더만 탐색한다. 따라서 계약을 그대로 실행하면 컴파일과 대전이 시작되지 않는다.

### 5.3 PvP 대전

```text
Lobby STOMP /app/match/join
  → Redis ZSET match_queue:land_grab
  → 1초 주기 MatchingScheduler
  → 2명 pop + 맵 생성 + Redis HASH match_room:{matchId}
  → /topic/match/{userId}
  → GameArena가 게임 소켓 재연결
  → /app/game/join, /app/game/submit
  → 두 코드 Redis 저장
  → Docker PvP 실행
  → DB 저장 + /topic/game/{matchId} 결과
  → Redis 방 정리
```

현재 STOMP 인터셉터는 토큰이 없거나 잘못되어도 연결을 거부하지 않는다. 컨트롤러도 인증 주체가 아니라 메시지 본문의 `userId`를 사용한다. Redis 방과 사용자/소켓 매핑에는 TTL이 없고 정상 종료 시 일부 매핑이 남는다.

### 5.4 외부 의존성

| 의존성 | 용도 | 장애 시 영향 |
| --- | --- | --- |
| MySQL | 사용자, 매치, 선수, 리플레이 저장 | 로그인/저장/백엔드 시작 실패 |
| Redis | 매칭 대기열, 방, 제출 코드, 소켓 매핑 | PvP 전 기능 실패 |
| Docker daemon | 맵 생성, 코드 컴파일·실행 | AI/PvP 게임 실행 실패 |
| 엔진 이미지 | Java/C/C++/Python/Node 실행 환경 | 모든 게임 실행 실패 |
| Google OAuth2 | 소셜 로그인 | Google 로그인만 실패해야 하나 현재 로컬 비활성 구성 없음 |
| 로컬 파일 시스템 | `temp/{matchId}` 맵과 코드 | 공간 누수, 권한/경로 차이에 따른 실행 실패 |

## 6. 현재 동작 기준선

상세 명령과 시나리오는 `verification-baseline.md`에 기록한다.

| 항목 | 결과 | 해석 |
| --- | --- | --- |
| 프론트 의존성 설치 | 성공 | 1,338개 설치, 다수의 CRA 전이 의존성 폐기 경고 |
| 프론트 프로덕션 빌드 | 조건부 성공 | Hook 경고 3건. 미선언 `axios`를 `C:\Users\dlqnf\node_modules`에서 찾아 성공한 비재현 결과 |
| 프론트 테스트 | 실패 | 테스트 0건 실행. 상위 폴더 Axios ESM을 Jest가 파싱하지 못함. 테스트 내용도 CRA 기본 문구 검사 |
| 백엔드 컴파일 | 성공 | Git Bash Wrapper로 Java 소스 32개 컴파일 |
| 백엔드 테스트 | 실패 | 1건 중 오류 1건. 테스트 데이터소스 미설정 |
| Python 구문 검사 | 성공 | Runner 치환 템플릿을 제외한 8개 파일 파싱 성공 |
| 엔진 50턴 루프 | 부분 성공 | 직접 실행 완료. 마지막 보드 영역 51에 보고 점수 50으로 불일치 재현 |
| 맵 샘플 1,000개 | 품질 문제 | 990개에 중복 벽, 32개에 시작점에서 도달 불가능한 코인 존재 |
| Docker 통합 | 실행 불가 | 필요한 두 이미지 모두 없음 |

## 7. 문제 목록과 우선순위

우선순위 정의:

- P0: 실행 불가, 데이터/승패 무결성, 명확한 보안·가용성 위험. 구조 변경 전에 해결한다.
- P1: 핵심 흐름의 오동작, 장애 복구·운영 문제, 높은 회귀 위험.
- P2: 유지보수성과 확장성 저하. P0/P1을 테스트로 보호한 뒤 개선한다.
- P3: 표현, 이름, 주석, 포맷 등 동작과 무관한 정리.

### 7.1 기능·구조 변경 대상

| ID | 문제와 근거 | 영향 범위 | 우선순위 | 검증 방법 |
| --- | --- | --- | --- | --- |
| BLD-01 | `axios`를 import하지만 `package.json`/lock에 없음. 현재 빌드는 상위 폴더 1.8.4를 우연히 사용 | 새 환경 프론트 빌드·테스트 | P0 | 격리된 `npm ci`, `npm ls axios`, build/test |
| BLD-02 | 테스트 프로필/내장 DB가 없어 `contextLoads`가 DataSource 오류로 실패 | 백엔드 전체 회귀 검증 | P0 | `./mvnw test`가 외부 MySQL 없이 통과 |
| BLD-03 | README 이미지 이름과 코드의 이미지 이름이 다르고 Compose/설정 예제가 없음 | 로컬 실행·온보딩·배포 | P0 | 빈 머신 실행 절차와 smoke test |
| ENG-01 | AI 코드는 평평한 경로에 저장하지만 `referee.py`는 `players/p1`, `players/p2`만 탐색 | AI 컴파일·실행 전 언어 | P0 | Docker 기반 언어별 compile/run 통합 테스트 |
| ENG-02 | C 템플릿과 UI는 있지만 `prepare_player`에 C 컴파일 분기가 없음 | C 사용자 전체 | P0 | C 정상/문법 오류 컨테이너 테스트 |
| ENG-03 | 선수 stdout `readline()`에 시간 제한이 없고 Docker CPU/메모리/PID/네트워크 제한도 없음 | 서버 가용성·보안 | P0 | 무응답/무한루프/메모리 과다 코드가 제한 시간 내 종료 |
| GAME-01 | 턴 시작에 점수를 계산하고 이동 후 재계산하지 않아 마지막 행동이 최종 점수에서 누락 | 승패, 전적, 리플레이 | P0 | 마지막 턴 신규 타일 점령 회귀 테스트 |
| SEC-01 | STOMP 인증 실패를 거부하지 않고 본문의 `userId`를 신뢰 | PvP 가장, 타인 제출·대기열 조작 | P0 | 무토큰 연결 거부, 다른 ID payload 무시 테스트 |
| FE-01 | AI 모드에서 `myRole`이 null이라 결과 오버레이가 P1 승리도 패배로 표시 | AI 결과 UX | P1 | P1/P2/draw 렌더링 테스트 |
| FE-02 | PvP 구독 콜백이 `setMyRole` 이전 값인 null을 캡처해 자신의 제출도 상대 제출로 표시 가능 | PvP 준비 상태 UI | P1 | 역할별 알림 상태 Hook 테스트 |
| AUTH-01 | 게스트 로그인은 응답 토큰을 localStorage에 저장하지 않음 | 게스트 PvP 연결·종료 처리 | P1 | 게스트 STOMP 인증 시나리오. 최종적으로는 localStorage 토큰 자체 제거 검토 |
| AUTH-02 | JWT 키가 재시작마다 바뀌며 토큰이 본문/URL/localStorage에 노출되고 쿠키 Secure/SameSite 정책이 없음 | 세션 지속성·토큰 유출 위험 | P1 | 재시작 후 정책 테스트, 쿠키 속성, URL 토큰 부재 |
| MATCH-01 | 동시 제출 시 두 요청이 모두 ready를 보고 엔진을 중복 실행할 수 있는 원자적 상태 전이가 없음 | 중복 실행·중복 DB 저장 | P1 | 동시 제출 통합 테스트, 실행 횟수 1회 보장 |
| MATCH-02 | Redis 방/세션/사용자 매핑 TTL과 완전한 정리가 없음 | 유령 매치·메모리 누수·잘못된 탈주 처리 | P1 | 정상/취소/탈주/오류 후 키와 TTL 검사 |
| OPS-01 | `temp/{matchId}`가 삭제되지 않음 | 디스크 누수, 사용자 코드 잔존 | P1 | 모든 종료 경로에서 디렉터리 삭제 확인 |
| ERR-01 | 전역 예외 처리기가 전체 스택을 500 응답으로 반환하고 다수 컨트롤러가 예외를 제각각 처리 | 내부 정보 노출·불안정한 API 계약 | P1 | 오류 코드/본문 계약과 로그 분리 테스트 |
| GAME-02 | 벽 중복을 허용하고 시작점 간 연결만 검사해 코인이 분리 구역에 생길 수 있음 | 맵 난이도·공정성 | P1 | 고정 seed 속성 테스트: 벽 유일성, 모든 코인 도달 가능 |
| GAME-03 | 크래시와 winner 판정 규칙이 분리되어 양쪽 모두 LOSE 같은 모순이 가능 | 전적 무결성 | P1 | crash/draw/disconnect 결과 행렬 테스트 |
| FE-03 | API URL, 소켓 수명주기, 도메인 상태, UI가 페이지 파일에 결합 | 테스트·환경 분리·변경 영향 | P2 | 추출 후 동일 시나리오 통과, 페이지가 조합 책임만 보유 |
| BE-01 | `LandGrabService`가 파일·템플릿·Docker·JSON·게임 오케스트레이션을 모두 담당 | 단위 테스트와 대체 실행기 도입 난이도 | P2 | 포트 인터페이스 단위 테스트와 Docker 어댑터 테스트 분리 |
| BE-02 | Map/raw Map과 수동 payload 검증이 많고 요청 DTO validation이 거의 없음 | 런타임 캐스팅·오류 응답 불일치 | P2 | DTO validation/직렬화 테스트 |
| TEST-01 | 프론트 CRA 기본 테스트와 백엔드 context test만 있고 엔진 테스트가 없음 | 모든 변경의 회귀 위험 | P0 | 아래 테스트 피라미드와 핵심 시나리오 구축 |

### 7.2 동작과 분리할 스타일 정리

다음은 기능 수정 커밋에 섞지 않는다.

- `[수정]`, `[추가]`, `[NEW]`처럼 이력에만 의미가 있는 주석 정리
- `System.out.println`, 이모지 중심 로그를 일관된 구조화 로그로 정리
- 상수의 `static final` 및 이름 규칙 통일
- 사용하지 않는 import, 주입 필드, DTO 제거
- 들여쓰기, 따옴표, 파일 확장자(`.js`/`.jsx`) 규칙 통일
- 인라인 스타일 이동은 시각 결과가 동일함을 확인하는 별도 구조 커밋으로 처리

## 8. 목표 구조(To-Be)

### 8.1 프론트엔드

현재 규모에 맞춰 지나친 세분화를 피하되, 페이지·기능·공통 코드의 의존 방향을 고정한다.

```text
frontend/src/
├─ app/
│  ├─ App.jsx                       라우팅/페이지 조합
│  └─ config.js                     API·WS base URL과 런타임 설정
├─ pages/
│  ├─ LoginPage.jsx                 페이지 배치만 담당
│  ├─ LobbyPage.jsx
│  └─ ArenaPage.jsx
├─ features/
│  ├─ auth/
│  │  ├─ authApi.js
│  │  ├─ useAuth.js
│  │  └─ AuthForm.jsx
│  ├─ matchmaking/
│  │  ├─ matchmakingSocket.js
│  │  └─ useMatchmaking.js
│  └─ battle/
│     ├─ landGrabApi.js
│     ├─ gameSocket.js
│     ├─ useBattleSession.js
│     ├─ CodeEditorPanel.jsx
│     ├─ BattleStatus.jsx
│     └─ ResultOverlay.jsx
├─ shared/
│  ├─ api/httpClient.js             쿠키·오류 처리 공통화
│  ├─ ui/                            도메인/네트워크를 모르는 공통 UI
│  ├─ hooks/                         둘 이상의 기능에서 쓰는 일반 Hook만
│  └─ utils/                         부수효과 없는 순수 함수만
└─ game/
   ├─ ReplayViewer.jsx
   ├─ replayCanvas.js               Canvas 순수 그리기 함수
   └─ codeTemplates.js
```

분리 기준:

- 페이지는 화면 배치와 기능 조합만 하고 직접 HTTP/STOMP 호출을 하지 않는다.
- 기능 코드는 하나의 사용자 행동 흐름을 소유하며 다른 페이지를 import하지 않는다.
- API/소켓 모듈은 외부 입출력과 직렬화만 담당하고 React 상태를 알지 못한다.
- Hook은 연결 수명주기와 상태 전이를 담당하되 JSX를 반환하지 않는다.
- 공통 UI는 `matchId`, `winner` 같은 도메인 규칙을 알지 않는다.
- 유틸리티는 네트워크, 저장소, DOM 접근이 없는 순수 함수로 제한한다.

### 8.2 백엔드

기술 계층만으로 묶인 현재 구조를 기능 모듈 중심으로 재배치하되, 각 기능 내부에서 API/Application/Infrastructure 경계를 유지한다.

```text
com.battle.code/
├─ auth/                             로그인, 사용자 인증, 토큰/쿠키 정책
├─ matchmaking/                     대기열과 매치 생성
├─ battle/
│  ├─ landgrab/                     게임 시작·규칙별 요청 조정
│  └─ session/                      PvP 제출·상태 전이·종료
├─ execution/
│  ├─ CodeExecutionPort.java        애플리케이션이 의존하는 인터페이스
│  ├─ DockerCodeExecutionAdapter.java
│  ├─ WorkspaceManager.java         temp 생성/정리
│  └─ CodeTemplateManager.java
├─ matchhistory/                     매치·선수·리플레이 저장과 조회
└─ common/
   ├─ config/
   └─ error/                         안정적인 오류 응답 계약
```

핵심 규칙:

- 컨트롤러는 인증 주체와 검증된 DTO만 Application 서비스에 전달한다.
- 매칭/세션 서비스는 Docker 명령, 파일 경로, JPA 구현을 직접 알지 않는다.
- 실행 어댑터는 시간·자원 제한, exit code, stdout JSON 계약을 한 곳에서 보장한다.
- 매치 상태는 `WAITING_FOR_SUBMISSIONS → RUNNING → FINISHED/FAILED`로 원자적으로 전이한다.
- 결과 판정은 하나의 값 객체/함수에서 처리해 DB와 UI의 의미를 일치시킨다.

### 8.3 엔진

```text
engine/
├─ referee.py                        CLI와 모드 분기만
├─ execution/
│  ├─ player_runner.py              언어 탐지·컴파일·프로세스 제한
│  └─ protocol.py                   상태/행동 JSON 계약
├─ games/land_grab/
│  ├─ map_generator.py
│  ├─ rules.py                      이동·점수·승패 순수 로직
│  └─ game_loop.py                  턴 오케스트레이션
└─ tests/
```

게임 규칙을 프로세스 I/O와 분리하면 맵·점수·이동은 Docker 없이 빠르게 단위 테스트하고, 언어 runner만 컨테이너 통합 테스트로 검증할 수 있다.

## 9. As-Is / To-Be 비교

| 관점 | As-Is | To-Be |
| --- | --- | --- |
| 프론트 페이지 | API, STOMP, 타이머, 상태, UI가 한 파일에 집중 | 페이지 조합 / 기능 Hook / API·소켓 / 공통 UI 분리 |
| 환경 설정 | localhost와 이미지 이름 하드코딩 | 단일 런타임 설정과 공유 가능한 예제 설정 |
| 백엔드 실행 | 게임 서비스가 파일과 Docker를 직접 제어 | 실행 포트와 Docker 어댑터, 작업공간 관리자 분리 |
| PvP 신뢰 경계 | payload `userId` 신뢰, 인증 실패 허용 | STOMP Principal만 신뢰, 목적지 권한 검사 |
| 매치 상태 | Redis 필드 존재 여부로 암묵 판정 | 원자적 명시 상태 머신과 TTL |
| 게임 규칙 | I/O 루프 안에서 점수·이동 처리 | 순수 규칙 모듈과 프로세스 오케스트레이션 분리 |
| 테스트 | placeholder 2개, 현재 모두 유효하게 통과하지 않음 | 단위 → 슬라이스 → Docker/Redis/DB 통합 → 핵심 수동 smoke |
| 오류 처리 | 문자열, 스택 트레이스, 상태 코드가 제각각 | 오류 코드·메시지·HTTP/STOMP 계약 통일 |
| 운영 정리 | temp/Redis 잔존 가능 | finally/TTL/보상 처리와 정리 검증 |

## 10. 2단계 작업·커밋 계획

각 항목은 완료 시 전체 기준선 검증을 다시 실행한다. 기능 수정과 구조 이동을 같은 커밋에 넣지 않는다.

1. `test: establish reproducible frontend and backend baseline`
   - Axios를 프로젝트 의존성으로 고정한다.
   - 의미 없는 CRA 테스트를 인증/페이지 smoke test로 교체한다.
   - 백엔드 테스트 프로필과 빠른 단위 테스트 기반을 추가한다.
   - Windows Wrapper와 개발용 설정/Compose/이미지 이름을 정리한다.
2. `test(engine): add runner contract and land-grab rule coverage`
   - 경로, 5개 언어, timeout, 점수, 맵 속성 테스트를 먼저 추가한다.
3. `fix(engine): restore runner contract and correct land-grab results`
   - AI/PvP 파일 배치를 통일하고 C 지원, 마지막 점수, 맵 유일성·도달성을 수정한다.
4. `fix(execution): enforce process and container limits`
   - 턴 응답/전체 실행 timeout, CPU/메모리/PID/네트워크 제한, 출력 크기 제한, temp 정리를 추가한다.
5. `fix(websocket): bind actions to authenticated principals`
   - 무인증 CONNECT 거부, payload ID 제거, 구독 권한, 원자적 RUNNING 전이, TTL/정리를 구현한다.
6. `fix(auth): make token and error handling production-safe`
   - 안정적 키 설정, URL/본문 토큰 제거 방향, 쿠키 정책, 입력 검증, 오류 응답을 정리한다.
7. `refactor(frontend): separate pages, features, and shared adapters`
   - 동작을 바꾸지 않고 API/소켓/Hook/UI를 추출한다.
8. `fix(frontend): correct battle state and result presentation`
   - AI 결과, PvP 역할 알림, 타이머 중복 제출, 연결 오류 UX를 수정한다.
9. `refactor(backend): isolate execution and match lifecycle responsibilities`
   - 테스트를 유지한 채 실행 포트, 작업공간, 세션, 결과 저장 책임을 분리한다.
10. `docs: publish final architecture, operations, and comparison`
    - 최종 구조, 데이터 흐름, API, 환경 변수, 배포/운영 주의, 계획 변경 이유, 트러블슈팅, 전후 비교를 갱신한다.

2단계 완료 조건은 `verification-baseline.md`의 품질 게이트가 모두 통과하고, P0가 0건이며, 남은 P1/P2가 명시적으로 기록되는 것이다.
