# Dockerfile
FROM node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d AS node-runtime

FROM ubuntu:24.04@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517

ENV DEBIAN_FRONTEND=noninteractive

# 유틸 및 언어별 컴파일러/런타임 설치. The minimal base has no CA
# bundle. APT still verifies the signed snapshot metadata during this one-time
# TLS bootstrap; all later fetches verify TLS.
RUN sed -ri 's#https?://(archive.ubuntu.com/ubuntu|ports.ubuntu.com/ubuntu-ports|security.ubuntu.com/ubuntu)#https://snapshot.ubuntu.com/ubuntu/20260810T000000Z#g' /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get -o Acquire::https::Verify-Peer=false update \
    && apt-get -o Acquire::https::Verify-Peer=false install -y --no-install-recommends ca-certificates \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
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
