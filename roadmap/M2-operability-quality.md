# M2 — 관측 가능하고 회귀에 강한 운영

- 상태: READY
- 선행: M1 DONE

| ID | 작업 | 완료 조건 |
| --- | --- | --- |
| OPS-01 | 구조화 로그, correlation ID, queue/engine/DB metric, readiness·alert | match ID로 전 구간 추적하고 적체·timeout·cleanup·저장 실패 경보 확인 |
| TEST-01 | Testcontainers, Playwright, failure injection, Windows/Linux CI | PR 빠른 gate와 release 실제 인프라·브라우저·5언어 gate |
| AUTH-01 | 실제 Google OAuth claim·충돌·취소·logout 정책 | 운영 credential smoke와 예측 가능한 오류/계정 연결 |
| DATA-02 | 제출 코드·replay 보존, 삭제, 암호화, 감사 | 자동 만료·삭제와 접근 감사, DB 성장 상한 |
| DEP-01 | MySQL·Flyway·JDK·Node 지원 버전 정렬과 의존성 갱신 정책 | 지원 경고 없이 호환 행렬·lockfile·정기 갱신 gate 통과 |

M2 완료 시 장애를 재현하지 않고도 상태와 원인을 metric·log·trace에서 찾을 수 있어야 한다.
