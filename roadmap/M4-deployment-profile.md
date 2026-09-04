# M4 배포 프로필 — OCI ARM

- 기준일: 2026-09-04
- 상태: 요청한 리소스 기준과 배치 초안. 실제 리소스 생성·배포·연결 확인은 수행하지 않았다.
- 관련 마일스톤: [M4 — 네트워크 계층 분리와 ARM 배포](M4-modernization-scale.md)

이 문서는 실제 배포 환경에 종속되는 값을 관리한다. Public/Private Subnet, NSG, NAT Gateway, 내부 통신, 데이터 계층 접근 정책은 M4의 논리 네트워크 설계에서 별도로 정의한다.

NET-01의 [`infra/oci/network`](../infra/oci/network/main.tf)는 별도 검증 환경에도 적용할 수 있는 네트워크 root다. 현재 mock 구성 검증만 수행했으며 이 배포 프로필의 VM이나 VNIC를 생성·연결하지 않았다. 실제 인증서 검증을 전제로 하는 내부 API TLS/8443 계약도 아직 proxy 배포나 실환경 성공을 의미하지 않는다.

## 요청한 리소스 기준

| 구분 | 값 |
| --- | --- |
| 공급자 | Oracle Cloud Infrastructure |
| 리전 | Tokyo (`ap-tokyo-1`) |
| 아키텍처 | Ampere A1 / ARM64 |
| VM 수 | 2대 |
| VM별 사양 | 각각 1 OCPU / 6 GB RAM |
| 논리 subnet | Public 1개, Private 1개 |
| 프론트 배포 단위 | React 정적 artifact + Nginx/Caddy 설정 |
| 백엔드 배포 단위 | Spring Boot artifact + 호환 engine digest + 배포 설정 |

앞서 요청한 OCI VM 사양과 Tokyo 리전, Public/Private Subnet 각 하나를 유지한다. 프로세스 배치 초안은 VM 1에 프론트·프록시, VM 2에 Spring Boot·MySQL·Redis·Docker 실행 엔진을 두는 것이다. **이 배치 초안만으로 VM의 서브넷·NSG 할당 또는 실제 접근 차단이 확정되는 것은 아니다.**

실행 엔진은 공통 Python 심판과 다섯 언어 runner 계약을 유지한다. React 개발 서버와 언어별 Judge 서비스를 운영용 상시 프로세스로 추가하지 않는다. 실행 엔진과 DB·Redis가 같은 호스트에 배치될 경우 sandbox 탈출의 영향 범위가 공유되므로 기존 격리·권한 정책을 유지하고 배치 기록에 명시한다.

## 적용 전에 확정할 값

- 실제 계정·home region·OS, 두 VM 확보 여부와 지정 사양, boot/block volume·backup 할당.
- Nginx/Caddy 선택, 도메인·인증서, 서비스·관리 포트, TLS 종료 지점.
- 논리 Public/Private 영역과 실제 VNIC·서브넷·NSG·route의 매핑. 필요한 NAT·Bastion의 생성 가능 여부와 IAM 권한.
- registry·배포 자격증명·백업 저장소 접근, 비용과 서비스 quota.
- 네트워크 검증 환경과 서비스 배포 환경이 다를 경우 각각의 리소스 식별자와 검증 결과.

실제 매핑은 `논리 영역 → 프로세스/리소스 → VNIC·서브넷 → NSG·route → 검증 결과`로 기록한다. 미적용·미검증 항목은 명시하고, 동일 호스트의 프로세스 또는 Docker network 분리를 실제 클라우드 서브넷 구분으로 대신 표시하지 않는다.

VM별 사양을 임의 변경하거나 유료 계정·상품으로 자동 전환하지 않는다. 리소스 적용과 배포는 대상·권한·비용을 확인한 후 별도로 진행한다.
