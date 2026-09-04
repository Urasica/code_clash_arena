# M4 — 네트워크 계층 분리와 ARM 배포

- 상태: IN_PROGRESS
- 선행: M3 DONE
- 범위 갱신일: 2026-09-04
- 범위 정리 브랜치: `codex/m4-subnet-plan`
- 현재 단계: NET-01 구성과 DATA-03 운영 데이터 경계의 로컬 구현·검증. 실제 OCI plan/apply·접근·Object Storage 복구 검증과 후속 배포 기능은 아직 수행하지 않았다.

## 목표와 범위

Public/Private Subnet, NSG, NAT Gateway, 내부 통신, 데이터 계층 접근 제한을 독립적인 네트워크 설계로 정의한다. 서브넷은 공개 진입점과 비공개 서비스의 역할·접근 정책에 따라 구분하며, 특정 공급자·VM 대수·사양·프로세스 배치와 일대일로 묶지 않는다.

개발·로드맵·PR은 저장소 루트에서 통합 관리하되 프론트와 백엔드의 배포 단위·자동 CI/CD·롤백은 분리한다. DEV-01은 완료 상태를 유지하고 native ARM 배포 게이트를 추가한다. 서버리스와 실행 워커 전용 VM 분리는 이번 범위에 포함하지 않는다.

논리 네트워크 설계, 실제 환경의 적용 상태, CI/CD 구현 상태는 각각 구분하여 기록한다. 설계에 Public/Private 영역을 표시했다는 이유만으로 실제 서브넷·NSG가 구성되었다고 설명하지 않는다.

## 논리 네트워크 구분

| 영역 | 역할 | 접근 원칙 |
| --- | --- | --- |
| Public Subnet / Edge | 외부 HTTPS 진입점, 정적 프론트 제공, API·WebSocket 프록시 | 외부 요청을 수신하고 허용된 Private API로만 전달 |
| Private Subnet / Application | 인증·REST·STOMP·매칭·실행 조정 | 외부 직접 진입 없이 Edge에서 허용한 API 요청 수신 |
| Private 영역의 데이터 계층 | MySQL·Redis | 허용된 애플리케이션 접근만 수신. 인터넷·Edge의 직접 접근 차단 |
| Private 영역의 외부 연결 | registry·갱신·인증 공급자 등 필요한 outbound | NAT Gateway를 통한 내부 시작 연결과 응답만 허용 |

데이터 계층은 Private 영역 안의 별도 접근 경계로 정의하며, 이 표가 곧 세 번째 서브넷이나 별도 DB VM 생성을 의미하지 않는다. 각 영역을 실제 리소스·VNIC·서브넷에 연결하는 작업은 배포 환경별 매핑으로 관리한다.

기존에 요청한 OCI ARM VM 두 대(각 1 OCPU / 6 GB)와 프로세스 배치 초안은 별도 [배포 프로필](M4-deployment-profile.md)에 보존한다. 이 프로필은 논리 네트워크의 정의가 아니며, 실제 적용 여부도 해당 문서에서 구분한다.

## 작업과 완료 조건

| ID | 상태 | 작업 | 완료 조건 |
| --- | --- | --- | --- |
| DEV-01 | DONE | CRA에서 유지보수되는 build/test 도구로 전환 유지 | DEP-01의 Vite/Vitest, env·bundle·test·브라우저 계약 유지 |
| NET-01 | IN_PROGRESS | 배포 프로필과 독립적인 Public/Private Subnet·NSG·IGW/NAT·관리 접근 설계, 환경별 IaC | 논리 역할·접근 행렬·라우팅을 정의하고 승인된 검증 환경에서 허용/차단을 입증. 실제 배포 매핑과 미적용 항목을 구분 |
| DATA-03 | IN_PROGRESS | 데이터 계층의 DB·Redis 비공개화, 권한 분리, 외부 백업·복원 | 인터넷과 Edge에서 DB·Redis 직접 연결 불가. 앱 접근은 정상. 호스트 밖의 백업으로 복원 성공 |
| ARM-01 | READY | 배포 게이트에 native Linux ARM64 실행 검증 추가 | 배포할 artifact의 ARM 호환성, 다섯 언어 실행·보안 corpus, 실제 DB/Redis 통합·브라우저 계약 통과. 누락·skip은 배포 차단 |
| OPS-02 | READY | 루트 모노레포 관리 + 컴포넌트별 자동 CI/CD·공급망 검증 | 변경 영향에 맞는 배포만 실행. lockfile build, SBOM·image scan·signature, digest 기반 승격, migration dry-run, 단계적 배포·롤백 |
| REL-02 | READY | 승인된 배포 프로필에서 실제 배포·연결·장애 복구 검증 | HTTPS/WSS, 쿠키 인증·AI/PvP·재연결·rollback 증거 확보. 논리 네트워크와 실제 리소스의 대응 및 미적용 항목을 명시 |

