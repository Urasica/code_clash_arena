# DATA-03 운영 데이터 경계

이 디렉터리는 기존 개발용 [`compose.yaml`](../../../compose.yaml)을 대체하지 않는다. 실제 배포용 MySQL·Redis 경계와 외부 백업·격리 복원 도구를 준비하며, 현재 OCI VM에는 적용하지 않았다.

## 결정한 복구 경계

- MySQL은 영속 원장이다. 최소 권한 `cca_backup` 계정으로 논리 snapshot을 만들고, 생성과 동시에 `age` recipient로 암호화한다. 평문 dump는 디스크에 만들지 않는다.
- 암호문은 Tokyo 리전의 OCI Object Storage에 instance principal로 업로드한다. overwrite는 허용하지 않고 SHA-256과 생성된 object name을 외부 복구 기록에 남긴다.
- Redis의 매치 대기열·reservation·room·socket·rate limit·AI workspace lease는 TTL을 가진 현재 실행 상태다. 장애 뒤 snapshot을 되살리면 이미 끝난 연결과 실행을 재개할 수 있으므로 volume/AOF/RDB 없이 빈 상태로 재시작한다.
- 복원은 새 `cca-restore-*` Compose project의 빈 schema에서만 허용한다. 기존 서비스 database에는 import하지 않는다. 검증을 마친 뒤 실제 승격은 REL-02의 별도 승인 절차다.

MySQL 논리 backup 옵션과 계정 권한은 [MySQL 8.4 mysqldump](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)를 따른다. `--single-transaction`, `--no-tablespaces`, `--set-gtid-purged=OFF`를 사용하고 dump account에는 schema 전체 SELECT/SHOW VIEW/TRIGGER만 준다.

## 비공개화와 계정

서비스 port는 `127.0.0.1:3306`, `127.0.0.1:6379`에만 publish한다. 두 컨테이너는 외부 egress가 없는 internal/non-attachable Docker network에만 속한다. OCI에서는 Private VM에 public IP를 할당하지 않고 Edge/인터넷에서 데이터 port를 허용하지 않는 NET-01 정책과 함께 적용한다.

| 계정 | 권한 |
| --- | --- |
| `cca_app` | MySQL SELECT/INSERT/UPDATE/DELETE만, Redis는 등록한 key prefix와 명시 명령만 |
| `cca_migrator` | MySQL schema migration에 필요한 DDL/DML, 앱 기동에서는 사용하지 않음 |
| `cca_backup` | MySQL 읽기·view·trigger dump만 |
| `cca_health` | local MySQL 연결과 `SELECT 1`만 |
| Redis `default` | OFF |

MySQL은 secure transport를 요구하고 local infile·X Protocol·general log를 끈다. Redis는 별도 volume 없이 read-only root와 `/tmp` tmpfs를 사용한다. `CONFIG`, `ACL`, 임의 key prefix는 앱 ACL에서 허용하지 않는다.

