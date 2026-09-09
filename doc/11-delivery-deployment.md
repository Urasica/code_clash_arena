# OCI AMD64·무료 DNS HTTPS 독립 배포

이 문서는 루트 모노레포에서 frontend와 backend를 독립적으로 빌드·검증하고 OCI DevOps로 두 Oracle Linux 9.8 AMD64 VM에 전달하는 OPS-02 구현을 설명한다. 외부 진입점은 무료 DNS 호스트명의 HTTPS/WSS이며 실제 OCI pipeline과 VM에서 아직 실행하지 않은 항목은 마지막 절에 분리했다.

## 배포 단위와 흐름

```text
main push / manual retry
  → 마지막 성공 component SHA부터 변경 영향 계산
  → exact source SHA Release Contract (amd64 + native arm64 이식성)
  → linux/amd64 backend bundle + engine 또는 frontend bundle build
  → SBOM + high severity scan + GitHub attestation
  → immutable OCI Generic Artifact version = source SHA
  → OCI DevOps Instance Group stage
  → VM manifest 검증 + atomic current/previous 전환 + smoke check
  → GitHub production-backend / production-frontend deployment 기록
```

저장소와 workflow는 루트에서 관리한다. 운영 환경과 직렬화 경계는 `production-backend`, `production-frontend`로 나뉜다. 양쪽 변경이면 backend가 먼저 성공한 뒤 frontend를 배포한다. 문서만 바뀌면 운영 배포는 하지 않는다.

[`../.github/workflows/delivery.yml`](../.github/workflows/delivery.yml)은 각 component의 마지막 **성공** GitHub Deployment SHA부터 현재 SHA까지 비교한다. 실패한 실행은 기준점을 전진시키지 않으므로 다음 실행에서 누락된 변경을 다시 포함한다. 분류하지 못한 경로와 공통 workflow·인프라 경로는 양쪽 영향으로 닫힌다. 배포 직전 `origin/main`을 다시 확인해 대기 중이던 오래된 실행이 새 release를 덮어쓰지 못하게 한다.

기존 `Release Gate` 수동·`v*` tag 진입점은 유지하고, 내부 계약은 [`../.github/workflows/release-contract.yml`](../.github/workflows/release-contract.yml)로 분리했다. 자동 delivery도 배포할 정확한 SHA의 amd64/native arm64 전체 계약을 통과해야 한다. PR 필수 check 이름 `Fast regression (ubuntu-latest)`, `Fast regression (windows-latest)`은 바꾸지 않았다.

## 불변 산출물과 공급망

| 단위 | OCI Generic Artifact | 포함 내용 |
| --- | --- | --- |
| frontend | `code-clash-arena/frontend/bundle.zip` | Vite build, 설치·검증 도구, release manifest |
| backend | `code-clash-arena/backend/bundle.zip` | 테스트한 JAR, migration, DATA-03 도구, 설치·검증 도구, engine identity |
| 배포 사양 | component별 `deployment.yaml` | OCI Instance Group 순서와 실패 rollback |
| 명시 rollback | component별 `rollback.yaml` | VM의 검증된 `previous` release 재활성화 |
| engine | OCIR `${repository}:${source_sha}` | AMD64 배포 image, 다섯 언어 계약과 다중 architecture 회귀 통과 |

Generic Artifact version은 항상 40자리 source SHA다. 재시도에서 같은 path/version이 이미 있으면 OCI에서 다시 내려받아 로컬 파일과 SHA-256이 완전히 같은 경우에만 재사용한다. bundle의 [`../deploy/common/releasectl.py`](../deploy/common/releasectl.py)는 모든 파일 hash, target `linux/amd64`, source SHA, component metadata를 기록하고 symlink·추가 파일·경로 이탈·변조를 거부한다. VM은 이 bundle을 다시 build하지 않는다.

frontend는 lockfile 설치, audit, unit test와 production build를 같은 AMD64 job에서 수행하고 npm SPDX SBOM을 만든다. backend는 Maven package/test로 생성한 JAR와 AMD64 engine image를 같은 job에서 검사하고, 배포에 쓸 digest 고정 Flyway image와 migration 전체를 일회용 MySQL에 먼저 적용한다. 별도 Release Contract의 native ARM64 job은 이식성 회귀를 유지한다. Syft/Grype action은 commit SHA로 고정하며 JAR·engine SBOM과 high 이상 취약점 gate를 적용한다. bundle과 engine digest에는 GitHub Sigstore attestation을 남긴다. 장기 OCI API key와 OCIR token은 artifact나 로그에 포함하지 않는다.

