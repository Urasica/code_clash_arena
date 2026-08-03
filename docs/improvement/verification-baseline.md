# Code Clash Arena 검증 기준선

- 실행일: 2026-08-03
- 기준 커밋: `61eec5c`
- 목적: 2단계 변경 전후에 같은 기준으로 기능 보존과 오류 수정을 확인한다.

## 1. 실제 실행 결과

| 영역 | 명령/검사 | 결과 | 기준선 판정 |
| --- | --- | --- | --- |
| Git | `git status --short --branch` | 시작 시 `main...origin/main`, 변경 없음 | PASS |
| 프론트 설치 | `npm.cmd ci --no-audit --no-fund` | 1,338 packages 설치 | PASS, 폐기 경고 다수 |
| 프론트 의존성 | `npm.cmd ls axios --depth=0` | `(empty)` | FAIL |
| Axios 해석 | `require.resolve('axios/package.json')` | `C:\Users\dlqnf\node_modules\axios`, 1.8.4 | FAIL, 상위 폴더 누출 |
| 프론트 빌드 | `npm.cmd run build` | 성공, Hook 경고 3건 | CONDITIONAL PASS |
| 프론트 테스트 | `npm.cmd test -- --watchAll=false` | suite 1개 실패, 테스트 0개 실행 | FAIL |
| Windows 백엔드 Wrapper | `.\mvnw.cmd test` | `Target[0]` null 참조 후 시작 실패 | FAIL |
| Git Bash 백엔드 | `./mvnw test` | Java 32개 컴파일, 테스트 1건 오류 | COMPILE PASS / TEST FAIL |
| 백엔드 실패 원인 | Surefire report | DataSource URL/내장 driver 없음 | FAIL |
| Python 구문 | Runner 치환 파일 제외 AST parse | 8개 성공 | PASS |
| 맵 품질 표본 | 랜덤 맵 1,000개 속성 검사 | 중복 벽 990, 도달 불가 코인 32 | FAIL |
| 최종 점수 | 50턴 결정적 게임 | 마지막 보드 P1 영역 51, 보고 점수 50 | FAIL |
| Docker 이미지 | 두 후보 이미지 inspect | 둘 다 없음 | BLOCKED |

프론트 빌드 성공은 깨끗한 머신에서 재현되지 않는다. 프로젝트에 없는 Axios를 저장소 바깥에서 찾았기 때문이다. 이를 성공 기준으로 사용하지 않는다.

## 2. 현재 테스트 자산 평가

| 파일 | 현재 내용 | 문제 |
| --- | --- | --- |
| `frontend/src/App.test.js` | `learn react` 문구 검사 | 실제 UI에 없는 CRA 기본 테스트이며 suite 로드 단계에서 실패 |
| `backend/.../CodeApplicationTests.java` | `contextLoads()` | 외부 설정 없이 실행할 테스트 프로필이 없어 실패 |
| 엔진 | 없음 | 맵, 이동, 점수, runner, timeout 회귀를 잡지 못함 |

현재 “통과하는 자동 테스트”는 0개다. 따라서 2단계 첫 커밋 전에는 리팩터링을 시작하지 않는다.

## 3. 목표 테스트 피라미드

### 3.1 빠른 단위 테스트

- 프론트: 결과 문구 판정, 타이머 상태 전이, 역할별 제출 알림, API 오류 정규화.
- 백엔드: 인증 입력 검증, 매치 결과 행렬, Redis 키/TTL 정책, 제출 상태 전이.
- 엔진: 이동, 영역/코인 점수, 마지막 턴, 벽 유일성, 코인 도달성, 잘못된 행동 처리.

### 3.2 슬라이스/통합 테스트

- Spring MVC: signup/login/me/logout, 인증 필요 API, 오류 응답.
- STOMP: CONNECT 인증, principal 기반 join/cancel/submit, 구독 권한.
- 영속화: AI/PvP/draw/crash/disconnect 결과와 리플레이 연관관계.
- Redis: 두 명 매칭, 취소, 동시 제출 1회 실행, 종료 후 키 정리.

### 3.3 Docker 계약 테스트

- Python, Java, C, C++, JavaScript 정상 코드 compile/run.
- 각 언어 문법 오류가 구조화된 오류로 반환됨.
- 무응답, 무한루프, 과다 출력, 메모리 과다 사용이 제한 시간/크기 안에서 종료됨.
- init/compile/run의 볼륨 경로와 JSON stdout 계약이 일치함.

## 4. 핵심 수동 검증 시나리오

