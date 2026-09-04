# Code Clash Arena 마일스톤 로드맵

- 기준일: 2026-09-04
- 기준 브랜치: `main`
- 현재 작업 브랜치: `codex/m4-subnet-plan`
- 관리 단위: 우선순위 목록이 아니라 완료 조건을 공유하는 마일스톤

현재 코드 설명은 [`../doc`](../doc/README.md), 완료 기록은 [`../docs`](../docs/README.md)에 둔다. 이 폴더에는 아직 끝나지 않은 목표와 진행 커밋만 둔다.

| 마일스톤 | 목표 | 상태 | 문서 |
| --- | --- | --- | --- |
| M1 | 실제 인프라에서 안전하고 일관된 매치 경계 | DONE | [M1-release-boundary.md](M1-release-boundary.md) |
| M2 | 관측 가능하고 자동 회귀에 강한 운영 | DONE | [M2-operability-quality.md](M2-operability-quality.md) |
| M3 | 기능 단위 유지보수 구조 | DONE | [M3-maintainability.md](M3-maintainability.md) |
| M4 | 네트워크 계층 분리와 ARM 배포 | IN_PROGRESS | [M4-modernization-scale.md](M4-modernization-scale.md) |

마일스톤은 순서대로 완료한다. 뒤 마일스톤의 작업을 앞당길 수는 있지만, 앞 마일스톤의 완료 조건을 대신할 수 없다.

M3까지 완료했다. M4는 실제 배포 환경과 독립적인 Public/Private Subnet·접근 정책, 루트 모노레포 관리와 서비스별 자동 CI/CD, native ARM 배포 게이트로 범위를 정리했다. Tokyo 리전의 OCI ARM VM 두 대(각 1 OCPU / 6 GB), Public/Private Subnet 각 하나와 배치 초안은 별도 [배포 프로필](M4-deployment-profile.md)에서 관리한다. DEV-01은 DONE을 유지한다. NET-01은 IaC·mock 검증을 마치고 실환경 검증 대기로 IN_PROGRESS다. 다음 구현은 DATA-03 → ARM-01 → OPS-02 → REL-02 순이다. push는 세부 작업마다 하지 않고 앞의 세 작업과 뒤의 두 작업으로 묶어 검토한다. 완료된 구현과 검증 근거는 각 마일스톤 문서와 [`../docs/improvement`](../docs/improvement/README.md)에 보존한다.

## 상태 규칙

- `READY`: 선행 마일스톤이 끝나면 시작할 수 있다.
- `IN_PROGRESS`: 구현 또는 검증이 진행 중이다.
- `DONE`: 모든 완료 조건, 자동 검증, 문서와 커밋 기록이 있다.
- `BLOCKED`: 외부 권한·비밀·환경 때문에 검증할 수 없으며 blocker와 해제 조건이 기록되어 있다.

구조 변경과 동작 변경은 별도 커밋으로 유지한다. 각 작업은 증상/근거, 구현 범위, 검증, 완료 커밋을 같은 마일스톤 문서에 남긴다.