기존 OPS-02의 공급망·migration·rollback 목표를 삭제하지 않고 독립 배포에 적용한다. ARM-01을 기록했다는 이유로 현재 Release Gate에 ARM job이 이미 있다고 해석하지 않는다.

## NET-01: 네트워크와 데이터 접근 경계

논리 요청 경로는 `Browser → Public Edge → Private API → 데이터 계층`이다. 배포 시 브라우저에는 하나의 서비스 origin을 제공하고 `/api`, `/ws-stomp`, 인증 시작·callback 경로를 Edge에서 API로 전달한다. 필요한 HTTP 경로와 SockJS fallback도 포함하며 HTTP와 WSS, 쿠키·Origin·forwarded header·OAuth redirect의 동작을 함께 검증한다.

| 출발지 | 목적지 | 정책 |
| --- | --- | --- |
| 인터넷 | Edge | HTTPS 허용. HTTP는 redirect/인증서 발급에 필요한 경우만 허용 |
| Edge NSG | Application NSG | 합의한 API 포트만 허용. 내부 private IP/DNS로 통신 |
| 인터넷 | Private Application 리소스 | 직접 inbound 불가, public IP 없음 |
| Edge 또는 인터넷 | MySQL·Redis·management·Docker API | 직접 연결 불가 |
| 허용된 Application | 데이터 계층의 MySQL·Redis | 인증된 앱 계정과 필요한 연결만 허용. 배치별 네트워크·호스트 제어는 별도 매핑 |
| Private 리소스 | 필요한 외부 서비스 | NAT 경유 outbound. registry·OS 갱신·인증 공급자 등 목적을 기록 |
| player container | 외부·VCN·metadata·DB/Redis | 기존 `network=none` 유지 |
| 승인된 배포·관리 세션 | 대상 VM의 관리 포트 | OCI Bastion 단기 세션 기본안. 별도 상시 public SSH 개방 없음 |

