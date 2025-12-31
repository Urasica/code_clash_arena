# Code Clash Arena (CCA)

**실시간 1:1 알고리즘 전략 배틀 플랫폼**

> **"당신의 코드로 전장을 점령하라!"**   
> 사용자가 작성한 알고리즘 코드로 알고리즘 대결을 펼치는 **코딩 배틀 서비스**입니다.   
> 단순한 코딩 테스트를 넘어, 전략적 사고와 최적화 능력을 겨뤄볼 수 있습니다.

## 01 프로젝트 설명

**Code Clash Arena**는 플레이어가 직접 봇(Bot)의 로직을 코딩하여 상대방과 실시간으로 대결하는 게임 플랫폼입니다.   
Spring Boot 기반의 백엔드와 Docker를 활용한 격리된 코드 실행 환경(Sandbox)을 통해 안전하고 공정한 대결 환경을 보장합니다.

### 핵심 기능

* **실시간 1:1 매칭 & 배틀**: WebSocket을 활용한 실시간 게임 플레이
* **다중 언어 지원**: Python, Java, C, C++, JavaScript 등 주요 언어 지원
* **전략적 땅따먹기 (Land Grab)**: 단순 문제 풀이가 아닌, 영토를 넓히고 상대를 제압하는 게임형 알고리즘 대결 (더 많은 게임 추가 가능)

## 02 핵심 기능 및 로직

### 1. 실시간 매칭 시스템 (Matchmaking)

사용자의 대기열 등록 요청을 Redis를 통해 관리하며, 스케줄러를 통해 적절한 상대를 찾아 게임 세션을 생성합니다.

![매칭 알고리즘](.\image\Matching.png)

### 2. 샌드박스 코드 실행 (Safe Execution)

사용자가 제출한 코드는 **Docker Container** 내부의 격리된 환경에서 실행됩니다. 이를 통해 무한 루프, 시스템 콜 등 악의적이거나 불안정한 코드로부터 서버를 보호합니다.


## 03 System Architecture
![Architecture](.\image\Architecture.png)

### ERD (Entity Relationship Diagram)
![ERD](.\image\ERD.png)

사용자, 사용자간 승패 기록, 매치 기록, 리플레이 데이터 관계도입니다.

### Tech Stack

| 분류 | 기술 스택 |
| --- | --- |
| **Frontend** | React, Stomp.js, Monaco Editor |
| **Backend** | Java 17, Spring Boot 3.x, Spring Security (OAuth2/JWT) |
| **Database** | MySQL, Redis (캐싱 및 대기열 관리) |
| **Communication** | WebSocket (STOMP), REST API |
| **DevOps / AI** | Docker (Code Engine), Python (Game Logic Ref) |

## 04 Directory Structure

```bash
code_clash_arena/
├── backend/                  # Spring Boot 서버
│   ├── src/main/java/.../controller  # REST & Socket Controllers
│   ├── src/main/java/.../service     # 비즈니스 로직 (Matching, Execution)
│   ├── src/main/java/.../scheduler   # 매칭 스케줄러
│   └── src/main/resources/templates  # Code Runners (C, Py, Java...)
│
├── frontend/                 # React 웹 클라이언트
│   ├── src/GameArena.js      # 메인 게임 UI
│   ├── src/Lobby.js          # 로비 화면 및 매칭
│   └── src/ReplayViewer.js   # 리플레이 컴포넌트
│
└── engine/                   
    ├── Dockerfile            # 샌드박스 환경 구성
    └── games/                # 게임 로직

```

## 05 실행 방법

### Prerequisites

* Docker & Docker Compose
* Java 17+
* Node.js 18+
* Redis & MySQL

### 1. 환경 설정 (코드에 미포함) - application.properties

```properties
## Database
DATABASE_URL=jdbc:mysql://localhost:3306/code_arena
DATABASE_USERNAME=root
DATABASE_PASSWORD=your_password

## Redis
REDIS_HOST=localhost
REDIS_PORT=6379

## OAuth2 (Google/Kakao/Naver)
OAUTH2_CLIENT_ID=...
OAUTH2_CLIENT_SECRET=...
```

### 2. 백엔드 실행

```bash
cd backend/code
./mvn clean package
./mvn spring-boot:run
```

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm start
```

### 4. 가상환경 구성 (Docker)

```bash
cd engine
docker build -t code-execution-engine .
```