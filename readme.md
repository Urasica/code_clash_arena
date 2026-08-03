# Code Clash Arena

사용자가 작성한 Python, Java, C, C++, JavaScript 전략 코드를 격리된 Docker 환경에서 실행해 AI 또는 다른 사용자와 겨루는 실시간 알고리즘 배틀 서비스입니다.

## 주요 기능

- Land Grab AI 대전과 Redis 기반 1:1 PvP 매칭
- STOMP WebSocket 실시간 제출 상태·결과 전달
- Monaco Editor와 턴별 리플레이
- HttpOnly JWT 쿠키 기반 로컬·게스트·선택적 Google 로그인
- CPU·메모리·PID·네트워크·실행 시간·출력 크기를 제한한 코드 실행

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
│  ├─ security/, config/  HTTP·JWT·OAuth2·STOMP·Redis 설정
│  └─ domain/, repository/, dto/
├─ engine/                다중 언어 runner와 Land Grab 규칙
├─ docs/improvement/      분석, 검증, 결과, 트러블슈팅
└─ compose.yaml           로컬 MySQL·Redis
```

## 설계와 로드맵

- 역할별 현재 코드 설계: [`doc/README.md`](doc/README.md)
- 남은 작업과 우선순위·완료 조건: [`roadmap/README.md`](roadmap/README.md)
- 완료된 2단계 개선 결과: [`docs/improvement/phase-2-results.md`](docs/improvement/phase-2-results.md)

## 로컬 실행

### 요구 사항

- Java 17 이상
- Node.js 18 이상과 npm
- Python 3.10 이상
- Docker Desktop 또는 호환 Docker daemon

### 1. 설정과 인프라

개발 기본값은 그대로 실행할 수 있습니다. 값을 바꾸려면 루트의 `.env.example`을 `.env`로 복사하고 수정합니다. `.env`는 Compose가 읽으며, 백엔드 값은 같은 이름을 셸 또는 IDE 실행 설정에도 지정해야 합니다.

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

macOS/Linux에서는 `./mvnw spring-boot:run`을 사용합니다. 기본 주소는 `http://localhost:8080`입니다.

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

# 엔진: code-battle-engine 이미지가 있으면 5개 언어 Docker 계약 테스트도 실행
Set-Location ../..
python -m unittest discover -s engine/tests -v
```

현재 자동 검증 범위와 수동 시나리오는 [검증 기준선](docs/improvement/verification-baseline.md), 개선 내역과 남은 제한은 [2단계 결과](docs/improvement/phase-2-results.md)에 기록합니다.

## 환경 변수와 운영 주의사항

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | 로컬 `code_arena`/`cca` | MySQL 연결 |
| `REDIS_HOST`, `REDIS_PORT` | `localhost`, `6379` | 매칭·세션 Redis |
| `FRONTEND_URL` | `http://localhost:3000` | CORS, WebSocket 허용 origin, OAuth 성공 리다이렉트 |
| `JWT_SECRET` | 로컬 개발 전용 값 | 운영에서는 32바이트 이상의 무작위 비밀로 반드시 교체 |
| `JWT_EXPIRATION` | `7d` | JWT와 쿠키 수명 |
| `COOKIE_SECURE`, `COOKIE_SAME_SITE` | `false`, `Lax` | HTTPS 운영에서는 `true`와 배포 구조에 맞는 SameSite 사용 |
| `ENGINE_IMAGE` | `code-battle-engine` | 실행 엔진 이미지 |
| `ENGINE_WORKSPACE` | `temp` | 매치별 임시 작업공간 루트 |
| `REACT_APP_API_BASE_URL` | `http://localhost:8080` | 프론트 REST·SockJS 기준 주소 |

- 백엔드는 Docker CLI를 직접 호출합니다. Docker socket을 외부에 노출하거나 백엔드 컨테이너에 무제한으로 마운트하지 마세요.
- 운영에서는 `JPA_DDL_AUTO=validate`와 별도 마이그레이션 도구 사용을 권장합니다.
- 엔진 컨테이너는 네트워크 없음, 0.5 CPU, 512 MiB, PID 128, 읽기 전용 rootfs로 실행됩니다. 정책 변경 시 실행기 테스트와 운영 문서를 함께 갱신하세요.
- Redis 매치 데이터는 30분, WebSocket 세션은 2시간 TTL을 사용합니다. 예상 최대 대전 시간과 장애 복구 정책에 맞춰 함께 조정해야 합니다.
- `.env`, OAuth 비밀, 실제 JWT 비밀과 사용자 제출 코드는 커밋하지 마세요.
