# 인증·HTTP API 설계

## 역할

이 영역은 사용자 계정 생성·검증, JWT 발급/검증, HttpOnly cookie, REST 접근 제어, CORS, 표준 오류 응답을 담당한다.

## 주요 컴포넌트

| 컴포넌트 | 책임 |
| --- | --- |
| `AuthController` | signup/login/guest/me/logout HTTP 계약 |
| `AuthService` | 로컬 계정 중복 확인·BCrypt 검증, guest 생성 |
| `JwtTokenProvider` | user ID/role claim JWT 생성·검증, Spring Authentication 생성 |
| `JwtFilter` | `accessToken` cookie를 읽어 SecurityContext 설정 |
| `AuthCookieService` | JWT cookie 발급·만료와 HttpOnly/Secure/SameSite 속성 |
| `CustomUserDetailsService` | JWT subject인 user ID로 DB user를 로드하고 role 변환 |
| `OAuth2SuccessHandler` | Google claim을 로컬 user로 연결/생성, JWT cookie, 프론트 redirect |
| `SecurityConfig` | stateless filter chain, endpoint 권한, CORS, optional OAuth2 |
| `SameOriginFilter` | 상태 변경 API의 Origin/Referer를 프론트 origin과 비교 |
| `RateLimitFilter` | 로그인·게스트·컴파일·실행 요청을 사용자/IP 단위로 제한 |
| `ProductionSecurityValidator` | 운영 secret·cookie·HTTPS 설정을 기동 시 검증 |
| `GuestAccountJanitor` | 보존 기간이 지난 미참조 guest 계정을 주기적으로 정리 |
| `GlobalExceptionHandler` | validation/auth/conflict/not-found/unexpected 오류를 `ApiError`로 변환 |

## 인증 흐름

```mermaid
sequenceDiagram
    participant C as Browser
    participant AC as AuthController
    participant AS as AuthService
    participant JWT as JwtTokenProvider
    participant DB as UserRepository

    C->>AC: POST /api/auth/login
    AC->>AS: username/password 검증
    AS->>DB: findByUsername
    DB-->>AS: User
    AS-->>AC: User
    AC->>JWT: createToken(userId, role)
    JWT-->>AC: signed JWT
    AC-->>C: Set-Cookie accessToken; HttpOnly
    C->>AC: GET /api/auth/me + cookie
    Note over C,AC: JwtFilter가 Principal을 먼저 설정
    AC->>DB: findById(principal name)
    AC-->>C: userId, nickname, role, provider
```

JWT subject와 Spring `UserDetails.username`은 로그인 ID가 아니라 DB `User.id` 문자열이다.

## REST API

