# 테스트·품질 설계

## 역할

테스트 영역은 프론트 표시 정책, Spring context·인증·STOMP·매치 조정, workspace·Docker 명령, Python 게임 규칙과 5개 언어 실행 계약을 계층별로 검증한다.

## 현재 테스트 계층

| 계층 | 위치 | 현재 수 | 주요 보장 |
| --- | --- | --- | --- |
| 프론트 단위/컴포넌트 | `frontend/src/**/*.test.js` | 5 tests | 익명/세션 복원, token localStorage 부재, AI/draw/disconnect 결과 표시 정책 |
| 백엔드 context/단위 | `backend/code/src/test/java` | 27 tests | H2 context, 요청 DTO, REST 오류/401 계약, Principal 기반 STOMP controller, 구독 권한, JWT·cookie, auth, resolution, workspace, Docker 명령 |
| 엔진 규칙 | `engine/tests/test_land_grab.py`, `test_referee.py` | 4 tests | turn timeout, 마지막 점수, 맵 속성, C compiler 분기 |
| Docker 계약 | `engine/tests/test_runners_integration.py` | 2 tests | 5개 언어 compile과 50-turn run |

엔진 전체 suite는 6개 test이며 Docker image가 없으면 계약 2개는 명시적으로 skip한다.

## 실행 명령

```powershell
# frontend
Set-Location frontend
npm.cmd test -- --watchAll=false
npm.cmd run build

# backend
Set-Location ../backend/code
.\mvnw.cmd test

# engine
Set-Location ../..
python -m unittest discover -s engine/tests -v

# Docker 계약만 직접 실행
python engine/tests/test_runners_integration.py -v

# 문서/구성 기본 검사
docker compose config
git diff --check
```

macOS/Linux backend는 `./mvnw test`를 사용한다. engine Docker 계약 전에 `docker build -t code-battle-engine engine`이 필요하다.

## 백엔드 테스트 격리

`application-test.properties`는 H2 memory DB를 MySQL compatibility mode로 사용하고 JPA schema를 create-drop한다. scheduling은 꺼서 1초 matcher가 단위 테스트에 개입하지 않는다. OAuth2는 test client registration을 사용한다.

Redis가 필요한 service test는 `RedisTemplate` 연산을 mock한다. 따라서 빠르고 결정적이지만 실제 Redis serialization, TTL, Lua/concurrency 동작까지 보장하지는 않는다.

## Docker 계약 조건

integration test는 다음 조건을 만족할 때만 실행한다.

1. `docker` executable이 PATH에 있다.
2. `code-battle-engine` image inspect가 성공한다.

Python, Java, C, C++, JavaScript의 backend runner template에 최소 strategy를 치환하고 실제 container에서 compile한다. run test는 각 언어가 player process로 참여해 50-turn 결과 JSON을 반환하는지 검증한다.

## 변경별 필수 게이트

| 변경 영역 | 최소 실행 |
| --- | --- |
| frontend page/API/result | frontend tests + build |
| auth/security/controller | backend 전체 test |
| Redis/STOMP/session | backend test + 향후 실 Redis integration |
| runner/template/referee | engine 전체 + Docker 계약 |
| Land Grab 규칙 | engine 규칙 + Docker 계약 |
| schema/entity/persistence | backend test + 향후 migration/Testcontainers |
| compose/설정/문서 | compose config + diff check + 문서 링크 검사 |

## 현재 검증 공백

- 실 MySQL/Redis를 쓰는 repository·TTL·동시성 검증.
- 두 브라우저 PvP join/cancel/submit/disconnect E2E.
- Google OAuth 실제 공급자 smoke.
- HTTP/STOMP 전체 payload schema와 오류 contract test.
- GameArena timer/socket/reconnect와 replay Canvas browser test.
- DB migration upgrade, 저장 실패, Redis/Docker 장애 주입.
- player가 상대 source/map/referee/process에 접근하지 못함을 확인하는 sandbox 공격 corpus.
- 중단된 AI start/compile workspace의 ownership·TTL·janitor 검증.
- 부하, queue latency, container capacity, 장기 데이터 증가 측정.

후속 작업은 `REL-01`, `TEST-01`, `AUTH-01`, `SCALE-01`로 관리한다.

## 품질 기록 위치

- 변경 전 기준선: [`../docs/improvement/verification-baseline.md`](../docs/improvement/verification-baseline.md)
- 2단계 결과: [`../docs/improvement/phase-2-results.md`](../docs/improvement/phase-2-results.md)
- 재현·원인·해결: [`../docs/improvement/troubleshooting.md`](../docs/improvement/troubleshooting.md)
- 미완료 항목: [`../roadmap/README.md`](../roadmap/README.md)
