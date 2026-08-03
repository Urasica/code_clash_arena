# Code Clash Arena 개선 2단계 결과

- 수행일: 2026-08-03
- 기준: `61eec5c` (`main`)
- 작업 브랜치: `codex/stage-2-improvements`
- 범위: 테스트 기반, 핵심 오류·보안 수정, 책임 분리, 실행·운영 문서 갱신

## 1. 결과 요약

1단계에서 정한 순서대로 검증 기반을 먼저 만들고, 기능 수정과 구조 변경을 별도 커밋으로 수행했다. P0 9개는 모두 코드 또는 재현 가능한 구성으로 해소했다. 자동 검증은 프론트엔드 5건, 백엔드 17건, 엔진 6건으로 늘었고 5개 지원 언어가 실제 Docker 이미지에서 50턴 대전을 완료한다.

| 구분 | 개선 전 | 개선 후 |
| --- | --- | --- |
| 프론트 테스트 | suite 로드 실패, 실행 0건 | 5건 통과 |
| 백엔드 테스트 | 1건 중 오류 1건 | 17건 통과 |
| 엔진 테스트 | 없음 | 규칙·runner·Docker 계약 6건 통과 |
| 프론트 빌드 | 저장소 밖 Axios 의존, Hook 경고 3건 | 저장소 선언 의존성만 사용, 경고 0건 |
| 지원 언어 계약 | 경로 불일치, C 실행 분기 없음 | Python/Java/C/C++/JavaScript compile·run 통과 |
| 게임 정확성 | 마지막 턴 점수 누락, 벽 중복·고립 코인 | 최종 상태 재계산, 고유 벽·도달 가능 코인 |
| 실행 격리 | 턴/전체 시간과 자원 상한 없음 | 턴 timeout + 전체 timeout, CPU 0.5, 512 MiB, PID 128, 네트워크 차단, read-only rootfs |
| 인증 경계 | 랜덤 JWT 키, 토큰 본문/URL/localStorage, payload ID 신뢰 | 설정 기반 키, HttpOnly 쿠키, Principal 기반 STOMP와 구독 권한 |
| 정리 정책 | temp·Redis 키 누수 가능 | 종료 시 temp 삭제, match 30분·WebSocket 2시간 TTL, 관련 키 정리 |

## 2. 작업 단위와 영향 범위

| 커밋 | 분류 | 목적과 영향 |
| --- | --- | --- |
| `b3d07fb` | 문서 | 1단계 현행 분석·검증 기준선·트러블슈팅 확정 |
| `83f77d0` | 테스트/빌드 | Axios 선언, H2 테스트 프로필, Windows Wrapper 수정, 최초 회귀 테스트 |
| `079721a` | 기능/보안 | 엔진 경로·C runner·점수·맵·timeout·컨테이너 제한·temp 정리 수정 |
| `a435c1c` | 기능/보안 | JWT·쿠키·오류 계약·STOMP 권한·원자적 제출·Redis TTL·프론트 결과 오류 수정 |
| `8b31c42` | 구조 | 프론트 API/설정/소켓/결과 정책과 백엔드 Docker/작업공간 책임 분리 |

기능 변경 커밋에서 결과와 보안 경계를 먼저 고정한 뒤 구조 커밋을 적용했다. 각 커밋 뒤 관련 테스트와 빌드를 다시 실행했다.

## 3. As-Is / To-Be 구조

### 프론트엔드

```text
As-Is
App / LoginPage / Lobby / GameArena
  ├─ localhost URL 직접 조립
  ├─ Axios 옵션 반복
  ├─ SockJS·STOMP 생성 반복
  └─ 결과 판정과 UI 혼합

To-Be
pages(App, LoginPage, Lobby, GameArena, ReplayViewer)
  ├─ features/auth/authApi
  ├─ features/landGrab/landGrabApi
  ├─ features/landGrab/matchOutcome
  └─ shared/
      ├─ api/httpClient
      ├─ config/runtime
      └─ realtime/createStompClient
```

- 페이지는 화면 상태와 조합을 담당한다.
- 기능 폴더는 인증·Land Grab에 고유한 API와 순수 정책을 담당한다.
- 공통 폴더는 환경 설정, 쿠키 포함 HTTP, SockJS/STOMP 생성처럼 기능에 독립적인 코드를 담당한다.
- `REACT_APP_API_BASE_URL` 하나로 REST, OAuth, SockJS 주소가 함께 바뀐다.
- `GameArena.js`는 기준 500줄에서 466줄로 줄었으며 직접 Axios/STOMP 생성과 결과 정책이 제거됐다. 화면 분할은 시각 회귀 테스트가 부족해 이번 단계에서는 더 진행하지 않았다.

### 백엔드

```text
As-Is
LandGrabService
  ├─ 매치 오케스트레이션
  ├─ 템플릿 합성
  ├─ 경로 검증·삭제
  ├─ docker run 명령
  └─ 프로세스 stream·timeout

To-Be
LandGrabService               게임 흐름과 JSON 계약
  ├─ CodeTemplateManager      언어별 runner·AI 템플릿
  ├─ DockerMatchExecutor      격리 명령, 프로세스, timeout·출력 상한
  └─ MatchWorkspaceManager    UUID 경로 경계와 안전한 정리
```

설정은 `cca.engine.image`와 `cca.engine.workspace`로 외부화했다. 인증은 `JwtProperties`, `AuthCookieService`, `JwtTokenProvider`로 키·쿠키·토큰 책임을 나눴다.

## 4. 주요 데이터 흐름과 API

### 인증

