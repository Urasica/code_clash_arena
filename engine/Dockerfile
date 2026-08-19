# Dockerfile
FROM node:24.18.0-bookworm-slim AS node-runtime

FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

# 유틸 및 언어별 컴파일러/런타임 설치
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    openjdk-21-jdk-headless \
    build-essential \
    util-linux \
    && rm -rf /var/lib/apt/lists/*

COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node

RUN groupadd --gid 10001 player1 \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin player1 \
    && groupadd --gid 10002 player2 \
    && useradd --uid 10002 --gid 10002 --no-create-home --shell /usr/sbin/nologin player2

WORKDIR /app

COPY . .

# The referee and game implementation remain readable only by root. Player
# sources are copied into per-UID tmpfs directories at runtime.
RUN chmod -R go-rwx /app

# 기본 실행은 파이썬 심판
CMD ["python3", "referee.py"]
