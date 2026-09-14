# M4 배포 프로필 — OCI AMD64·무료 DNS HTTPS

- 기준일: 2026-09-09
- 갱신일: 2026-09-10
- 상태: 사용자가 Tokyo의 Public/Private Subnet에 일반 컴퓨팅 VM 두 대와 NSG를 구성했다. 두 VM은 `x86_64`, Oracle Linux 9.8, 각각 1 OCPU / 6 GB이며 `/usr/bin/python3.11` 3.11.13을 추가 설치했다. 실제 VNIC·route·NSG 대응과 OCI DevOps 배포·연결 증거는 아직 검증하지 않았다.
- 관련 마일스톤: [M4 — 네트워크 계층 분리와 독립 배포](M4-modernization-scale.md)

이 문서는 실제 배포 환경에 종속되는 값을 관리한다. Public/Private Subnet, NSG, NAT Gateway, 내부 통신, 데이터 계층 접근 정책은 M4의 논리 네트워크 설계에서 별도로 정의한다.

NET-01의 [`infra/oci/network`](../infra/oci/network/main.tf)는 별도 검증 환경에도 적용할 수 있는 네트워크 root다. 현재 저장소에서는 mock 구성만 검증했으며 사용자가 만든 VM·VNIC가 이 IaC로 생성되었는지 또는 동일한 규칙인지 확인하지 않았다. OPS-02의 내부 API는 인증서를 검증하는 TLS/8443을 사용하지만 실제 VM 성공을 의미하지 않는다.

## 확정된 배포 기준

| 구분 | 값 |
| --- | --- |
| 공급자·리전 | Oracle Cloud Infrastructure, Tokyo (`ap-tokyo-1`) |
| 인스턴스 | 일반 컴퓨팅 AMD64(`x86_64`) VM 2대 |
| VM별 사양 | 각각 1 OCPU / 6 GB RAM |
| OS | Oracle Linux 9.8 |
| Python | OS 기본 3.9.25 유지, 배포 도구는 `/usr/bin/python3.11` 3.11.13 사용 |
| 논리 subnet | Public `10.0.0.0/24` 1개, Private `10.0.1.0/24` 1개 |
| Edge | Public VM: React 정적 artifact + Nginx, 공개 HTTPS/WSS |
| Application | Private VM: Spring Boot + MySQL + Redis + Docker 실행 엔진 |
| 자동 배포 | OCI DevOps Instance Group + Oracle Cloud Agent Run Command plugin |

실제 배포 artifact와 OCI 배포 job의 target은 `linux/amd64`다. ARM-01의 native ARM64 Release Contract는 대상 VM 아키텍처가 아니라 이식성 회귀 검사로 유지한다.

프로세스 배치는 VM 1에 frontend·Nginx Edge, VM 2에 Spring Boot·MySQL·Redis·Docker 실행 엔진을 둔다. 이 배치만으로 VM의 실제 서브넷·NSG 할당이나 접근 차단이 증명되지는 않는다. 실행 엔진과 데이터 계층이 같은 호스트에 있으므로 기존 UID·capability·읽기 전용 root·CPU·메모리·PID·timeout·`network=none` 경계를 유지한다.

## DNS와 TLS 기준

- 무료 DNS에 공개 호스트명의 `A` record를 만들고 Edge VM의 public IPv4를 가리킨다.
- GitHub `production-frontend` 환경의 `PUBLIC_ORIGIN`은 `https://<공개 호스트명>` 형식만 허용한다. IP, localhost, 사용자 정보, 명시적 port, path, query는 거부한다.
- 공개 CA 인증서의 SAN이 공개 호스트명과 일치해야 한다. Edge bootstrap 전에 `/etc/pki/code-clash-arena/public/fullchain.pem`, `/etc/pki/code-clash-arena/public/private.key`를 준비한다.
- TCP 80은 HTTPS redirect와 인증서 발급에만 사용하고 서비스 확인은 HTTPS 443/WSS로 수행한다.
- Edge에서 Application으로는 `app.cca.internal:8443`을 사용한다. Edge의 `/etc/pki/code-clash-arena/internal/ca.pem`으로 Application 인증서 SAN을 검증하며 평문 fallback은 두지 않는다.

## 적용 전에 확인할 값

- 공개 DNS 호스트명, DNS 전파, 공개 인증서 발급·갱신 책임과 파일 배치.
- 두 VM의 실제 VNIC·subnet·NSG·route, Public IGW와 Private NAT Gateway 대응.
- `app.cca.internal`의 Private VM IP 해석과 Application 내부 인증서.
- Oracle Cloud Agent Run Command plugin의 `Running` 상태와 OCI DevOps/IAM 정책.
- registry·배포 자격증명·비공개 backup bucket, boot/block volume·backup 할당과 서비스 quota.

실제 매핑은 `논리 영역 → 프로세스/리소스 → VNIC·서브넷 → NSG·route → 검증 결과`로 기록한다. 미적용·미검증 항목은 명시하고, 동일 호스트의 프로세스나 Docker network 분리를 클라우드 서브넷 구분으로 대신 표시하지 않는다.

VM별 사양을 임의 변경하거나 유료 상품으로 자동 전환하지 않는다. 리소스 적용과 배포는 대상·권한·비용을 확인한 후 별도로 진행한다.
