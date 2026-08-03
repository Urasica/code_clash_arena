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

서버를 재시작해도 같은 secret이면 기존 JWT를 검증할 수 있다. JWT는 응답 body, redirect query, localStorage에 저장하지 않는다.

## Google OAuth2

Spring `ClientRegistrationRepository`가 있을 때만 `oauth2Login`을 활성화한다. 성공 시 Google `sub`를 `google_{sub}` username으로 사용하고 email 앞부분을 nickname으로 저장한 뒤 프론트 URL로 redirect한다.

## 현재 보안 경계와 제약

- cookie JWT를 사용하지만 CSRF는 현재 비활성화되어 있다.
- rate limit, guest 만료, secret rotation 자동화가 없다.
- 실제 Google OAuth claim 오류·계정 충돌 smoke가 없다.
- engine 성공 결과와 server→client STOMP message는 아직 raw Map 기반이다.
- STOMP validation 실패의 client error frame과 OpenAPI 성공 응답 계약은 아직 고정되지 않았다.

후속 작업은 `API-01`, `SEC-01`, `AUTH-01`로 관리한다.
