# Dockerfile
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# 유틸 및 언어별 컴파일러/런타임 설치
RUN apt-get update && apt-get install -y \
    python3 python3-pip \
    openjdk-17-jdk \
    build-essential \
    nodejs npm \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . .

# 기본 실행은 파이썬 심판
CMD ["python3", "referee.py"]