- OCI 구현 시 Public route table은 `0.0.0.0/0 → Internet Gateway`, Private route table은 `0.0.0.0/0 → NAT Gateway`에 매핑한다. VCN 내부 트래픽은 내부 경로를 사용하며 NAT를 거치지 않는다. IPv6를 사용하면 동등한 접근 제한을 따로 검증한다. [OCI NAT 동작](https://docs.oracle.com/en-us/iaas/Content/Network/Tasks/NATgateway.htm)
- NAT는 외부에서 시작하는 SSH·배포 연결을 수신하지 않는다. NAT 경로와 GitHub에서 private VM으로 배포하는 경로를 혼동하지 않는다.
- NSG와 subnet security list의 허용 규칙은 합쳐져 적용되므로 기본 security list의 광범위한 허용이 NSG 설계를 무력화하지 않는지 검사한다. [OCI security rule 결합](https://docs.oracle.com/en-us/iaas/Content/Network/Concepts/securityrules.htm)
- NSG는 VNIC/리소스 경계다. 애플리케이션과 데이터 계층이 다른 리소스라면 실제 VNIC와 NSG를 매핑하고 필요한 DB 연결만 허용한다. 같은 호스트라면 bind·port publish·전용 container network·계정·파일 권한으로 접근을 제한한다. 호스트 내부 프로세스나 Docker network의 분리를 클라우드 서브넷 분할로 표시하지 않는다.
- 내부 private 통신이 곧 암호화 통신은 아니다. NET-01에서 TLS 종료 지점과 Edge→API 암호화 여부를 결정하고 근거·인증서 검증 방식을 기록한다. 두 경우 모두 외부 HTTPS/WSS와 기존 애플리케이션 인증은 유지한다.
- 실행 엔진과 DB·Redis가 같은 호스트에 배치되면 sandbox 탈출의 영향 범위가 공유됨을 배포 프로필에 기록한다. M1~M3의 UID·capability·읽기 전용 root·시간/CPU/메모리/PID 제한을 낮추지 않는다.
- 논리 영역별로 실제 리소스·VNIC·서브넷·NSG·route·검증 결과를 연결한다. 검증 환경과 서비스 배포 환경이 다르면 증거를 따로 보관하며, 실제 적용하지 않은 경계는 미적용으로 표시한다.

## OPS-02: 루트 관리와 독립 CI/CD

### 저장소와 배포 단위

하나의 repository, `main`, roadmap, PR 정책을 유지한다. frontend 전용 장기 branch나 별도 repository를 만들지 않는다. 여기서 루트 관리는 **저장소 루트의 통합 관리**를 뜻하며 모든 서비스를 Linux root 사용자로 운영한다는 뜻이 아니다.

다음은 구현 경계다. 현재 `infra/oci/`와 기존 PR Gate의 네트워크 mock 검사만 추가했으며, delivery·deploy workflow와 서비스 배포 디렉터리는 아직 구현하지 않았다.

| 경로 | 역할 |
| --- | --- |
| `frontend/`, `backend/code/`, `engine/` | 기존 소스 경계 유지 |
| `infra/oci/` | 논리 네트워크를 OCI 리소스로 연결하는 환경별 IaC, secret을 제외한 변수 예제 |
| `deploy/frontend/`, `deploy/backend/` | 각 컴포넌트의 설치·상태 점검·승격·복구 정의. 대상 호스트는 배포 프로필에서 선택 |
| `.github/workflows/delivery.yml` | 변경 영향과 정확한 source SHA 판정, 각 배포 workflow 호출 |
| `.github/workflows/deploy-frontend.yml`, `deploy-backend.yml` | `workflow_call` 기반의 독립 build/gate/deploy 경로 |
| `.github/workflows/`의 공통 reusable workflow | ARM·공급망·artifact 검증 등 중복 절차 공유 |

workflow 파일은 repository 루트의 `.github/workflows` 바로 아래에 둔다. 컴포넌트 디렉터리 아래 별도 `.github/workflows`를 두는 방식은 사용하지 않는다. [GitHub reusable workflow](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows)

### 변경 영향과 트리거

| 변경 범위 | 기본 영향 |
| --- | --- |
| `frontend/**`, `deploy/frontend/**` | frontend build/gate/deploy |
| `backend/code/**`, `engine/**`, `deploy/backend/**` | backend와 engine의 호환 bundle build/gate/deploy |
| 공통 계약·공통 build/gate·영향 판정 로직 | 양쪽을 검증하고 영향 있는 단위를 배포. 초기에는 보수적으로 양쪽 영향으로 처리 |
| `infra/oci/**`, 공통 runtime/Compose 설정 | 인프라·양쪽 영향 검토. 승인된 infra apply 이후 필요한 배포만 진행 |
| `doc/**`, `docs/**`, `roadmap/**` 등 순수 문서 | 문서 검사만 수행, 운영 배포 없음 |

1. Ready PR의 기존 Ubuntu/Windows 빠른 회귀와 `main` ruleset은 유지한다. PR이나 fork에는 운영 자격증명을 전달하지 않는다.
2. `main` 병합 뒤 별도 delivery workflow가 변경된 배포 단위를 자동 처리한다. 기존 PR Gate를 `main` push에 중복 연결하지 않는다. 인프라 생성/apply는 일반 코드 push로 자동 수행하지 않는다.
3. 루트 orchestrator는 실행할 변경 집합과 source SHA를 고정하고, 컴포넌트별 마지막 성공 배포와 누락된 변경도 반영한다. 직전 한 commit의 파일 목록만 검사해 이전 실패 배포를 영구 누락하지 않는다. 수동 재시도·명시적 rollback 경로를 제공한다.
4. 서로 다른 CI 단계를 통과한 artifact를 VM에서 다시 build하지 않는다. source SHA, artifact hash/image digest, target architecture, engine policy version을 release manifest에 기록하고 테스트한 동일 산출물을 승격한다. 재시도 때도 같은 manifest를 쓴다.
5. frontend와 backend가 서로 다른 source SHA에서 배포될 수 있음을 전제로 실제 배포 버전 쌍을 기록한다. REST/STOMP·결과 계약은 이전 버전과 호환되게 변경하고, 동시에 변경할 때의 순서·검증·rollback을 정의한다. engine-only 변경도 backend 영향으로 취급한다.
6. 환경·권한·동시성은 `production-frontend`와 `production-backend`로 분리한다. 배포 중인 migration/승격을 새 push가 강제 취소하지 않도록 직렬화하고, 오래된 실행이 최신 배포를 덮어쓰는 것을 막는다. 공통 인프라 변경과 동시 배포 간 경합도 제어한다.

### 게이트와 배포 접근

- 현재 Release Gate의 수동·`v*` tag 진입점과 기존 실제 인프라 검증을 보존하면서 공통 gate를 재사용할 수 있게 구성한다. 자동 CD에도 ARM 및 공급망 통과를 필수 의존성으로 걸고, 단순 `main` push만으로 배포 성공으로 처리하지 않는다.
- 정확한 merge SHA 또는 명시한 release SHA에서 artifact를 만들고 검증한다. 이전 PR head의 성공 상태를 다른 SHA의 배포 증거로 재사용하지 않는다.
- 배포 접근 기본안은 OCI Bastion의 제한된 수명·대상 세션이다. IAM, target agent/plugin 또는 SSH forwarding 방식, host key 검증, runner 발신 IP allowlist, 세션 종료를 NET-01/OPS-02에서 실증한다. `0.0.0.0/0` 관리 접근이나 SSH 검증 해제로 우회하지 않는다. [OCI Bastion](https://docs.oracle.com/en-us/iaas/Content/Bastion/Concepts/bastionoverview.htm)
- 운영 VM에 범용 self-hosted Actions runner를 설치해 PR/build/test를 실행하지 않는다. cloud-hosted 일회용 runner와 운영 배포 권한을 분리한다. Bastion 접근 방식이 계정 정책에 맞지 않으면 별도 설계 결정 후 제한된 pull 배포 방식 등을 검토한다.
- 배포 계정은 필요한 배포 명령만 수행하도록 제한한다. registry/OCI/SSH/DB 자격증명은 해당 environment 또는 외부 secret store에만 두며 `.env`, Terraform state, private key, 제출 코드가 artifact·로그·저장소에 유출되지 않게 한다. DB migration 계정과 애플리케이션 계정의 권한을 구분한다.

## ARM-01: 실제 ARM 실행을 배포 조건으로 추가

현재 `.github/workflows/release-gate.yml`은 `ubuntu-latest` 한 환경이다. 기존 x86/Linux·Windows 지원을 없애는 대신, 배포 대상인 `linux/arm64` 검증 경로를 추가한다.

1. native runner 기본 후보는 `ubuntu-24.04-arm`이다. 현재 public repository에서 사용할 수 있는 표준 ARM64 runner이며, OCI 운영 VM을 CI runner로 소비하지 않는다. 실제 job에서도 `uname -m`, Docker architecture를 확인한다. [GitHub runner 지원표](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
2. frontend는 lockfile 설치·unit/build를 검사하고, 배포할 proxy/runtime의 ARM 동작과 정적 artifact를 확인한다. backend는 Java build/test/package와 runtime 동작을 검사한다. host binary·base image·MySQL·Redis·Toxiproxy 등 테스트 도구까지 ARM 호환성을 확인한다.
3. engine은 ARM image로 init/compile/run, Python·Java·C·C++·JavaScript 실행, 기존 보안 corpus를 실제 수행한다. Docker나 image가 없어 integration이 skip된 실행은 합격으로 인정하지 않는다. QEMU build 성공이나 multi-arch manifest 존재만으로 native 실행 검증을 대체하지 않는다.
4. 실제 MySQL/Flyway·Redis/Testcontainers 통합, AI/PvP·disconnect 결과와 digest/policy 저장을 검사한다. 배포 artifact를 사용하는 browser/auth/HTTP/WSS smoke를 포함하며 frontend-only와 backend-only 변경에도 현재 상대 컴포넌트와의 호환성을 검증한다. 외부 Google credential을 PR CI에 전달하지 않는다.
5. 통과한 artifact digest/hash와 gate evidence를 연결한다. 해당 배포 단위의 ARM gate가 실패·취소·누락·skip이면 CD를 차단하고 x86 gate 성공으로 대체하지 않는다. 초기 배포와 공통 경계 변경은 양쪽 전체 gate를 요구한다.
6. native hosted runner 검증과 실제 배포 환경 검증은 구분한다. REL-02에서 배포 대상의 Docker·OS·네트워크와 사용자 기능 계약을 다시 확인한다.

기존 필수 check 이름 `Fast regression (ubuntu-latest)`, `Fast regression (windows-latest)`은 변경하지 않는다. 새로운 ARM 검사를 PR required check로도 승격하려면 workflow와 ruleset을 일치시키는 별도 승인·검증이 필요하다. 이번 요구의 최소 조건은 **ARM 검증 없이는 배포 불가**이다.

## DATA-03·REL-02: 운영 증거

- 개발용 [`../compose.yaml`](../compose.yaml)의 공개 port와 개발 credential을 운영에 그대로 복사하지 않는다. DB·Redis·management port·Docker socket/API의 실제 노출을 호스트와 외부 양쪽에서 검사한다.
- 배포 대상별 재부팅·프로세스 재시작·네트워크 단절 때의 결과와 복구 절차를 기록한다. 실제 프로세스 배치에 따라 영향을 받는 기능을 명시한다.
- NAT 차단으로 registry/OS/OAuth 등 필요한 outbound가 실패하는지와 복구를 검사한다. 이미 열린 내부 API 연결까지 NAT 의존으로 잘못 구성하지 않았는지 확인한다.
- 배포 전 DB 백업·migration dry-run을 수행하고, 새 앱 버전의 schema 호환성·롤백 허용 범위를 확인한다. 이미 적용된 DB 변경을 무조건 역실행하지 않는다. 복원 작업은 별도의 절차와 데이터 손실 범위를 가진다.
- 백업은 호스트 밖에 저장하고 실제 restore를 검증한다. 재생성 절차와 데이터 보존 범위를 남기고, 자동 failover나 무중단은 실제 구현·검증한 경우에만 표시한다.
- 엔진의 CPU·memory·PID·출력·timeout 보안 제한과 실제 container 종료·고아 workspace 정리를 검증한다. 기존 보안 경계를 완화하지 않는다.
- 배포 시 실행 중인 매치의 drain, migration 순서, 점검 창, 실패 시 rollback 절차를 명시한다.
- 결과는 익명화한 네트워크 허용/차단 증거, 논리 설계와 실제 적용의 대응표, ARM gate 기록, 서비스별 배포·rollback 기록, 복원 증거로 남긴다. 구현 후 현재 설계는 `doc/`, 완료한 실험은 `docs/`에 반영하고 미래 계획과 섞지 않는다.

## 진행 순서와 착수 전 확인

1. NET-01: 독립적인 논리 네트워크·접근 행렬 정의, 환경별 매핑과 IaC plan, 적용할 환경의 quota·권한·비용 확인.
2. DATA-03과 ARM-01: 데이터 비공개·백업 기준, ARM artifact와 native gate 준비.
3. OPS-02: 루트 변경 감지·컴포넌트별 build/gate/배포·공급망·rollback 연결.
4. REL-02: 승인된 배포 프로필에 따라 적용하고 연결·사용자 흐름·장애 복구 검증. 네트워크 검증 환경과의 대응을 기록하고 M4 완료 판정.

리소스 사양·계정·region·도메인·실제 호스트 배치는 [배포 프로필](M4-deployment-profile.md)에서 관리한다. 이 값들이 달라져도 Public/Private 구분과 접근 정책의 정의는 유지하며, 적용 방식과 검증 증거만 환경별로 갱신한다.

인프라 적용과 배포의 권한·비용 경계는 각 구현 작업에서 다시 확인한다. 코드 변경만으로 실제 리소스 생성, 원격 workflow 실행, repository settings 변경, 운영 배포 또는 push를 수행하지 않는다.

## 작업·푸시 묶음

- 1차 묶음: NET-01 → DATA-03 → ARM-01. 네트워크·데이터 경계와 ARM 게이트를 준비·검증한 시점에 검토한다.
- 2차 묶음: OPS-02 → REL-02. 독립 배포 연결과 승인 환경 검증을 모아 검토한다.
- 세부 작업마다 push하지 않는다. 검증된 작업 단위로 로컬 변경/커밋을 모으고, 사용자가 요청한 묶음 단위에서만 push한다. 실제 환경 미검증 항목은 별도로 남기며 push를 완료 증거로 사용하지 않는다.

## NET-01 진행 기록 — 2026-09-04

- 근거: 논리 네트워크 문서는 있었지만 적용 가능한 IaC·구성 회귀가 없었다. 배포 사양과 독립적으로 재현할 네트워크 root가 필요했다.
- 구현: [`infra/oci/network`](../infra/oci/network/main.tf)에 VCN·두 subnet·IGW/NAT·route·DHCP·역할 NSG·단기 Bastion을 정의했다. VM/이미지/사양·VNIC 연결은 포함하지 않는다.
- 경계: 기본 data 정책은 host-local로 DB port를 열지 않는다. 별도 VNIC 정책은 명시적으로 선택하며 앱 NSG만 3306/6379 접근을 허용한다. 내부 API는 인증서 검증 TLS/8443 계약으로 정하고 실제 proxy 배포는 OPS-02에 남겼다.
- 검증: Windows 로컬 Terraform fmt/validate, readonly lockfile init 및 mock 11건 통과. 공식 Windows/Linux/ARM provider checksum을 잠금 파일에 기록했다. 기존 PR Gate의 Ubuntu/Windows 필수 job 이름·트리거를 유지하며 같은 검사를 추가했고 actionlint 문법 검사도 통과했다. 원격 실행·native ARM 앱 검증은 아직 하지 않았다.
- 현재 코드 설명·실환경 검증 행렬: [`doc/10-network-infrastructure.md`](../doc/10-network-infrastructure.md), 실행 안내: [`infra/oci/README.md`](../infra/oci/README.md).
- 남은 조건: 승인된 계정·compartment·region·관리 클라이언트 IP, quota·비용·IAM·state backend 결정, 실제 plan/apply와 VNIC 매핑, 허용/차단·TLS·Bastion·NAT 검증. 네트워크 검증 환경과 서비스 배포 환경을 구분해 기록한다.
- 상태: 로컬 구성 준비, 실환경 검증 대기. `DONE`이 아니며 완료 커밋·push 없음.

## DATA-03 진행 기록 — 2026-09-04

- 근거: 개발용 Compose의 공개 port·공용 앱/migration 권한·Redis volume을 운영에 그대로 사용할 수 없고, 호스트 장애와 분리된 복원 증거가 필요했다.
- 구현: [`deploy/backend/data`](../deploy/backend/data/README.md)에 운영 전용 MySQL·Redis Compose와 제어 도구를 추가했다. 두 port는 IPv4 loopback에만 publish하고 internal/non-attachable network만 사용한다. MySQL은 영속 volume, Redis는 volume/AOF/RDB 없는 빈 상태 재시작으로 구분했다.
- 권한: MySQL app·migration·backup·health 계정을 분리하고 앱 계정에서 DDL을 제거했다. Redis default 계정은 끄고 앱이 실제 사용하는 key prefix와 명령만 ACL에 허용했다. 앱에는 별도 `backend/` 경로의 app credential만 전달하고 DB root·migration·backup secret은 전달하지 않는다. `prod` 기동은 loopback DB/Redis, 전용 앱 계정, MySQL TLS, Flyway 비활성화, management loopback을 검증한다.
- 백업/복원: `mysqldump` 출력을 평문 파일로 만들지 않고 `age` public recipient로 바로 암호화한다. OCI upload 전에 Tokyo region과 비공개 bucket·승인 compartment를 확인하며 instance principal, checksum 검증, overwrite 거부를 사용한다. 복원은 새 `cca-restore-*` project와 빈 schema에서만 허용하고 Redis 과거 상태는 복원하지 않는다.
- 로컬 검증: Python 단위 7건, 운영 Compose 구성, 실제 MySQL 역할 거부/허용, Redis key·관리 명령 거부, 암호화 snapshot과 별도 MySQL 복원, Redis 빈 재시작을 통과했다. 백엔드 빠른 회귀 `104 pass / 6 opt-in skip`과 실제 MySQL·Redis·engine 통합 6종도 통과했다. 일회용 container·volume은 검사 종료 후 남지 않았다.
- 게이트: DATA 단위 검사를 기존 Ubuntu/Windows PR Gate에, 일회용 백업·복원 검사를 Release Gate에 연결했다. 변경한 원격 workflow는 아직 실행하지 않았다.
- 남은 조건: ARM-01 이후 실제 Private VM에서 앱 연결과 Edge/인터넷 차단을 같은 시점의 대조군으로 확인한다. Tokyo Object Storage에 올린 호스트 밖 암호문을 새 복구 환경으로 내려받아 schema·행·민감 payload·Flyway 계약을 확인해야 한다.
- 상태: 구현 및 로컬 검증 완료, 실제 VM/Object Storage 검증 대기. `DONE`이 아니며 완료 커밋·push 없음.
