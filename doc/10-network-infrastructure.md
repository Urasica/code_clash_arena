# 네트워크 구성 코드와 검증 경계

- 기준일: 2026-09-04
- 구현: [`infra/oci/network`](../infra/oci/network/main.tf)의 네트워크 root 및 mock 계약 테스트.
- 적용 상태: **실제 OCI plan/apply, VNIC 연결, 네트워크 검증은 미수행**. 이 문서는 현재 구성 코드가 표현하는 정책과 아직 필요한 배포 검증을 구분한다.

## 배포와 독립적인 역할

Public/Private 구분은 외부 진입과 비공개 서비스 접근 정책이다. VM 대수·사양·프로세스 배치는 [배포 프로필](../roadmap/M4-deployment-profile.md)의 별도 관심사다. 구성 코드는 Compute 리소스를 만들거나 기존 VM의 VNIC를 변경하지 않는다.

| 역할 | subnet 정책 | 연결 시 사용할 경계 |
| --- | --- | --- |
| Edge | Public, default route → IGW | Edge NSG. 외부 HTTPS, Private API로 전달 |
| Application | Private, public IP 금지, default route → NAT | Application NSG. Edge의 내부 TLS API만 수신 |
| Data | Private 역할 내부 | 기본 `host_local` 또는 명시적으로 선택한 `private_vnic` 정책 |
| 관리 | Bastion private endpoint는 Private Subnet | 관리 클라이언트 `/32` allowlist, 목적 호스트 SSH만 단기 접근 |