| ID | 시나리오 | 절차 | 기대 결과 | 현재 상태 |
| --- | --- | --- | --- | --- |
| M-01 | 새 환경 기동 | 저장소 clone → 문서 순서대로 인프라/백엔드/프론트 실행 | 추가 비밀 파일 없이 예제 설정으로 기동, 비밀은 환경 변수 주입 | BLOCKED |
| A-01 | 로컬 회원가입/로그인 | 유효 입력 signup → login → me | 비밀번호 해시 저장, me 200, 브라우저 URL/본문에 토큰 없음 | BLOCKED |
| A-02 | 로그아웃 | 로그인 후 logout → me | 쿠키 만료, me 401 | BLOCKED |
| A-03 | 게스트 | guest → AI/PvP 진입 | 인증 principal 일관 유지, 재시작/만료 정책 명확 | BLOCKED |
| G-01 | AI 맵 시작 | 로그인 → Land Grab AI → 맵 생성 | 벽/코인/ID 표시, 시작점과 모든 코인 도달 가능 | BLOCKED: 이미지 없음 |
| G-02 | 언어별 컴파일 | 5개 언어 기본 템플릿 제출 | 모두 compile success | FAIL 예상: 경로, C 분기 |
| G-03 | AI 정상 대전 | 기본 코드로 easy/normal/hard 실행 | 50턴 이내 종료, 최종 점수와 마지막 보드 일치, 전적 1건 | FAIL 예상: 경로 |
| G-04 | 사용자 코드 오류 | 문법 오류/런타임 오류/무응답 코드 제출 | 제한 시간 내 오류, 서버 정상, 상대 승리 규칙 일관 | FAIL: timeout 부재 |
| R-01 | 리플레이 | 정상 대전 완료 → 재생/이전/다음 | 0~마지막 턴 상태·점수 일치 | BLOCKED |
| P-01 | PvP 매칭 | 두 사용자 join | FIFO로 한 방 생성, 각자 정확한 역할 수신 | BLOCKED |
| P-02 | 매칭 취소 | join 후 cancel/연결 종료 | 큐에서 즉시 제거, 유령 매치 없음 | BLOCKED |
| P-03 | PvP 제출 | 두 사용자가 거의 동시에 제출 | 엔진 실행과 DB 저장 정확히 1회 | 미보장 |
| P-04 | 가장 방지 | 무토큰 연결, 다른 `userId` payload, 타인 topic 구독 | 모두 거부 | FAIL 예상 |
| P-05 | 탈주 | 매치 중 한 사용자 연결 종료 | 남은 사용자만 승리, 1회 저장, 모든 키/temp 정리 | 부분 구현/미검증 |
| O-01 | 재시작 | 로그인 후 백엔드 재시작 | 문서화된 정책에 따라 세션 유지 또는 명시적 만료 | FAIL: 랜덤 JWT 키 |
| O-02 | 장애 정리 | Docker 실패/Redis 실패/DB 저장 실패 유도 | 오류 계약 반환, 중복 실행 없음, temp/Redis 정리 | 미보장 |

## 5. 2단계 품질 게이트

각 기능 또는 구조 커밋 뒤 아래 순서로 실행한다.

1. 프론트: 깨끗한 `npm ci`, 테스트 non-watch, production build. 경고는 새로 증가하지 않아야 한다.
2. 백엔드: 외부 MySQL/Redis 없이 단위·슬라이스 테스트 통과.
3. 엔진: Docker 없는 규칙 단위 테스트 통과.
4. 실행 계약을 건드린 경우: 5개 언어 Docker 계약 테스트 통과.
5. 매칭/세션을 건드린 경우: Redis/STOMP 동시성 통합 테스트 통과.
6. DB 스키마/저장을 건드린 경우: AI/PvP 결과 행렬과 리플레이 통합 테스트 통과.
7. 단계 마지막: M-01, A-01~03, G-01~04, P-01~05 수동 smoke.

최종 합격 조건:

- 자동 테스트가 모두 통과하고 placeholder 테스트가 없다.
- P0 문제 0건.
- 빌드가 저장소 상위 폴더, 개인 설정 파일, 기존 Docker 이미지에 의존하지 않는다.
- 정상/오류/취소/탈주 경로에서 Redis와 temp 잔존이 없다.
- 최종 점수, UI 결과, DB 전적이 같은 승패를 나타낸다.

## 6. 성능·장애 기준

정확한 수치는 2단계에서 CI/개발 머신 측정 후 확정한다. 우선 다음 상한을 설정 후보로 사용한다.

- 선수 1턴 응답: 200~500ms 내 정책 결정.
- 전체 컴파일: 언어별 10초 이하.
- 전체 대전: 설정된 상한 이후 프로세스와 컨테이너 강제 종료.
- stdout/stderr: 최대 바이트 수 제한 후 잘린 여부 표시.
- 컨테이너: CPU, 메모리, PID, 네트워크, 읽기 전용 rootfs 정책 명시.
- 매치 Redis TTL과 temp 보존 시간: 운영 요구에 맞게 설정하고 테스트에서 시간 정책 검증.

이 값은 보안 경계이므로 단순 튜닝 값이 아니라 설정, 문서, 테스트를 함께 변경한다.
