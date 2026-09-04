# Code Clash Arena

사용자가 작성한 Python, Java, C, C++, JavaScript 전략 코드를 격리된 Docker 환경에서 실행해 AI 또는 다른 사용자와 겨루는 실시간 알고리즘 배틀 서비스입니다.

## 주요 기능

- Land Grab AI 대전과 Redis 기반 1:1 PvP 매칭
- STOMP WebSocket 실시간 제출 상태·결과 전달
- Monaco Editor와 턴별 리플레이
- HttpOnly JWT 쿠키 기반 로컬·게스트·선택적 Google 로그인
- CPU·메모리·PID·네트워크·실행 시간·출력 크기를 제한한 코드 실행
- correlation/match ID가 포함된 JSON 로그와 readiness·Prometheus metric

## 구성

```text
브라우저(React)
  ├─ REST ───────────────→ Spring Boot ──→ MySQL
  └─ SockJS/STOMP ───────→ Spring Boot ──→ Redis
                                  │
                                  └─ DockerMatchExecutor
                                       └─ code-battle-engine
                                            └─ referee.py → Land Grab
```

```text
code_clash_arena/
├─ frontend/src/
│  ├─ features/           인증·Land Grab 기능 API와 결과 정책
│  ├─ shared/             HTTP, 런타임 설정, STOMP 공통 코드
│  └─ *.jsx               페이지와 화면 조합
├─ backend/code/src/main/java/com/battle/code/
│  ├─ controller/         REST·STOMP 진입점
│  ├─ service/            인증·매칭·게임 오케스트레이션
│  ├─ execution/          Docker 실행과 임시 작업공간 관리
│  ├─ data/               제출 코드·replay 암호화, 보존·삭제, 접근 감사
│  ├─ observability/      correlation context, metric, engine readiness
│  ├─ security/, config/  HTTP·JWT·OAuth2·STOMP·Redis 설정
│  └─ domain/, repository/, dto/
├─ engine/                다중 언어 runner와 Land Grab 규칙
├─ .github/workflows/     Windows/Linux PR gate와 실제 release gate
├─ ops/prometheus/        초기 운영 경보 규칙
├─ doc/                   역할별 현재 코드 설계
├─ docs/                  완료된 분석·검증·개선 기록
├─ roadmap/               미완료 작업과 완료 조건
└─ compose.yaml           로컬 MySQL·Redis
```

## 설계와 로드맵

- 역할별 현재 코드 설계: [`doc/README.md`](doc/README.md)
- 남은 작업과 우선순위·완료 조건: [`roadmap/README.md`](roadmap/README.md)
- 완료 기록 색인: [`docs/README.md`](docs/README.md)

## 로컬 실행

### 요구 사항

- Java 21 (Maven Wrapper가 Java 21~25, Maven 3.9.x를 강제)
- Node.js 22.22.2 이상 또는 24.15 이상 24.x와 npm 10~11
- Python 3.10 이상
- Docker Desktop 또는 호환 Docker daemon

### 1. 설정과 인프라

개발 기본값은 그대로 실행할 수 있습니다. 값을 바꾸려면 루트의 `.env.example`을 `.env`로 복사하고 수정합니다. `.env`는 Git에서 제외되며 실제 Client ID, Client Secret, JWT secret은 이 파일 또는 외부 secret store에만 둡니다. `.envExample`은 다음 인증 작업에서 사용할 OAuth 항목만 모은 빠른 참조용이고, 전체 기준 템플릿은 `.env.example`입니다.

Docker Compose는 루트 `.env`를 자동으로 읽지만 Spring Boot 단독 실행은 읽지 않습니다. Google 로그인을 검증할 때는 `.env`의 `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_*` 값을 셸 환경이나 IDE 실행 설정으로 불러온 뒤 백엔드를 시작해야 합니다.

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

### 2. 코드 실행 이미지

백엔드 기본 이미지 참조는 `code-battle-engine:latest`이며 아래 빌드 명령이 같은 태그를 만듭니다. 매치 실행 시에는 이 태그를 불변 digest로 해석해 실행·저장합니다.