| Method | 경로 | 인증 | 요청 | 성공 응답 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/signup` | 불필요 | username, password, nickname | `Signup Success` |
| POST | `/api/auth/login` | 불필요 | username, password | cookie + message/userId/nickname |
| POST | `/api/auth/guest` | 불필요 | 없음 | cookie + userId/nickname |
| GET | `/api/auth/me` | endpoint는 public, 내부 Principal 확인 | 없음 | userId/nickname/role/provider |
| POST | `/api/auth/logout` | 불필요 | 없음 | cookie Max-Age 0 |
| POST | `/api/match/land-grab/start` | 필요 | 없음 | matchId/walls/coins |
| POST | `/api/match/land-grab/compile` | 필요 | matchId/userCode/language | status/error |
| POST | `/api/match/land-grab/run` | 필요 | matchId/userCode/language/difficulty | engine result |

`/api/match/**`는 Spring Security에서 인증을 요구한다. `/api/auth/**`, OAuth2 callback, OPTIONS는 허용된다.

## 입력 검증과 오류

- signup: username 3~40, password 8~100, nickname 2~40.
- login: username/password 필수 및 길이 검증.
- AI compile/run: UUID matchId, 64,000자 이하 code, 지원 언어, 선택적 easy/normal/hard 난이도.
- STOMP match/game 요청: land_grab gameType, UUID matchId, 필수 code와 지원 언어.
- `GlobalExceptionHandler.ApiError`: `{code, message}`.
- 처리 코드: `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `INVALID_CREDENTIALS`, `CONFLICT`, `BAD_REQUEST`, `NOT_FOUND`, `EXECUTION_ERROR`, `EXECUTION_INTERRUPTED`, `INTERNAL_ERROR`.
- 보안 처리 코드: `CSRF_REJECTED`, `RATE_LIMITED`, `RATE_LIMIT_UNAVAILABLE`.
- 인증되지 않은 보호 endpoint는 `{code:"UNAUTHORIZED", message:"Authentication required"}`를 반환한다.
- 예상하지 못한 예외 stack은 서버 로그에만 남기고 응답에는 일반 문구를 사용한다.

Land Grab controller는 실행 예외를 전역 handler에 위임한다. DB 결과 저장 실패는 사용자에게 engine 결과를 전달하기 위해 controller 내부에서 별도로 기록하고 성공 응답은 유지한다.

## Cookie/JWT 설정

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| `cca.jwt.secret` / `JWT_SECRET` | 로컬 개발 전용 32바이트 이상 값 | HMAC signing key |
| `cca.jwt.expiration` / `JWT_EXPIRATION` | `7d` | JWT와 cookie lifetime |
| `cca.auth.cookie.secure` | `false` | 운영 HTTPS에서는 `true` 필요 |
| `cca.auth.cookie.same-site` | `Lax` | 배포 origin 구조에 맞게 설정 |
| `cca.frontend-url` | `http://localhost:3000` | CORS, SockJS origin, OAuth redirect |
| `cca.security.require-origin` | `true` | `/api/**` 상태 변경 요청의 동일 origin 강제 |
| `cca.security.rate-limit.window` | `1m` | rate limit 고정 window |
| `cca.auth.guest-ttl` | `24h` | 참조되지 않은 guest 계정 보존 기간 |

서버를 재시작해도 같은 secret이면 기존 JWT를 검증할 수 있다. JWT는 응답 body, redirect query, localStorage에 저장하지 않는다.

## Google OAuth2

Spring `ClientRegistrationRepository`가 있을 때만 `oauth2Login`을 활성화한다. 성공 시 Google `sub`를 `google_{sub}` username으로 사용하고 email 앞부분을 nickname으로 저장한 뒤 프론트 URL로 redirect한다.

Google 등록 정보는 Spring 표준 환경 변수 `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`, `..._CLIENT_SECRET`, `..._SCOPE`로 주입한다. 개발자는 실제 값을 Git에서 제외된 루트 `.env` 또는 외부 secret store에만 보관한다. 전체 공개 템플릿은 `.env.example`, OAuth 항목 빠른 참조는 `.envExample`이며 두 파일에는 실제 credential을 넣지 않는다. Docker Compose와 달리 Spring Boot 단독 실행은 루트 `.env`를 자동으로 읽지 않으므로 실행 셸 또는 IDE가 값을 주입해야 한다.

## 현재 보안 경계와 운영 기준

- unsafe method(`POST`, `PUT`, `PATCH`, `DELETE`)의 `/api/**` 요청은 `Origin` 또는 `Referer`가 `cca.frontend-url`과 같아야 한다. 브라우저 밖의 운영 도구도 허용된 `Origin` header를 보내야 한다.
- Spring 기본 CSRF token 대신 cookie 인증 구조에 맞춘 동일-origin 정책을 사용한다. CORS도 허용되지 않은 `Origin`을 먼저 차단할 수 있다.
- 로그인 10회, guest 5회, compile 20회, run 10회를 기본 1분 window로 제한한다. 인증 요청은 user ID, 비인증 요청은 remote IP가 기준이며 Redis 장애 시 보호 endpoint는 `503 RATE_LIMIT_UNAVAILABLE`로 닫힌다.
- `prod` profile은 32자 미만 또는 개발 기본 JWT secret, 비보안 cookie, 잘못된 SameSite, HTTP frontend URL을 거부한다.
- 생성 후 24시간이 지난 guest 중 `match_player`가 참조하지 않는 계정만 주기적으로 삭제한다.
- secret rotation은 배포 운영 절차에 속하며 자동화하지 않는다. 실제 Google OAuth claim 오류·계정 충돌 smoke는 별도 인증 마일스톤에서 다룬다.
- Land Grab 성공 응답은 `StartMatchResponseDto`, `CompileResultDto`, `MatchExecutionResultDto`로 고정되어 있으며 engine의 snake_case 필드도 직렬화 테스트로 보호한다.
- STOMP validation 실패는 `/user/queue/errors`로 `{type:"ERROR", code:"VALIDATION_ERROR", message}`를 반환한다.

SEC-01의 동일-origin, rate limit, 운영 fail-fast, guest cleanup은 자동 테스트로 보호한다.
