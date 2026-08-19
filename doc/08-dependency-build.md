# 빌드·의존성 설계

## 역할

이 영역은 개발·CI·실행 엔진이 사용하는 지원 버전을 하나의 호환 행렬로 관리하고, lockfile 재현성과 보안 감사를 변경 승인 조건으로 만든다. 애플리케이션 기능과 무관한 자동 major upgrade는 수행하지 않으며, 지원 종료나 보안 문제는 기능 변경과 분리해 검증한다.

## 지원 호환 행렬

| 경계 | 지원 기준 | 적용 위치 |
| --- | --- | --- |
| Backend Java | Java 21 기준 compile, 실행 21~25 | `pom.xml`의 release 21·Maven Enforcer, CI Java 21 |
| Maven | 3.9.x, Wrapper 3.9.16 | `.mvn/wrapper`, Maven Enforcer |
| Spring Boot | 3.5.16 | parent BOM이 Spring·Hibernate·Testcontainers 등 호환 버전을 관리 |
| MySQL/Flyway | MySQL 8.4, Flyway 11.20.3 | Compose·Testcontainers·`flyway-mysql`, 실제 migrate/validate smoke |
| JWT | JJWT 0.13.0 | API/impl/Jackson 세 모듈을 같은 property로 고정 |
| Frontend Node | 22.22.2 이상 또는 24.15 이상 24.x, CI는 24 | `package.json` engines, Actions setup-node |
| Frontend build/test | Vite 8.2.1, Vitest 4.1.11 | npm lockfile과 scripts |
| npm | 10~11, lockfile v3 | `package.json` engines, `npm ci` |
| Engine image | Ubuntu 24.04, Java 21, Node 24.18.0, Python 3.12 계열, GCC/G++ 13 계열 | `engine/Dockerfile`, 5개 언어 Docker 계약 |

Node 20은 지원 종료 상태라 CI 기준에서 제거했다. Node 22는 개발 환경 호환을 위해 최소 22.22.2부터 허용하고, CI와 Docker engine은 동일한 활성 LTS 계열인 Node 24를 사용한다. React 18→19 같은 major 변경은 DEP-01 범위에 포함하지 않는다.

## 의존성 소유권

- 백엔드의 일반 라이브러리와 plugin은 Spring Boot 3.5 BOM을 우선 사용한다. 개별 버전 override는 실제 지원 문제나 독립 release cadence가 있는 경우만 둔다.
- Flyway는 Boot 3.5.16 기본 11.7.2가 아닌 11.20.3을 명시한다. MySQL 8.4 지원 경고 제거가 근거이며 실제 DB 통합 gate가 override의 호환성을 보호한다.
- JJWT 세 모듈은 한 property에서 같은 0.13.0을 사용한다. token 생성·검증 테스트가 공개 API 변경을 보호한다.
- 프론트 direct dependency는 `package.json`, 전체 해석 결과는 `package-lock.json`이 원천이다. 설치는 항상 `npm ci`로 하고 lockfile을 우회한 상위 디렉터리 module 해석을 허용하지 않는다.
- `dompurify=3.4.13` override는 Monaco 0.56이 취약한 3.4.8을 exact dependency로 갖는 데 대한 임시 호환 예외다. Monaco가 수정 버전을 채택하면 direct/override 중복과 브라우저 editor 회귀를 확인한 뒤 제거한다.

## 빌드 흐름

```text
pull request
  ├─ Maven Enforcer → Java 21 compile → backend fast tests
  ├─ npm ci → full npm audit → Vitest → Vite production build
  └─ Python engine rule tests

release gate
  ├─ engine image build → five-language isolation contract
  ├─ MySQL 8.4/Flyway + Redis/Toxiproxy integration
  ├─ backend package
  └─ production frontend + Chromium user flow
```

Vite entry는 `frontend/index.html`과 `src/index.jsx`다. JSX를 포함하는 화면 파일은 `.jsx`, 순수 API·설정·계산 코드는 `.js`를 사용한다. SockJS의 CommonJS 브라우저 호환을 위해 build 시 `global`은 표준 `globalThis`로 치환한다. Vitest는 `src/**/*.test.{js,jsx}`만 수집하고 `frontend/e2e`는 Playwright가 별도로 실행한다. E2E의 사전 build는 npm이 전달한 `npm_execpath`를 현재 Node process로 호출하므로 Windows와 Linux에서 같은 CLI를 사용한다.

## 정기 갱신 정책

`.github/dependabot.yml`은 매주 월요일 KST에 Maven, npm, GitHub Actions, engine Docker를 순차 확인한다. 루트에는 Dependabot Docker가 지원하는 Dockerfile이나 Kubernetes manifest가 없으므로 Compose image는 이 설정의 대상이 아니다.

- 정기 minor/patch update는 생태계별 group 하나로 묶고 열린 version update PR을 생태계당 1개로 제한한다.
- 정기 major version PR은 생성하지 않는다. major 변경이 필요하면 별도 작업에서 migration note, 지원 행렬, 기능·Release 회귀 범위를 먼저 정의한다.
- 이 version update 제한은 취약점 해결을 위한 Dependabot security update를 차단하지 않는다.
- 생성된 PR도 일반 PR과 동일하게 Ubuntu/Windows PR Gate를 통과해야 한다.
- runtime만이 아니라 dev dependency를 포함한 전체 npm audit의 high 이상을 차단한다. 현재 전체 결과는 0건이다.
- lockfile 변경 PR은 `npm ci`, test, production build가 함께 성공해야 한다.
- MySQL, Flyway, Spring Boot 중 하나를 변경하면 실제 인프라 5종과 schema migrate/validate를 실행한다.
- engine base/runtime을 변경하면 이미지 build와 Python·Java·C·C++·JavaScript compile/run·격리 테스트 8건을 실행한다.

## 업그레이드 절차

1. 공식 지원표와 release note에서 최소/최대 runtime을 확인한다.
2. BOM, explicit override, engines, CI, Dockerfile 중 영향을 받는 경계를 함께 수정한다.
3. lockfile은 수정된 manifest에서 새로 해석하고 `npm ci`로 재현한다.
4. 변경 영역의 빠른 gate를 실행하고, DB 또는 engine 경계가 바뀌면 release 통합 gate도 실행한다.
5. 지원 행렬, 예외 사유, 트러블슈팅과 roadmap 완료 기록을 갱신한다.

## 현재 제약

- Dependabot은 버전 PR과 기존 gate를 제공하지만 SBOM, image scan/signature, immutable artifact promotion은 아직 없다. M4 `OPS-02`에서 다룬다.
- Ubuntu package 설치는 이미지 build 시점의 patch를 가져오므로 digest 기반 완전 재현성은 아직 보장하지 않는다. Node base는 명시적 patch tag를 사용한다.
- React 19, Vite/Vitest의 다음 major, Spring Boot 4는 자동 갱신 대상이 아니다.

## 공식 기준

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring Boot 3.5 managed dependency coordinates](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html)
- [Node.js release schedule](https://nodejs.org/en/about/previous-releases)
- [Flyway MySQL support](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)
- [Dependabot version update configuration](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/configure-version-updates)
- [Dependabot update 대상 제어](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/manage-your-dependency-security/controlling-dependencies-updated)
