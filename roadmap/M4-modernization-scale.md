# M4 — 플랫폼 현대화와 용량 최적화

- 상태: READY
- 선행: M3 DONE

| ID | 작업 | 완료 조건 |
| --- | --- | --- |
| DEV-01 | CRA에서 유지보수되는 build/test 도구로 전환 | env·bundle·test 동등성과 브라우저 smoke |
| OPS-02 | lockfile build, SBOM/image scan/signature, migration dry-run, staged deploy/rollback | 동일 commit의 immutable artifact만 gate 통과 후 배포 |
| SCALE-01 | queue/container/DB/replay 부하와 p95/p99 용량 측정 | bounded queue와 capacity alert, 격리 유지 |

성능 최적화는 M1의 player/host 격리 수준을 낮춰서는 안 된다.