backend manifest에는 digest가 고정된 engine·migration·MySQL·Redis image와 `engine_policy_version`을 기록한다. systemd와 DATA-03 기동이 읽는 `runtime.env`가 이 metadata와 정확히 다르면 VM 배포가 중단된다.

## 두 VM 실행 경계

| VM | 공개 listener | host-local listener | 책임 |
| --- | --- | --- | --- |
| Edge / Public Subnet | TCP 443, TCP 80 HTTPS redirect/인증서 발급 | 없음 | 무료 DNS 호스트명에서 React 정적 파일, 허용된 REST·STOMP·OAuth 경로만 Private VM으로 proxy |
| Application / Private Subnet | Edge NSG에서만 TCP 8443 | Spring 127.0.0.1:8080, management 127.0.0.1:8081, MySQL 127.0.0.1:3306, Redis 127.0.0.1:6379 | 내부 TLS 종료, Spring Boot, MySQL·Redis, Docker engine |

Edge Nginx는 client가 넣은 forwarding header를 버리고 다시 만든다. `app.cca.internal:8443`의 SAN과 private CA를 검증하며 평문 fallback을 두지 않는다. Application Nginx는 forwarding 계약과 허용된 REST·STOMP·OAuth 경로만 Spring loopback으로 전달한다. `/actuator`는 두 proxy 모두 외부에 내보내지 않는다. management, DB, Redis와 Docker TCP socket은 NSG에 열지 않는다.

Spring service는 `prod`, application/management loopback, 외부 `backend.env`, DATA-03의 app 전용 configtree, release의 digest identity를 사용한다. Docker socket이 있는 같은 호스트 배치의 보안 영향 범위는 그대로 남으므로 Docker TCP API를 열지 않고 기존 engine CPU·memory·PID·timeout·read-only 제한을 유지한다.

## VM 1회 준비

OCI Instance Group 배포는 별도 상주 CI runner가 아니라 Oracle Cloud Agent의 **Compute Instance Run Command plugin**을 사용한다. 두 인스턴스에서 plugin을 활성화하고 `Running`이 될 때까지 확인한다. 운영 VM에 GitHub Actions self-hosted runner를 설치하지 않는다.

공통 기준은 `x86_64`, Oracle Linux 9.8, Nginx, `/usr/bin/python3.11` 3.11.x, `openssl`, `policycoreutils-python-utils`, firewalld다. OS가 사용하는 Python 3.9.25는 교체하지 않는다. Application VM에는 Java 21, Docker Engine/Compose plugin, `age`, OCI CLI를 추가한다. 필요한 패키지와 인증서 설치는 이미지 또는 승인된 관리 절차로 먼저 수행하며 delivery workflow가 임의로 OS package를 갱신하지 않는다. bootstrap은 architecture, `ID=ol`, OS major 9, 정확한 Python 3.11 minor를 확인하고 다르면 중단한다.

인증서·secret을 준비한 후 저장소의 bootstrap을 한 번 실행한다.

```text
# Edge VM prerequisites: <public-hostname>은 무료 DNS에 만든 실제 이름
/etc/pki/code-clash-arena/public/fullchain.pem
/etc/pki/code-clash-arena/public/private.key
/etc/pki/code-clash-arena/internal/ca.pem
app.cca.internal -> 10.0.1.0/24 Application VM private IP
sudo CCA_PUBLIC_ORIGIN=https://<public-hostname> \
  CCA_PYTHON_BIN=/usr/bin/python3.11 \
  bash deploy/host/provision.sh edge

# Application VM prerequisites
/etc/pki/code-clash-arena/application/fullchain.pem  # SAN app.cca.internal
/etc/pki/code-clash-arena/application/private.key
/etc/code-clash-arena/backend.env                    # 저장소 밖, root:cca 0640
/etc/code-clash-arena/data-secrets                   # DATA-03 init-secrets 결과
sudo CCA_PYTHON_BIN=/usr/bin/python3.11 \
  bash deploy/host/provision.sh application
```

