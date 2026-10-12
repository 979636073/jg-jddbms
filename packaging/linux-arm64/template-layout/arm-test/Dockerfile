FROM eclipse-temurin:8-jre-jammy
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    nginx curl procps netcat-openbsd ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /home/sjbz/dbms
CMD ["bash"]