Linux의 Docker Engine 28 이전 버전은 loopback publish만으로 인접 L2 호스트 접근을 완전히 배제하지 못할 수 있다. OCI NSG/security list와 host firewall로도 3306/6379를 차단하고, VM 검증 때 다른 출발지 대조군으로 확인한다. [Docker port 보안 주의사항](https://docs.docker.com/engine/network/port-publishing/)

## Secret 생성과 서비스 준비

Python 3.10 이상과 Docker Compose가 필요하다. secret 디렉터리는 저장소 밖의 새 경로만 허용한다. 명령 출력은 값 대신 생성한 파일 수만 보여준다. 실제 key·password·identity는 저장소·로그·artifact에 넣지 않는다.

```text
python deploy/backend/data/datactl.py init-secrets --directory /etc/code-clash-arena/data-secrets
python deploy/backend/data/datactl.py validate-configuration --project cca-data-production --secrets /etc/code-clash-arena/data-secrets
python deploy/backend/data/datactl.py start --project cca-data-production --secrets /etc/code-clash-arena/data-secrets
```

Compose secrets는 host 파일을 `/run/secrets`에 read-only로 제공하며 환경 변수에 password 값을 넣지 않는다. 도구는 매 실행마다 15개 파일의 형식·상호 일치, 역할 credential 중복, Redis ACL 일치를 확인하고 Linux에서는 group/other 권한이 열려 있으면 중단한다. 앱용 네 파일은 생성 경로의 `backend/` 아래에 따로 두므로 배포 시 이 하위 경로만 앱의 `/run/secrets/backend/`에 mount하거나 `CCA_DATA_SECRETS_DIR`로 지정한다. DB root·migration·backup·health 파일이 있는 상위 경로를 앱 사용자에게 공개하지 않는다. 환경 변수를 사용하면 동일한 전용 app 계정을 주입한다. [Docker Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/)

`prod` 기동 검사는 app 계정, host-local URL, MySQL TLS/public-key retrieval 차단, Flyway 비활성화, Redis ACL 계정, management loopback을 강제한다. Migration은 app 프로세스에 DDL 자격증명을 주지 않는 별도 OPS-02 단계에서 구현한다.

## 암호화 백업과 외부 업로드

`age` identity는 VM에 두지 않고 복구 책임자의 외부 secret store에 보관한다. VM에는 암호화용 public recipient만 전달한다.

```text
python deploy/backend/data/datactl.py backup --project cca-data-production --secrets /etc/code-clash-arena/data-secrets --recipient AGE_PUBLIC_RECIPIENT --output /var/lib/code-clash-arena/outbox/snapshot.sql.age
python deploy/backend/data/datactl.py upload --file /var/lib/code-clash-arena/outbox/snapshot.sql.age --region ap-tokyo-1 --compartment-id APPROVED_COMPARTMENT_OCID --namespace OCI_NAMESPACE --bucket BACKUP_BUCKET --object-name GENERATED_OBJECT_NAME
```

backup JSON 출력의 SHA-256과 object name을 복구 기록에 보존한다. `upload`는 먼저 bucket이 승인된 compartment에 있고 `NoPublicAccess`인지 조회한 뒤 OCI CLI instance-principal 인증, `--no-overwrite`, `--verify-checksum`으로 전송한다. 신규 upload receipt의 `etag`가 없으면 성공으로 처리하지 않는다. bucket 생성·보존/삭제 정책·versioning·IAM은 자동화하지 않았으며 실제 적용 전에 승인한다. [OCI object upload](https://docs.oracle.com/en-us/iaas/tools/oci-cli/latest/oci_cli_docs/cmdref/os/object/put.html)

## 격리 복원

Object Storage에서 받은 암호문의 SHA-256을 외부 복구 기록과 먼저 비교한다. 복구 전용 secret과 project를 새로 만들고, age identity는 실행 중에만 제한된 local file로 제공한다.

```text
python deploy/backend/data/datactl.py download --output /secure/restore/downloaded.sql.age --sha256 RECORDED_SHA256 --region ap-tokyo-1 --compartment-id APPROVED_COMPARTMENT_OCID --namespace OCI_NAMESPACE --bucket BACKUP_BUCKET --object-name RECORDED_OBJECT_NAME
python deploy/backend/data/datactl.py restore --project cca-restore-DRILL_ID --secrets /etc/code-clash-arena/restore-secrets --file downloaded.sql.age --identity /secure/age-identity.txt --sha256 RECORDED_SHA256
```

`download`도 승인된 비공개 bucket을 먼저 확인하고 새 외부 경로에만 저장한 뒤 age header와 기록한 SHA-256을 검증한다. 도구는 restore MySQL을 먼저 시작하고 schema가 비어 있지 않으면 거부한다. 암호문의 인증을 끝낸 임시 평문만 import하고 종료 시 삭제를 시도한다. 일반 파일시스템 overwrite는 물리 매체에서 완전 삭제를 보장하지 않으므로 restore host의 암호화된 임시 디스크와 폐기 정책을 함께 사용한다.

복원 결과에서 table/row 계약, 민감 payload의 앱 수준 AES-GCM envelope, Flyway validate, backup app의 write 거부, Redis stale key 부재를 확인한 뒤 복구 환경을 폐기한다. 자동 failover·운영 DB 덮어쓰기 기능은 제공하지 않는다.

일반 점검 중지는 volume을 삭제하지 않는다.

```text
python deploy/backend/data/datactl.py stop --project cca-data-production --secrets /etc/code-clash-arena/data-secrets
```

운영 volume 제거·기존 DB 복원·object 삭제는 이 도구가 제공하지 않는다.

## ARM-01 이후 실환경 확인

실제 VM 정보가 준비되면 다음 증거를 같은 배포 시점에 수집한다. 자격증명·공인 IP 전체 값·제출 코드 원문은 기록에서 가린다.

1. Application VM의 실제 VNIC·Private Subnet·전체 NSG/security list·host firewall과 MySQL/Redis listener가 논리 역할에 어떻게 대응하는지 기록한다.
2. 전용 앱 계정으로 Private VM 내부의 DB·Redis 접근과 readiness가 성공하는 동안 Edge VM과 인터넷 대조군에서 3306/6379 직접 연결이 차단되는지 확인한다. listener 미기동이나 단일 timeout만 차단 증거로 사용하지 않는다.
3. VM에는 `age` public recipient만 둔 상태로 암호화 backup을 생성하고 Tokyo의 승인된 비공개 bucket으로 upload한다. 원본과 별개 경로에 `download`해 SHA-256을 비교한다.
4. 새 `cca-restore-*` 환경으로 복원해 Flyway schema/version, 익명화한 행 계약, 앱 수준 AES-GCM envelope와 backup 계정 write 거부를 확인한다. 운영 DB에는 import하지 않는다.
5. Redis 재시작 후 과거 queue/room/socket/workspace key가 돌아오지 않는지 확인하고 새 매치 흐름으로 서비스 복구를 검증한다.

이 증거가 확보되기 전에는 로컬 Compose와 mock OCI 검사만으로 DATA-03을 완료 처리하지 않는다.

## 현재 검증 상태

단위 검사는 secret 비노출·권한/ACL 구성·잘못된 업로드/복원 입력 거부를 확인한다. Docker 통합 검사는 고유한 임시 project/volume만 만들고 MySQL 역할 분리, Redis key/명령 ACL, age 암호화 backup, 새 MySQL 복원, 빈 Redis 재시작을 확인한 뒤 삭제한다.

실제 Tokyo OCI Object Storage 업로드·다운로드 복원과 VM의 외부 접근 차단은 수행하지 않았다. ARM-01 이후 동일 배포 VM에서 확인하며, 그 전까지 DATA-03은 `IN_PROGRESS — 구현 및 로컬 검증, VM 검증 대기`다.