먼저 무료 DNS에 `<public-hostname> A <Edge public IPv4>`를 만들고 전파를 확인한다. 그 호스트명으로 공개 CA 인증서를 발급해 Edge 경로에 배치한다. `provision.sh edge`는 인증서 SAN과 `CCA_PUBLIC_ORIGIN` 호스트명이 일치하지 않으면 중단하며, Nginx `server_name`도 같은 값으로 렌더링한다. `PUBLIC_ORIGIN`에는 IP, localhost, 명시적 port, path나 query를 사용할 수 없다. OAuth를 사용하면 공급자 redirect URI도 같은 HTTPS origin의 실제 callback 경로로 등록한다.

Application 환경 파일은 [`../deploy/backend/application/backend.env.example`](../deploy/backend/application/backend.env.example)의 이름만 참고해 별도 secret 전달 경로에서 작성한다. JWT, 데이터 암호화 key와 OAuth secret의 실제 값은 저장소·GitHub variable·OCI deployment argument에 넣지 않는다. DATA-03 secret 생성 뒤 `provision.sh application`이 앱용 하위 네 파일만 `cca`가 읽을 수 있게 정리한다. migration·backup 계정은 root로 실행되는 승인된 OCI Run Command 단계에서만 읽으며 Spring process에는 mount하지 않는다.

bootstrap은 SELinux label, host firewall, release/state 디렉터리, Nginx와 systemd 파일을 설치한다. Edge의 `ocarun` sudo는 `nginx -t`와 Nginx reload 두 명령만 허용한다. Application 배포는 OCI Run Command의 `root` step을 사용하므로 해당 DevOps pipeline 실행 권한 자체를 운영 변경 권한으로 취급하고 IAM과 GitHub environment approval을 제한한다. 일반 inbound SSH 배포 권한은 필요 없다. Bastion은 별도의 제한된 관리·검증 세션에만 사용한다.

## OCI DevOps 준비

Tokyo의 immutable Generic Artifact repository, OCIR repository, Edge/Application instance group environment를 만든다. 각 forward pipeline은 deployment spec artifact와 bundle artifact 두 개를 같은 source SHA version으로 참조한다.

| pipeline | deployment spec path | 추가 artifact path | 대상 |
| --- | --- | --- | --- |
| frontend deploy | `code-clash-arena/frontend/deployment.yaml` | `code-clash-arena/frontend/bundle.zip` | Edge group |
| backend deploy | `code-clash-arena/backend/deployment.yaml` | `code-clash-arena/backend/bundle.zip` | Application group |
| frontend rollback | `code-clash-arena/frontend/rollback.yaml` | 없음 | Edge group |
| backend rollback | `code-clash-arena/backend/rollback.yaml` | 없음 | Application group |

모든 artifact version은 pipeline parameter `${artifactVersion}`로 매핑한다. source SHA와 공개 origin, backup 위치, OCIR Vault secret OCID도 문서의 deployment spec placeholder와 같은 이름으로 pipeline parameter를 선언한다. stage rollout은 VM 한 대 기준 count 1로 둔다. forward spec의 `onFailure`가 host의 `previous` release를 복구하므로 OCI stage의 별도 자동 rollback은 중복 활성화하지 않는다.

DevOps dynamic group에는 해당 instance environment Run Command, Artifact Registry read, backend의 OCIR/Vault secret read만 허용한다. Application VM instance principal에는 승인된 비공개 backup bucket inspect/read/write만 허용한다. GitHub가 쓰는 OCI API principal에는 두 deploy pipeline의 create/get과 대상 Generic Artifact upload/download 검증만 허용한다. tenancy-wide 관리자 권한으로 대체하지 않는다.

## GitHub 환경 설정

두 environment 모두 필요:

| 종류 | 이름 |
| --- | --- |
| variable | `OCI_REGION=ap-tokyo-1`, `OCI_ARTIFACT_REPOSITORY_OCID` |
| secret | `OCI_TENANCY_OCID`, `OCI_USER_OCID`, `OCI_API_KEY_FINGERPRINT`, `OCI_API_PRIVATE_KEY` |

`production-frontend` 추가 variable:

- `PUBLIC_ORIGIN=https://<public-hostname>`
- `OCI_FRONTEND_DEPLOY_PIPELINE_OCID`
- `OCI_FRONTEND_ROLLBACK_PIPELINE_OCID`

`production-backend` 추가 variable:

