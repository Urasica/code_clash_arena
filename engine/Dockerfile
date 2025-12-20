# Dockerfile
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# 필수 유틸 및 언어별 컴파일러/런타임 설치
# - build-essential: gcc, g++, make 등 포함
# - nodejs, npm: JavaScript 실행용
RUN apt-get update && apt-get install -y \
    python3 python3-pip \
    openjdk-17-jdk \
    build-essential \
    nodejs npm \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# (옵션) 파이썬 라이브러리가 필요하다면 requirements.txt 복사 후 설치
# COPY requirements.txt .
# RUN pip3 install -r requirements.txt

COPY . .

# 기본 실행은 파이썬 심판
CMD ["python3", "referee.py"]