```powershell
docker build -t code-battle-engine engine
```

### 3. 백엔드

```powershell
Set-Location backend/code
.\mvnw.cmd spring-boot:run
```

macOS/Linux에서는 `./mvnw spring-boot:run`을 사용합니다. API 기본 주소는 `http://localhost:8080`, 내부 management 주소는 `http://localhost:8081`입니다.

### 4. 프론트엔드

```powershell
Set-Location frontend
Copy-Item .env.example .env
npm.cmd ci
npm.cmd start
```

기본 주소는 `http://localhost:3000`입니다. 다른 백엔드를 사용하면 `VITE_API_BASE_URL`을 변경합니다.

Google 로그인은 선택 사항입니다. 사용하려면 Spring 표준 환경 변수 `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`, `..._CLIENT_SECRET`, `..._SCOPE`를 설정하고 Google 콘솔에 `http://localhost:8080/login/oauth2/code/google`을 리다이렉트 URI로 등록합니다. scope 기본 계약은 `openid,profile,email`입니다. 설정하지 않아도 로컬·게스트 로그인과 서버 기동은 동작합니다.

## 검증

일반 변경에서는 아래 빠른 회귀만 실행합니다.

```powershell
Set-Location frontend
npm.cmd test
npm.cmd run build

Set-Location ../backend/code
.\mvnw.cmd test

Set-Location ../..
python -m unittest discover -s engine/tests -v
```

실제 인프라, 브라우저, 의존성 감사와 배포 검증 명령은 [테스트·품질 설계](doc/06-testing-quality.md)를 참고합니다.

## 환경 설정

로컬 기본값은 [`.env.example`](.env.example)에 있습니다. 주로 변경하는 값은 다음과 같습니다.

| 목적 | 환경 변수 |
| --- | --- |
| MySQL·Redis | `DATABASE_*`, `REDIS_*` |
| 프론트·쿠키 | `FRONTEND_URL`, `VITE_API_BASE_URL`, `COOKIE_*` |
| 인증 | `JWT_SECRET`, `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_*` |
| 코드 실행 | `ENGINE_IMAGE`, `ENGINE_WORKSPACE`, `ENGINE_POLICY_VERSION`, `ENGINE_CPUS`, `ENGINE_MEMORY`, `ENGINE_PIDS_LIMIT`, `ENGINE_*_TIMEOUT` |
| 민감 데이터 | `DATA_ENCRYPTION_*`, `DATA_*_RETENTION`, `DATA_MAX_*` |

Spring Boot 단독 실행은 루트 `.env`를 자동으로 읽지 않습니다. 셸이나 IDE 실행 설정으로 필요한 값을 주입해야 합니다. 전체 설정과 기본값은 [영속화·운영 설계](doc/05-persistence-operations.md)를 참고합니다.

## 운영 전 확인

- `.env`와 실제 JWT·OAuth·암호화 키는 Git에 커밋하지 않습니다.
- 운영에서는 개발용 `JWT_SECRET`, `DATA_ENCRYPTION_KEY`를 반드시 교체하고 HTTPS cookie를 사용합니다.
- 배포 전 암호화 DB backup을 확인하고 앱과 분리한 migration 계정으로 Flyway를 실행하며, 이미 적용된 migration 파일은 수정하지 않습니다.
- Docker socket과 management endpoint는 외부에 공개하지 않습니다.
- 암호화 키를 교체할 때는 기존 키를 이전 키 목록에 유지합니다.

세부 기준은 [인증·HTTP API](doc/02-auth-http-api.md), [코드 실행·게임 엔진](doc/04-code-execution-engine.md), [관측성·장애 대응](doc/07-observability.md), [빌드·의존성](doc/08-dependency-build.md), [민감 데이터 수명](doc/09-sensitive-data-lifecycle.md)에 나누어 기록되어 있습니다. 운영 MySQL·Redis와 backup/restore 절차는 [DATA-03 운영 데이터 안내](deploy/backend/data/README.md)를 따릅니다.
