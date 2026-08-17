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
│  └─ *.js                페이지와 화면 조합
├─ backend/code/src/main/java/com/battle/code/
│  ├─ controller/         REST·STOMP 진입점
│  ├─ service/            인증·매칭·게임 오케스트레이션
│  ├─ execution/          Docker 실행과 임시 작업공간 관리
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

- Java 17 이상
- Node.js 20 이상과 npm
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

백엔드 기본 이미지 이름과 아래 태그는 모두 `code-battle-engine`입니다.

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

기본 주소는 `http://localhost:3000`입니다. 다른 백엔드를 사용하면 `REACT_APP_API_BASE_URL`을 변경합니다.

Google 로그인은 선택 사항입니다. 사용하려면 Spring 표준 환경 변수 `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`, `..._CLIENT_SECRET`, `..._SCOPE`를 설정하고 Google 콘솔에 `http://localhost:8080/login/oauth2/code/google`을 리다이렉트 URI로 등록합니다. 설정하지 않아도 로컬·게스트 로그인과 서버 기동은 동작합니다.

## 검증

```powershell
# 프론트엔드
Set-Location frontend
npm.cmd test -- --watchAll=false
npm.cmd run build

# 백엔드
Set-Location ../backend/code
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package

# Testcontainers MySQL·Redis·Toxiproxy와 Docker release 회귀
.\mvnw.cmd "-Dcca.run.integration=true" "-Dtest=RealInfrastructureSmokeTest,ObservabilityIntegrationTest,DependencyFailureInjectionTest,FullStackAiFlowTest,FullStackPvpFlowTest" test

# 엔진: code-battle-engine 이미지가 있으면 5개 언어 Docker 계약 테스트도 실행
Set-Location ../..
python -m unittest discover -s engine/tests -v

# 실제 backend가 실행 중일 때 production frontend 브라우저 회귀
Set-Location frontend
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

실제 백엔드 통합 테스트의 MySQL·Redis·Toxiproxy는 Testcontainers가 자동 시작·정리하므로 Compose를 미리 시작하지 않아도 됩니다. Playwright는 로컬 backend, Compose MySQL·Redis, `code-battle-engine`이 실행 가능한 상태에서 사용합니다. 자동화 범위는 [테스트·품질 설계](doc/06-testing-quality.md)에 기록합니다.

## 환경 변수와 운영 주의사항

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | 로컬 `code_arena`/`cca` | MySQL 연결 |
| `DATABASE_*_TIMEOUT_MS` | 연결 5초, 검증 2초, driver 연결·socket 5초 | DB 단절 시 pool 획득·검증·network 대기 상한 |
| `REDIS_HOST`, `REDIS_PORT` | `localhost`, `6379` | 매칭·세션 Redis |
| `REDIS_CONNECT_TIMEOUT`, `REDIS_COMMAND_TIMEOUT` | `3s` | Redis 연결·명령 대기 상한 |
| `FRONTEND_URL` | `http://localhost:3000` | CORS, WebSocket 허용 origin, OAuth 성공 리다이렉트 |
| `JWT_SECRET` | 로컬 개발 전용 값 | 운영에서는 32바이트 이상의 무작위 비밀로 반드시 교체 |
| `JWT_EXPIRATION` | `7d` | JWT와 쿠키 수명 |
| `COOKIE_SECURE`, `COOKIE_SAME_SITE` | `false`, `Lax` | HTTPS 운영에서는 `true`와 배포 구조에 맞는 SameSite 사용 |
| `SECURITY_REQUIRE_ORIGIN` | `true` | 상태 변경 API의 Origin/Referer를 `FRONTEND_URL`과 비교 |
| `RATE_LIMIT_*` | endpoint별 개발 기본값 | login/guest/compile/run Redis 고정 window 제한 |
| `ENGINE_IMAGE` | `code-battle-engine` | 실행 엔진 이미지 |
| `ENGINE_WORKSPACE` | `temp` | 매치별 임시 작업공간 루트 |
| `ENGINE_READINESS_TIMEOUT` | `3s` | Docker와 engine image readiness 검사 제한 시간 |
| `MANAGEMENT_PORT` | `8081` | health·Prometheus 내부 endpoint 포트 |
| `REACT_APP_API_BASE_URL` | `http://localhost:8080` | 프론트 REST·SockJS 기준 주소 |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | 없음 | Google OAuth Client ID. 실제 값은 `.env` 또는 secret store에만 저장 |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | 없음 | Google OAuth Client Secret. 실제 값은 `.env` 또는 secret store에만 저장 |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE` | `profile,email` | Google OAuth 요청 scope |

- 백엔드는 Docker CLI를 직접 호출합니다. Docker socket을 외부에 노출하거나 백엔드 컨테이너에 무제한으로 마운트하지 마세요.
- Flyway가 vendor별 V1을 적용하고 Hibernate는 항상 `ddl-auto=validate`로 schema를 검사합니다. 운영 배포 전 DB backup과 migration 권한을 확인하고 적용된 migration 파일은 수정하지 마세요.
- 브라우저 밖에서 `/api/**` 상태 변경 요청을 보내는 운영 도구도 `FRONTEND_URL`과 같은 `Origin` header를 보내야 합니다.
- 엔진 컨테이너는 네트워크 없음, 0.5 CPU, 512 MiB, PID 128, 읽기 전용 rootfs로 실행됩니다. 정책 변경 시 실행기 테스트와 운영 문서를 함께 갱신하세요.
- Redis 매치 데이터는 30분, WebSocket 세션은 2시간 TTL을 사용합니다. 예상 최대 대전 시간과 장애 복구 정책에 맞춰 함께 조정해야 합니다.
- `.env`, OAuth 비밀, 실제 JWT 비밀과 사용자 제출 코드는 커밋하지 마세요.
- `MANAGEMENT_PORT`는 인증 없이 상태와 metric을 제공하므로 외부에 공개하지 말고 내부 scrape 경계에서만 접근하세요. 기본 경보 규칙은 `ops/prometheus/alerts.yml`에 있습니다.
