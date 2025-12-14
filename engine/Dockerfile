# 베이스 이미지: 가볍고 만만한 Ubuntu 기반
FROM ubuntu:22.04

# 패키지 설치 시 상호작용 방지 (Timezone 설정 등 무시)
ENV DEBIAN_FRONTEND=noninteractive

# 1. 패키지 리스트 업데이트 및 필수 유틸 설치
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 소스 코드 복사 (현재 폴더의 모든 파일을 컨테이너의 /app으로)
COPY . /app

# 4. (옵션) 실행 시 기본 명령어
CMD ["python3", "referee.py"]