# OCI 네트워크 구성 — NET-01

[`network/`](network/main.tf)는 **VM·애플리케이션 배포와 독립적인 Terraform root**다. OCI 계정에 적용하지 않은 구성 코드이며, mock 검증 성공이 실제 서브넷 접근 차단을 뜻하지 않는다.

## 포함 범위

- IPv4 VCN, regional Public/Private Subnet 각 하나, IGW/NAT, 역할별 route table, DHCP resolver.
- Edge/Application/Data NSG, 기본 security list를 사용하지 않는 명시적 subnet 보안 목록.
- 제한된 공인 IPv4 `/32` 클라이언트만 허용하는 OCI Bastion과 최대 30분 세션 정책.
- 네트워크 정책 회귀를 확인하는 Terraform mock 테스트와 고정 provider lockfile.

VM, VNIC 연결, 도메인, 인증서, DB, registry, IAM policy, Bastion session은 생성하지 않는다. 리소스 사양을 변수로 받지 않으며, 실제 호스트 정보는 [M4 배포 프로필](../../roadmap/M4-deployment-profile.md)에서 별도로 관리한다. 다른 배포 환경을 사용해도 논리 역할은 유지하고 환경별 매핑만 변경한다.

## 로컬 검증: OCI 자격증명 불필요

검증 기준은 Terraform `1.13.5`, OCI provider `8.25.0`이다. Terraform은 PATH에 준비하고 아래 명령을 저장소 루트에서 실행한다. PowerShell과 Bash에서 동일한 명령을 사용할 수 있다.

```text
terraform -chdir=infra/oci/network fmt -check -recursive
terraform -chdir=infra/oci/network init -backend=false -input=false -lockfile=readonly
terraform -chdir=infra/oci/network validate -no-color
terraform -chdir=infra/oci/network test -no-color
```

초기화는 registry에서 공개 provider를 다운로드한다. 테스트 파일의 모든 run은 `mock_provider "oci"`를 사용한다. `command = apply`도 메모리 안의 가짜 리소스에만 적용되며 실제 OCI API를 호출하지 않는다. mock 선언을 제거하고 실행하지 않는다. PR Gate의 기존 Ubuntu/Windows 필수 job에 같은 검사를 연결하며, 운영 자격증명이나 실제 apply는 추가하지 않는다.

11개 테스트는 기본 접근 정책, 별도 Data VNIC 정책, HTTP opt-in, NAT 차단 스위치, 다른 CIDR 환경, 광범위·비어 있는·사설 관리 IP, public VCN 주소, 너무 작은 subnet, 알 수 없는 데이터 정책을 검사한다. **패킷 전달·방화벽·TLS·Bastion 연결 검증은 포함하지 않는다.**

provider 변경 시 공식 checksum으로 Windows/Linux/ARM 잠금을 갱신하고 위 검사를 다시 수행한다. ARM provider 패키지 checksum은 native ARM 애플리케이션 실행 증거가 아니다.

```text
terraform -chdir=infra/oci/network providers lock -platform=windows_amd64 -platform=linux_amd64 -platform=linux_arm64
```

## 환경별 입력과 승인 경계

[`environment.tfvars.example`](network/environment.tfvars.example)은 실제 계정 값이 없는 예제다. 환경별 `.tfvars`, 인증 설정, state, plan은 커밋하지 않는다. OCI API private key·token은 외부 OCI profile/secret store에서 공급하고 이 root에 복사하지 않는다.

1. 적용할 계정·compartment·region, 전용 네트워크 검증 환경 여부를 확정한다. 예제 IP는 관리자의 실제 주소가 아니다.
2. 기존 VCN·VPN·호스트 주소와 충돌하지 않는 RFC1918 CIDR, 명시적인 관리 클라이언트 `/32`, 사용 가능한 VCN/Subnet/NAT/Bastion quota를 확인한다.
3. IAM 최소 권한, 비용·과금 상태, state의 암호화·접근 제한·잠금·백업 위치를 승인받는다. 이 root에는 backend를 고정하지 않았다. 로컬 state를 운영의 공유 state로 사용하지 않는다.
4. 승인된 환경 입력과 외부 인증으로 **읽기 전용 plan**을 만들고 변경 대상을 검토한다. 실제 apply 전에 state backend도 확정한다. plan 파일 자체도 민감 산출물로 취급한다.
5. 리소스 생성은 별도 승인을 받은 뒤 수행한다. 이 문서, 코드 push, mock 테스트는 apply 승인이나 유료 상품 전환을 의미하지 않는다. 자동 apply, `-auto-approve`, 일괄 destroy 명령은 제공하지 않는다.

```text
terraform -chdir=infra/oci/network plan -input=false -var-file=approved.tfvars -out=approved.tfplan
```

기존 리소스에 무작정 적용/import하지 않는다. 새 환경용 이름과 state인지, 기존 변경·삭제가 plan에 없는지 확인한다. 네트워크 삭제나 NAT 차단 실험도 별도 대상 확인과 복구 계획이 필요하다.

## 매핑과 실제 검증

접근 정책·TLS·관리 경계 및 검증 행렬은 [현재 네트워크 코드 설명](../../doc/10-network-infrastructure.md)을 따른다. output `network_mapping`은 네트워크 리소스 식별자만 제공하며 `vnic_mapping`은 의도적으로 비어 있다. VM이 생성되었다거나 접근 차단을 실증했다고 해석하지 않는다.

실제 환경에서는 각 역할의 리소스·VNIC·subnet·**모든** NSG/security list·OS firewall·프로세스 bind를 별도 매핑한다. NET-01의 실환경 완료에는 동일 시점의 허용/차단 대조군, Bastion 단기 세션, NAT outbound/내부 통신, 인증서 검증 증거가 필요하다. 단순 접속 timeout이나 서비스 미기동은 접근 차단 성공이 아니다.

현재 실제 환경 plan/apply와 네트워크 검증은 수행하지 않았다. 환경 정보와 적용 승인이 확보되기 전까지 [M4 NET-01](../../roadmap/M4-modernization-scale.md)은 `IN_PROGRESS`로 유지한다.