- `OCI_BACKEND_DEPLOY_PIPELINE_OCID`, `OCI_BACKEND_ROLLBACK_PIPELINE_OCID`
- `OCI_OCIR_REGISTRY`, `OCI_OCIR_NAMESPACE`, `OCI_ENGINE_REPOSITORY`
- digest가 고정된 `MIGRATION_IMAGE`, `DATA_MYSQL_IMAGE`, `DATA_REDIS_IMAGE`, `ENGINE_POLICY_VERSION`
- `BACKUP_COMPARTMENT_ID`, `BACKUP_NAMESPACE`, `BACKUP_BUCKET`, public `BACKUP_AGE_RECIPIENT`
- Vault secret **값이 아니라 OCID**인 `OCIR_USER_VAULT_ID`, `OCIR_AUTH_VAULT_ID`

`production-backend` 추가 secret:

- `OCI_OCIR_USERNAME`, `OCI_OCIR_AUTH_TOKEN`

각 environment에 required reviewer와 main branch protection을 적용한다. fork/PR job은 environment나 secret을 사용하지 않는다.

## 배포, migration, rollback

`main` push는 영향 있는 component만 자동 실행한다. 수동 재시도는 `Production Delivery`의 `changed/frontend/backend/both` 입력을 사용하되 source SHA는 현재 `main`과 정확히 같아야 한다.

backend host 단계 순서는 다음과 같다.

1. manifest·runtime identity 검증과 engine/migration image pull
2. 불변 release 설치와 `current`, `previous` atomic link 전환
3. loopback DATA-03 구성 검증·기동
4. 분리된 migration 계정으로 Flyway `info`와 `validate`
5. `age` 암호화 MySQL snapshot을 Tokyo 비공개 Object Storage에 업로드하고 receipt 저장
6. Flyway `migrate`와 재검증
7. Spring restart, DB·Redis·engine readiness, 인증 API와 AMD64 image 확인

DB 변경은 자동으로 역실행하지 않는다. migration은 이전 앱과 호환되는 확장 우선 방식이어야 하며 비호환 정리는 별도 release에서 수행한다. 실패하면 deployment는 실패 상태를 유지하면서 app release link를 `previous`로 전환한다. 이미 적용된 schema에 대한 복구 판단은 backup receipt와 DATA-03 격리 restore 절차를 사용한다.

최초 배포에는 복구할 `previous` release가 없으므로 자동 rollback도 실패 상태로 종료된다. 따라서 backend 최초 배포와 frontend 최초 배포는 각각 점검 시간에 실행하고 smoke 검증을 완료해 첫 정상 release를 만든 뒤 후속 자동 배포를 활성화한다.

운영자가 명시적으로 되돌릴 때는 `Production Rollback` workflow에서 component를 고른다. 최근 GitHub Deployment의 서로 다른 성공 SHA 두 개를 확인한 뒤 전용 OCI rollback pipeline으로 VM의 검증된 `previous` release를 활성화한다. 새 build나 mutable tag는 만들지 않는다. 성공하면 복구된 SHA를 새 GitHub Deployment로 기록해 다음 변경 감지 기준과 실제 상태를 맞춘다.

## 현재 검증 상태

변경 분류, manifest 변조 거부, OCI 명령 구성·artifact 재사용 검사, proxy/systemd/deployment spec, frontend/backend smoke verifier는 자격증명 없는 단위 계약으로 검사한다. 기존 Release Gate 실행 `33871950779`에서 amd64와 native arm64 전체 계약이 통과했다. OPS-02의 실제 대상은 AMD64이며, 두 VM에서 OCI pipeline을 실행한 증거는 아직 없다.

OPS-02를 완료하려면 DNS A record와 공개 인증서를 준비하고 GitHub environment/OCI pipeline을 위 표대로 구성한 뒤 backend 최초 배포, frontend 배포, frontend-only/backend-only 변경, 실패 자동 복구, 명시 rollback을 수행해야 한다. REL-02에서 무료 DNS의 외부 HTTPS/WSS·쿠키 인증·AI/PvP와 Edge→Application 8443 허용, 비허용 출발지·3306·6379·8080·8081 차단, Object Storage restore까지 같은 배포 SHA와 연결해 기록한다. IP 직접 접속이나 인증서 검증 비활성화는 성공 증거로 사용하지 않는다.