VCN 기본 주소는 `10.42.0.0/16`, Public `10.42.0.0/24`, Private `10.42.1.0/24`다. 환경별 입력으로 변경하며 두 subnet은 같은 VCN에서 겹치지 않게 계산한다. IPv6는 생성하지 않는다. VCN 내부 통신은 내부 경로를 사용하고 private outbound만 NAT를 사용한다. [OCI NAT](https://docs.oracle.com/en-us/iaas/Content/Network/Tasks/NATgateway.htm)

## 접근 행렬

아래는 **구성한 허용 규칙**이다. 실제 패킷 검증 결과가 아니다. 모든 TCP 허용은 stateful이며 응답을 위해 역방향 전체 port를 열지 않는다.

| 출발지 | 목적지 | 규칙 |
| --- | --- | --- |
| 인터넷 | Edge | TCP 443. TCP 80은 `allow_http_redirect=true`일 때만 허용 |
| Edge NSG | Application NSG | TCP 8443만 양쪽 NSG에 허용 |
| 인터넷 | Application/Data | public IP 없음. 인터넷 직접 inbound 허용 없음 |
| Edge | DB/Redis·management·Docker | 3306/6379/8081/2375/2376/22 직접 허용 없음 |
| Application NSG | Data NSG | `private_vnic`에서만 3306/6379 양쪽 허용 |
| Edge/Application 및 별도 Data 호스트 | HTTPS 목적지 | TCP 443 egress. Private에서는 NAT 경유 |
| Bastion private endpoint | 역할별 관리 포트 | 정확한 endpoint `/32`에서 TCP 22만 허용 |
| IPv4 네트워크 | 활성 역할별 VNIC | ICMP type 3/code 4만 허용하여 경로 MTU 오류 처리 |

HTTPS egress `0.0.0.0/0:443`은 특정 도메인만 허용하는 정책이 아니다. registry·OS repository·OAuth 목적지를 운영 기록에 남기고 필요한 별도 host/proxy 제어를 적용한다. HTTP-only package repository는 이 정책에서 동작하지 않으므로 HTTPS mirror를 선택해야 한다.

### Security list와 NSG의 합산

Public security list에는 허용 규칙이 없다. Private security list에도 ingress가 없고, Bastion endpoint가 두 subnet의 SSH 대상에 도달하기 위한 TCP 22 egress만 있다. 이 egress는 Private Subnet의 모든 VNIC에 적용되는 공통 예외이므로 NSG 전용 규칙으로 설명하지 않는다. 수신 호스트의 SSH ingress가 Bastion `/32`만 허용해 다른 Private 호스트가 직접 SSH를 시작하는 것은 막는다.

OCI 기본 security list는 어느 subnet에도 연결하지 않는다. 실환경에서 다른 NSG나 security list를 추가하면 허용 규칙이 합쳐지므로 **연결된 전체 규칙**을 검사해야 한다. 빈 목록을 NSG 허용보다 우선하는 deny 규칙으로 해석하지 않는다. [OCI 보안 규칙 합산](https://docs.oracle.com/en-us/iaas/Content/Network/Concepts/securityrules.htm)

DHCP는 OCI VCN resolver를 사용한다. `169.254.0.0/16` link-local 서비스는 OCI 보안 규칙으로 제한되지 않으므로, NSG로 metadata까지 차단했다고 주장하지 않는다. 플레이어의 metadata·DB·외부 접근 차단은 기존 `network=none`과 호스트/엔진 보안 정책을 유지하고 별도로 실증한다. [OCI link-local 예외](https://docs.oracle.com/en-us/iaas/Content/Network/Concepts/securityrules.htm)

### 데이터 배치별 정책

- `host_local` 기본값: MySQL/Redis ingress·egress NSG 규칙을 만들지 않는다. Data NSG는 비어 있으며 VNIC에 연결하지 않는다. 앱과 같은 호스트의 데이터 접근은 private container network/loopback·미공개 port·계정 권한으로 제한해야 한다. 이 실제 설정은 DATA-03에서 구현·검증한다.
- `private_vnic`: 별도 데이터 VNIC를 가진 환경을 위한 정책이다. Data NSG는 Application NSG에서만 3306/6379를 수신한다. Compute 생성, VNIC 연결, DB 인증 설정은 포함하지 않는다.
- Edge와 Application/Data NSG를 같은 VNIC에 중복 연결하면 역할 경계가 사라질 수 있다. 같은 호스트의 container network를 클라우드 subnet으로 표현하지 않는다. 같은 커널에서 실행 엔진과 DB가 공유하는 영향 범위는 배포 프로필에 남긴다.

## TLS와 프록시 계약

네트워크 정책의 내부 API 포트는 TCP 8443으로 결정했다. 외부 TLS는 Edge에서 종료하고, Edge→Application ingress는 **인증서를 검증하는 TLS로 다시 연결**한다. Private IP만 사용한다는 이유로 암호화를 생략하지 않는다.

아직 proxy나 인증서를 배포한 것은 아니다. OPS-02에서 Application ingress의 private DNS와 일치하는 SAN, 신뢰 CA, 만료/교체 절차, upstream 인증서 검증을 구현한다. 검증 비활성화나 평문 fallback은 허용하지 않는다. Application ingress→Spring의 8080은 같은 호스트의 loopback 또는 미공개 전용 container network에 한정한다. 현재 개발용 Spring 포트를 8443으로 이미 변경한 것으로 해석하지 않는다.

Edge는 브라우저에 하나의 HTTPS origin을 제공하고 `/api`, `/ws-stomp`와 하위 SockJS 경로, `/oauth2/authorization/*`, `/login/oauth2/code/*`를 전달한다. WebSocket upgrade, 원래 Host/HTTPS scheme, 쿠키·Origin을 보존하며 외부에서 전달한 spoofed forwarded header를 신뢰하지 않는다. Management 8081은 public proxy 경로에서 제외한다. 경로·쿠키·WSS·잘못된 upstream 인증서 차단은 실제 배포 artifact로 다시 검증한다.

## 관리·배포 접근

OCI Bastion은 private subnet으로 들어가는 관리 경로이며 NAT가 inbound SSH를 전달하는 것은 아니다. IaC에는 Bastion과 대상별 `/32:22`만 정의하며 session 생성·IAM·배포 계정·SSH 키는 포함하지 않는다.

검증 기본 방식은 한 대상의 SSH 22에 대한 단기 port-forwarding session이다. 승인된 클라이언트 IP, 세션 TTL(최대 30분), 대상 private IP, 최소 권한 계정, 등록된 host key 확인과 세션 종료를 검증한다. managed SSH 방식을 선택할 경우에는 OCI agent/plugin 및 필요한 연결 조건도 별도 확인한다. host key 검사 생략, 상시 public SSH, 광범위 SOCKS proxy로 우회하지 않는다. [Bastion 연결 조건](https://docs.oracle.com/en-us/iaas/Content/Bastion/Tasks/connectingtosessions.htm)

공용 GitHub runner 발신 IP가 고정되어 있다고 가정하지 않는다. OPS-02는 제한된 runner 주소를 일시 allowlist에 반영하고 회수하는 방식과 해당 IAM을 실증하거나, 별도 승인된 접근 방식을 정해야 한다. 이 선택이 해결되기 전에는 자동 CD 준비 완료로 표시하지 않는다.

## 실제 검증 체크리스트

승인된 검증 환경에서만 수행하고, 외부 운영 서비스나 공유 자원을 검사 대상으로 임의 추가하지 않는다. 검증용 listener/인증서가 필요하면 별도 승인된 호스트를 사용한다.

| 검사 | 합격 증거 |
| --- | --- |
| 리소스 대응 | 논리 역할 → resource/VNIC → subnet → 전체 NSG/security list/route → host bind/firewall |
| 외부 진입 | 외부에서 Edge HTTPS 성공. HTTP 옵션과 redirect 정책 일치 |
| API 격리 | Edge→private 8443 TLS 성공, 비허용 대조군에서 같은 listener 연결 차단 |
| 데이터 격리 | 앱의 인증된 DB/Redis 접근 성공과 같은 시점의 Edge/외부 직접 접근 차단 |
| 관리 격리 | 승인 Bastion session에서 SSH 성공, Edge·인터넷 직접 SSH 실패, 종료 후 재접근 불가 |
| 내부 TLS | 정상 CA/hostname 성공, 잘못된 hostname·CA·만료 인증서 실패 |
| NAT | private 호스트 HTTPS outbound 성공, 승인된 NAT 차단 시 새 outbound 연결 실패, 내부 Edge/API는 유지, 복구 후 재성공 |
| 위험한 노출 | 실제 host port/listener, Docker publish, 8081·2375·2376·3306·6379 노출 검사 |
| 플레이어 격리 | 기존 `network=none`, metadata·VCN·DB/Redis 접근 불가, 보안 corpus 통과 |

접속 timeout·refused만으로 NSG 차단을 입증하지 않는다. 같은 서비스가 허용 출발지에서 정상 동작하는 대조군과 route/firewall/VCN flow 기록을 함께 확인한다. 인터넷에서 private 주소에 route가 없는 것은 비공개 진입 증거일 뿐 NSG 단독 검증이 아니다. NAT 실험도 이미 열린 연결과 새 연결을 구분한다.

원본 plan/state·식별자·계정 정보·쿠키·키·제출 코드는 공개 결과에 포함하지 않는다. 검증 환경과 실제 배포 환경이 다르면 각각 증거를 남기고 미적용 부분을 명시한다. 결과가 없는 항목은 미검증이며 NET-01 완료로 표시하지 않는다.