```text
브라우저 → POST /api/auth/login|guest
        ← HttpOnly JWT cookie (본문/URL에 토큰 없음)
브라우저 → GET /api/auth/me → 서버 Principal 기반 사용자 정보
SockJS handshake → 같은 쿠키로 인증 → STOMP Principal 고정
```

| 종류 | 경로 | 책임 |
| --- | --- | --- |
| REST | `POST /api/auth/signup` | 검증된 로컬 계정 생성 |
| REST | `POST /api/auth/login`, `/guest`, `/logout` | 쿠키 발급·만료 |
| REST | `GET /api/auth/me` | 현재 Principal 조회 |
| OAuth2 | `/oauth2/authorization/google` | 등록 정보가 있을 때만 Google 로그인 활성화 |

### AI 대전

```text
POST /start → UUID 작업공간 → 제한된 Docker init → map.json
POST /compile → p1/ runner 합성 → 제한된 Docker compile
POST /run → p1/ + p2/AI → 제한된 Docker run → 결과·리플레이 저장 → 작업공간 삭제
```

REST 경로는 `/api/match/land-grab/start`, `/compile`, `/run`이다.

### PvP

```text
/app/match/join (Principal)
  → Redis FIFO queue → MatchingScheduler → match_room:{id}, TTL 30분
  → /topic/match/{본인 userId}
/app/game/join → 방 구성원 확인과 socket 역매핑
/app/game/submit → Principal의 역할에만 코드 저장
  → Redis resolution putIfAbsent → 엔진 정확히 1회
  → /topic/game/{참가한 matchId} → DB 저장·Redis/temp 정리
```

STOMP 연결·SEND·SUBSCRIBE는 인증 Principal이 없으면 거부된다. 개인 매치 topic은 본인만, 게임 topic은 방 참가자만 구독할 수 있다.

## 5. 계획에서 조정한 내용

| 초기 방향 | 실제 적용 | 이유 |
| --- | --- | --- |
| 프론트 페이지·기능·공통을 전면 폴더 이동 | 네트워크·설정·순수 정책부터 추출, 페이지는 유지 | 시각 회귀/E2E 기반이 없어 대규모 JSX 이동의 위험이 개선 이익보다 큼 |
| 백엔드 포트/어댑터 전면 전환 | Docker 실행기와 작업공간 관리자부터 분리 | 현재 단일 게임 규모에서 필요한 대체 지점만 만들고 과도한 추상화를 피함 |
| Redis/STOMP 컨테이너 통합 테스트 전부 구축 | interceptor·동시 제출 단위 테스트와 TTL 정책 검증 | 실 Redis 다중 브라우저 테스트 인프라는 별도 후속 범위가 적합 |
| 백엔드도 Compose 컨테이너화 | Compose는 MySQL·Redis만 제공 | 백엔드가 Docker CLI와 호스트 작업공간을 사용하므로 Docker socket을 앱 컨테이너에 노출하지 않는 개발 구성이 더 안전함 |

## 6. 검증 결과

| 게이트 | 결과 |
| --- | --- |
| `npm.cmd test -- --watchAll=false` | 2 suites, 5 tests 통과 |
| `npm.cmd run build` | 경고 없이 성공 |
| `.\mvnw.cmd test` | 17 tests 통과 |
| `python -m unittest discover -s engine/tests -v` | 6 tests 통과, Docker 기반 5언어 포함 |
| `docker compose config` | Compose 구성 유효 |
| `git diff --check` | 공백 오류 없음 |

Docker 계약 테스트는 `code-battle-engine` 이미지를 실제로 빌드한 뒤 Python, Java, C, C++, JavaScript 기본 전략이 각각 50턴 결과를 반환하는지 확인한다.

## 7. 남은 제한과 후속 우선순위

- 실제 두 브라우저, 실 Redis, 실 MySQL을 묶은 PvP E2E는 자동화하지 않았다. 배포 전 join/cancel/동시 submit/disconnect를 브라우저 또는 Testcontainers로 추가해야 한다.
- Google OAuth는 선택적 설정과 토큰 비노출 흐름까지만 구현했다. 실제 공급자 credential을 사용하는 smoke는 운영 비밀이 있는 환경에서 수행해야 한다.
- `GameArena.js`는 여전히 UI·타이머·세션 상태가 큰 파일에 함께 있다. Playwright 같은 시각/E2E 보호막을 먼저 추가한 뒤 editor, status panel, result overlay, session hook 순서로 분리한다.
- 일부 컨트롤러와 저장 계층은 raw `Map`을 사용한다. 다음 단계에서 요청·엔진 결과 DTO와 결과 행렬 테스트를 추가한다.
- CRA 5 전이 의존성의 폐기·취약점 경고는 기능 회귀와 분리해 Vite 등으로 마이그레이션할 항목이다.
- 운영에서는 마이그레이션 도구, `JPA_DDL_AUTO=validate`, HTTPS, `COOKIE_SECURE=true`, 비밀 관리자를 적용해야 한다.

## 8. 유지보수 절차 변화

- 백엔드 주소 변경: 여러 페이지 수정 → `REACT_APP_API_BASE_URL` 한 곳 변경.
- 실행 이미지/작업공간 변경: 서비스 코드 수정 → `ENGINE_IMAGE`, `ENGINE_WORKSPACE` 설정 변경.
- 결과 문구 규칙 변경: JSX 내부 수정 → 순수 `matchOutcome`과 단위 테스트 수정.
- Docker 제한 변경: 게임 서비스와 stream 코드 탐색 → `DockerMatchExecutor`와 해당 테스트 수정.
- 장애 정리 검증: 수동 temp·Redis 확인만 가능 → 작업공간 삭제 테스트, TTL·resolution 테스트로 기본 회귀 탐지.
